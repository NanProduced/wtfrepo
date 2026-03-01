import NextAuth, { NextAuthConfig } from "next-auth";
import Credentials from "next-auth/providers/credentials";
import GitHub from "next-auth/providers/github";
import Google from "next-auth/providers/google";
import { createHmac, randomUUID } from "crypto";

// --- API Contract Types (from AUTH_CONTRACT_v0.1.md) ---
interface BackendUser {
  userId: string;
  username: string;
  usernameChanged: boolean;
  roles: string[];
  bugBalance?: number;
}

interface BackendAuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  expiresAt: string;
  isNewUser: boolean;
  user: BackendUser;
  initialBugGrant: number;
}

type BackendOAuthProvider = "GITHUB" | "GOOGLE";

const DEFAULT_IDENTITY_PROOF_ISSUER = "wtf-repo-bff";
const DEFAULT_IDENTITY_PROOF_AUDIENCE = "wtf-repo-backend-auth-exchange";
const DEFAULT_IDENTITY_PROOF_SECRET = "change-this-identity-proof-secret-change-this";
const DEFAULT_IDENTITY_PROOF_TTL_SECONDS = 120;

function resolveBackendProvider(provider: string): BackendOAuthProvider {
  if (provider === "github") {
    return "GITHUB";
  }
  if (provider === "google") {
    return "GOOGLE";
  }
  throw new Error(`Unsupported OAuth provider: ${provider}`);
}

function encodeBase64Url(value: string): string {
  return Buffer.from(value).toString("base64url");
}

function buildIdentityProof(provider: BackendOAuthProvider, providerSubject: string): string {
  const issuer = process.env.AUTH_IDENTITY_PROOF_ISSUER || DEFAULT_IDENTITY_PROOF_ISSUER;
  const audience = process.env.AUTH_IDENTITY_PROOF_AUDIENCE || DEFAULT_IDENTITY_PROOF_AUDIENCE;
  const secret = process.env.AUTH_IDENTITY_PROOF_SECRET || DEFAULT_IDENTITY_PROOF_SECRET;
  const ttlSeconds = Number(process.env.AUTH_IDENTITY_PROOF_TTL_SECONDS || DEFAULT_IDENTITY_PROOF_TTL_SECONDS);

  const now = Math.floor(Date.now() / 1000);
  const header = {
    alg: "HS256",
    typ: "JWT",
  };
  const payload = {
    iss: issuer,
    aud: audience,
    sub: providerSubject,
    provider,
    jti: randomUUID(),
    iat: now,
    exp: now + ttlSeconds,
  };

  const encodedHeader = encodeBase64Url(JSON.stringify(header));
  const encodedPayload = encodeBase64Url(JSON.stringify(payload));
  const unsignedToken = `${encodedHeader}.${encodedPayload}`;
  const signature = createHmac("sha256", secret).update(unsignedToken).digest("base64url");

  return `${unsignedToken}.${signature}`;
}

// --- BFF Logic: Token Exchange ---
// Implements: POST /api/v1/auth/exchange
async function exchangeTokenWithBackend(
  provider: string,
  providerSubject: string,
  profile?: { displayName?: string | null; avatarUrl?: string | null },
  oauthState?: string
): Promise<BackendAuthResponse> {
  const API_URL = process.env.API_URL || "http://localhost:8080/api/v1";
  const backendProvider = resolveBackendProvider(provider);
  const identityProof = buildIdentityProof(backendProvider, providerSubject);
  const requestId = `req-${randomUUID()}`;
  const idempotencyKey = `idem-${requestId}`;
  const resolvedOAuthState = oauthState || randomUUID();

  console.log(`[BFF] Exchanging ${provider} token with Backend: ${API_URL}`);

  try {
    const res = await fetch(`${API_URL}/auth/exchange`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Request-Id": requestId,
        "X-Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify({
        provider: backendProvider,
        identityProof,
        oauthState: resolvedOAuthState,
        profile: {
          displayName: profile?.displayName || null,
          avatarUrl: profile?.avatarUrl || null,
        }
      }),
    });

    if (!res.ok) {
      const errorText = await res.text();
      console.error(`[BFF] Backend Exchange Failed (${res.status}):`, errorText);
      throw new Error(`Backend Exchange Failed: ${res.status}`);
    }

    const data = await res.json();
    return data as BackendAuthResponse;
  } catch (error) {
    // Fallback for Mock/Dev if Backend is unreachable
    if (process.env.NODE_ENV === "development") {
      console.warn("[BFF] Backend unreachable, falling back to Mock Data");
      return mockExchangeToken(provider);
    }
    throw error;
  }
}

