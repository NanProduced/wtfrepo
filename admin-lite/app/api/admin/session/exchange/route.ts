import { randomUUID } from "node:crypto";
import { NextRequest, NextResponse } from "next/server";
import { getBackendBaseUrl } from "@/lib/admin-env";
import { ADMIN_TOKEN_COOKIE } from "@/lib/admin-session";

type ExchangeMode = "auto" | "login" | "bootstrap" | "oauth";

interface ExchangePayload {
  mode: ExchangeMode;
  userToken?: string;
  email?: string;
  code?: string;
  state?: string;
  redirectUri?: string;
}

interface UpstreamResult {
  ok: boolean;
  status: number;
  body: Record<string, unknown>;
}

function toJsonResponse(status: number, body: unknown) {
  return NextResponse.json(body, { status });
}

async function callUpstream(params: {
  mode: "login" | "bootstrap";
  userToken: string;
  email?: string;
}): Promise<UpstreamResult> {
  const endpoint =
    params.mode === "bootstrap"
      ? `${getBackendBaseUrl()}/api/v1/admin/platform/bootstrap`
      : `${getBackendBaseUrl()}/api/v1/admin/platform/login`;

  const idempotencyKey = randomUUID();
  const upstream = await fetch(endpoint, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${params.userToken}`,
      "X-Request-Id": randomUUID(),
      "X-Idempotency-Key": idempotencyKey,
      "Content-Type": "application/json",
    },
    body: params.mode === "bootstrap" ? JSON.stringify({ email: params.email }) : undefined,
    cache: "no-store",
  });

  const contentType = upstream.headers.get("content-type") ?? "";
  const body = contentType.includes("application/json")
    ? ((await upstream.json()) as Record<string, unknown>)
    : ({ message: await upstream.text() } as Record<string, unknown>);

  return {
    ok: upstream.ok,
    status: upstream.status,
    body,
  };
}

function errorMessage(body: Record<string, unknown>): string {
  const message = body.message;
  return typeof message === "string" ? message : "";
}

function writeSessionCookie(response: NextResponse, accessToken: string) {
  response.cookies.set({
    name: ADMIN_TOKEN_COOKIE,
    value: accessToken,
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    maxAge: 60 * 60 * 8,
    path: "/",
  });
}

function buildSuccessResponse(body: Record<string, unknown>, source: "login" | "bootstrap") {
  const accessToken = body.accessToken;
  if (typeof accessToken !== "string" || accessToken.length === 0) {
    return toJsonResponse(502, { message: "Missing admin access token in response." });
  }
  const response = toJsonResponse(200, {
    ...body,
    source,
  });
  writeSessionCookie(response, accessToken);
  return response;
}

export async function POST(request: NextRequest) {
  const payload = (await request.json()) as ExchangePayload;
  const mode = payload.mode;
  const userToken = payload.userToken?.trim();
  const email = payload.email?.trim();
  const code = payload.code?.trim();
  const state = payload.state?.trim();
  const redirectUri = payload.redirectUri?.trim();

  // OAuth 授权码模式
  if (mode === "oauth") {
    if (!code || !state || !redirectUri) {
      return toJsonResponse(400, { message: "Missing authorization code, state or redirectUri." });
    }

    try {
      // 调用后端 OAuth 授权码交换端点
      const endpoint = `${getBackendBaseUrl()}/api/v1/admin/platform/oauth/token`;
      const response = await fetch(endpoint, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Request-Id": randomUUID(),
          "X-Idempotency-Key": randomUUID(),
        },
        body: JSON.stringify({ code, state, redirectUri }),
        cache: "no-store",
      });

      const contentType = response.headers.get("content-type") ?? "";
      const body = contentType.includes("application/json")
        ? ((await response.json()) as Record<string, unknown>)
        : ({ message: await response.text() } as Record<string, unknown>);

      if (!response.ok) {
        return toJsonResponse(response.status, body);
      }

      return buildSuccessResponse(body, "login");
    } catch (error) {
      return toJsonResponse(500, {
        message: error instanceof Error ? error.message : "OAuth token exchange failed.",
      });
    }
  }

  // 原有的 token 模式
  if (!userToken) {
    return toJsonResponse(400, { message: "Missing user token." });
  }

  if (mode !== "auto" && mode !== "login" && mode !== "bootstrap") {
    return toJsonResponse(400, { message: "Invalid mode." });
  }

  if (mode === "bootstrap" && !email) {
    return toJsonResponse(400, { message: "Email is required for bootstrap." });
  }

  if (mode === "login") {
    const loginResult = await callUpstream({ mode: "login", userToken });
    if (!loginResult.ok) {
      return toJsonResponse(loginResult.status, loginResult.body);
    }
    return buildSuccessResponse(loginResult.body, "login");
  }

  if (mode === "bootstrap") {
    const bootstrapResult = await callUpstream({ mode: "bootstrap", userToken, email });
    if (!bootstrapResult.ok) {
      return toJsonResponse(bootstrapResult.status, bootstrapResult.body);
    }
    return buildSuccessResponse(bootstrapResult.body, "bootstrap");
  }

  const loginResult = await callUpstream({ mode: "login", userToken });
  if (loginResult.ok) {
    return buildSuccessResponse(loginResult.body, "login");
  }

  const loginMessage = errorMessage(loginResult.body);
  const canTryBootstrap = loginMessage.includes("admin_role_required") && Boolean(email);
  if (!canTryBootstrap) {
    return toJsonResponse(loginResult.status, loginResult.body);
  }

  const bootstrapResult = await callUpstream({ mode: "bootstrap", userToken, email });
  if (!bootstrapResult.ok) {
    return toJsonResponse(bootstrapResult.status, {
      ...bootstrapResult.body,
      autoFlow: {
        firstStep: "login",
        secondStep: "bootstrap",
        loginMessage,
      },
    });
  }
  return buildSuccessResponse(bootstrapResult.body, "bootstrap");
}