// Mock Fallback
async function mockExchangeToken(provider: string): Promise<BackendAuthResponse> {
  return {
    accessToken: "mock_backend_jwt_" + Date.now(),
    tokenType: "Bearer",
    expiresIn: 3600,
    expiresAt: new Date(Date.now() + 3600 * 1000).toISOString(),
    isNewUser: false,
    user: {
      userId: "u_mock_real_" + provider,
      username: `patient_${provider.toLowerCase()}`,
      usernameChanged: false,
      roles: ["USER"],
      bugBalance: 5,
    },
    initialBugGrant: 0,
  };
}

export const authConfig = {
  providers: [
    GitHub({
      clientId: process.env.AUTH_GITHUB_ID,
      clientSecret: process.env.AUTH_GITHUB_SECRET,
    }),
    Google({
      clientId: process.env.AUTH_GOOGLE_ID,
      clientSecret: process.env.AUTH_GOOGLE_SECRET,
    }),
    // Keep Credentials for Dev testing without keys
    Credentials({
      name: "Mock Login",
      credentials: { username: { label: "Username", type: "text" } },
      async authorize(credentials) {
        if (!credentials?.username) return null;
        return {
          id: "u_mock_cred",
          name: credentials.username as string,
          email: "patient@asylum.dev",
          image: `https://api.dicebear.com/7.x/pixel-art/svg?seed=${credentials.username}`,
        };
      },
    }),
  ],
  callbacks: {
    async jwt({ token, account, user }) {
      // 1. Initial Login (OAuth)
      if (account && user) {
        // Only run exchange for OAuth providers
        if (account.provider === "github" || account.provider === "google") {
          try {
            const providerSubject = account.providerAccountId || user.id || "";
            if (!providerSubject) {
              throw new Error("Missing provider subject for backend exchange");
            }
            const backendData = await exchangeTokenWithBackend(
              account.provider,
              providerSubject,
              {
                displayName: user.name,
                avatarUrl: user.image,
              }
            );

            // Persist Backend Token to JWT
            token.backendAccessToken = backendData.accessToken;
            token.user = backendData.user; // Backend user profile
          } catch (error) {
            console.error("Token Exchange Error", error);
            // Don't fail the whole signin, but maybe mark session as "partial"
            // or rely on client to handle missing backend token
          }
        } else {
          // Credentials Provider (Mock)
          token.user = {
            userId: user.id || "mock",
            username: user.name || "mock",
            roles: ["USER"],
            bugBalance: 999
          };
        }
      }
      return token;
    },
    async session({ session, token }) {
      // Pass backend data to client session
      if (token.user) {
        const backendUser = token.user as Partial<BackendUser>;
        session.user = {
          ...(session.user ?? {}),
          ...backendUser,
        };
      }

      // Pass the backend token so BFF can use it
      if (token.backendAccessToken) {
        session.backendAccessToken = token.backendAccessToken;
      }

      return session;
    },
  },
  pages: {
    signIn: "/auth/login", // We need to build this page
  },
  // Ensure we use a secret in prod
  secret: process.env.AUTH_SECRET,
} satisfies NextAuthConfig;

export const { handlers, auth, signIn, signOut } = NextAuth(authConfig);
