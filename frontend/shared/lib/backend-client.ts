import { auth } from "@/shared/config/auth";
import { randomUUID } from "crypto";

const BACKEND_URL = process.env.API_URL || "http://localhost:8080/api/v1";

interface SessionTokenCarrier {
  user?: {
    backendAccessToken?: string;
  };
  backendAccessToken?: string;
}

function isNonEmptyObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && Object.keys(value).length > 0;
}

export interface BackendErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  requestId: string;
  violations?: Array<{ field: string; message: string }>;
}

export class BackendError extends Error {
  constructor(public response: BackendErrorResponse) {
    super(response.message || response.error);
    this.name = "BackendError";
  }
}

/**
 * Backend Client (Server-only)
 * Used by BFF Route Handlers to communicate with the Spring Boot backend.
 */
export async function backendFetch<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const session = await auth();
  const sessionTokenCarrier = session as SessionTokenCarrier | null;
  const token =
    sessionTokenCarrier?.user?.backendAccessToken ||
    sessionTokenCarrier?.backendAccessToken;

  const requestId = `req-${randomUUID()}`;

  const headers = new Headers(options.headers);
  headers.set("Content-Type", "application/json");
  headers.set("X-Request-Id", requestId);

  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const url = `${BACKEND_URL}${path.startsWith("/") ? path : `/${path}`}`;

  try {
    const res = await fetch(url, {
      ...options,
      headers,
    });

    if (!res.ok) {
      let errorData: BackendErrorResponse;
      try {
        errorData = await res.json();
      } catch {
        errorData = {
          timestamp: new Date().toISOString(),
          status: res.status,
          error: res.statusText,
          code: "UNKNOWN_ERROR",
          message: "An unexpected error occurred",
          path,
          requestId,
        };
      }
      throw new BackendError(errorData);
    }

    if (res.status === 204) {
      return {} as T;
    }

    return res.json();
  } catch (error) {
    const isDev = process.env.NODE_ENV === "development" || process.env.NODE_ENV === "test";

    if (isDev) {
      console.warn(`[BFF] Backend request failed for ${path}. Falling back to mock data.`);
      const mockData = getMockDataForPath(path, options.method);
      if (isNonEmptyObject(mockData)) {
        return mockData as T;
      }

      if (path.includes("/specimens")) {
        return { items: [], hasMore: false } as T;
      }
    }

    if (error instanceof BackendError) throw error;
    throw error;
  }
}

/**
 * Helper to generate mock data for specimen routes during development.
 */
function getMockDataForPath(path: string, method?: string): unknown {
  const normalizedPath = path.split("?")[0];
  const requestMethod = (method || "GET").toUpperCase();

  if (normalizedPath.includes("/arena/duel")) {
    const now = new Date().toISOString();

    return {
      battleId: `battle_${Date.now()}`,
      userBugBalance: 42,
      participantCount: 128,
      round: 42,
      left: {
        specimenId: "arena_sp_left",
        repoFullName: "is-thirteen/is-thirteen",
        oneLiner: "Check if a number is equal to 13. A masterclass in over-engineering.",
        readmePreview:
          "# is-thirteen\n\nCheck if a number is equal to 13.\n\n## Usage\n\n```js\nvar is = require('is-thirteen');\n\nif (is(13).thirteen()) {\n  // ...\n}\n```",
        githubMeta: {
          repoHtmlUrl: "https://github.com/is-thirteen/is-thirteen",
          ownerLogin: "is-thirteen",
          ownerAvatarUrl: "https://api.dicebear.com/7.x/identicon/svg?seed=left",
          languages: [{ name: "JavaScript", percentage: 100 }],
          stargazersCount: 4200,
          pushedAt: now,
        },
        metrics: {
          elo: 1320,
          hype: 71.2,
          votes: 1402,
        },
        tags: [
          { dimensionKey: "species", tagKey: "species_tooling", name: "Tooling" },
          { dimensionKey: "diagnosis", tagKey: "diag_funny", name: "Funny" },
        ],
      },
      right: {
        specimenId: "arena_sp_right",
        repoFullName: "kelseyhightower/nocode",
        oneLiner: "The best way to write secure and reliable applications. Write nothing; deploy nowhere.",
        readmePreview:
          "# No Code\n\nNo code is the best way to write secure and reliable applications.\n\n## Getting Started\n\nStart by not writing any code.\n\n## Deployment\n\nDeploying nothing to nowhere.",
        githubMeta: {
          repoHtmlUrl: "https://github.com/kelseyhightower/nocode",
          ownerLogin: "kelseyhightower",
          ownerAvatarUrl: "https://api.dicebear.com/7.x/identicon/svg?seed=right",
          languages: [{ name: "Markdown", percentage: 100 }],
          stargazersCount: 68000,
          pushedAt: now,
        },
        metrics: {
          elo: 1368,
          hype: 86.5,
          votes: 26711,
        },
        tags: [
          { dimensionKey: "species", tagKey: "species_art", name: "PerformanceArt" },
          { dimensionKey: "diagnosis", tagKey: "diag_insane", name: "Insane" },
        ],
      },
    };
  }

  if (normalizedPath.includes("/arena/vote") && requestMethod === "POST") {
    return {
      battleId: "battle_mock",
      choice: "LEFT",
      leftDelta: 12,
      rightDelta: -12,
      newBugBalance: 41,
    };
  }

  if (normalizedPath.includes("/archive/specimens")) {
    return {
      items: Array.from({ length: 12 }).map((_, i) => ({
        specimenId: `mock_sp_${i}`,
        repoFullName: `asylum-labs/sample-repo-${i}`,
        oneLiner: "Official diagnosis: this thing running is already a miracle.",
        githubMeta: {
          repoHtmlUrl: `https://github.com/asylum-labs/sample-repo-${i}`,
          ownerLogin: "asylum-labs",
          ownerAvatarUrl: `https://api.dicebear.com/7.x/identicon/svg?seed=mock${i}`,
          languages: [
            { name: "TypeScript", percentage: 80 },
            { name: "Rust", percentage: 20 },
          ],
          stargazersCount: 1000 + i * 100,
          pushedAt: new Date().toISOString(),
          topics: ["bug", "insane", "mock"],
        },
        metrics: {
          elo: 1200 + i * 50,
          hype: 42.5 + i,
          votes: 100 + i * 10,
          delta24h: i % 2 === 0 ? 12 : -5,
        },
        tags: [
          { dimensionKey: "species", tagKey: "species_tooling", name: "Tooling" },
          { dimensionKey: "diagnosis", tagKey: "diag_funny", name: "Funny" },
        ],
      })),
      nextCursor: "mock_cursor_1",
      hasMore: true,
    };
  }

  if (normalizedPath.includes("/tags")) {
    return {
      configVersion: "mock_v1",
      dimensions: [
        {
          dimensionKey: "species",
          nameZh: "Species",
          nameEn: "Species",
          selectMode: "single",
          required: true,
          sortOrder: 1,
          uiMeta: { icon: "species" },
          tags: [
            {
              tagKey: "species_tooling",
              nameZh: "Tooling",
              nameEn: "Tooling",
              uiMeta: { color: "#22d3ee" },
            },
            {
              tagKey: "species_lib",
              nameZh: "Library",
              nameEn: "Library",
              uiMeta: { color: "#818cf8" },
            },
          ],
        },
      ],
    };
  }

  if (normalizedPath.match(/\/specimens\/[^/]+\/drawer$/)) {
    const specimenId = normalizedPath.split("/")[2];
    return {
      specimen: {
        specimenId,
        repoFullName: "asylum-labs/sample-repo",
        githubUrl: "https://github.com/asylum-labs/sample-repo",
      },
      githubMeta: {
        repoHtmlUrl: "https://github.com/asylum-labs/sample-repo",
        ownerLogin: "asylum-labs",
        ownerAvatarUrl: "https://api.dicebear.com/7.x/identicon/svg?seed=asylum",
        description: "A weird but useful tool",
        languages: [
          { name: "TypeScript", percentage: 72.5 },
          { name: "Python", percentage: 19.4 },
        ],
        stargazersCount: 2450,
        pushedAt: new Date().toISOString(),
        topics: ["cli", "funny", "automation"],
        metadataSyncedAt: new Date().toISOString(),
      },
      metrics: {
        elo: 1320,
        hype: 89.2,
        votes: 1402,
      },
      readmeExcerpt: {
        excerptType: "FUNNY",
        text: "This README reads like accidental performance art.",
      },
      tags: [{ dimensionKey: "species", tagKey: "species_tooling", name: "Tooling" }],
      topRoast: null,
      canOpenInternalDetail: true,
      githubJumpWarning: {
        title: "WARNING: HIGH RADIATION ZONE",
        body: "You are about to enter GitHub. Protective gear is recommended.",
      },
    };
  }

  if (normalizedPath.match(/\/specimens\/[^/]+\/repo-identities$/)) {
    const specimenId = normalizedPath.split("/")[2];
    return {
      specimenId,
      owner: {
        githubLogin: "asylum-labs",
        githubUserId: "12345",
      },
      contributors: [
        {
          githubLogin: "doctor-weird",
          githubUserId: "23456",
        },
      ],
      syncedAt: new Date().toISOString(),
    };
  }

  if (normalizedPath.match(/\/specimens\/[^/]+\/hype$/)) {
    return {
      accepted: true,
      hypeScore: 89.3,
    };
  }

  if (normalizedPath.match(/\/watchlist\/items\/[^/]+$/) && requestMethod === "DELETE") {
    return { removed: true };
  }

  if (normalizedPath.includes("/watchlist/items") && requestMethod === "POST") {
    return {
      added: true,
      itemId: "wl_mock_1",
    };
  }

  if (normalizedPath.includes("/watchlist/items") && requestMethod === "GET") {
    return {
      items: [
        {
          specimenId: "mock_sp_0",
          repoFullName: "asylum-labs/sample-repo-0",
          metrics: { elo: 1320, hype: 89.2 },
          addedAt: new Date().toISOString(),
        },
      ],
      nextCursor: null,
      hasMore: false,
    };
  }

  if (normalizedPath.match(/\/specimens\/[^/]+$/)) {
    const id = normalizedPath.split("/").pop();
    return {
      specimen: {
        specimenId: id,
        repoFullName: "asylum-labs/sample-repo",
        publicStatus: "ACTIVE",
      },
      githubMeta: {
        repoId: "123456789",
        repoHtmlUrl: "https://github.com/asylum-labs/sample-repo",
        owner: {
          login: "asylum-labs",
          id: "12345",
          avatarUrl: "https://api.dicebear.com/7.x/identicon/svg?seed=asylum",
          htmlUrl: "https://github.com/asylum-labs",
        },
        description: "A research project focused on non-deterministic behavior in production environments.",
        homepage: "https://asylum.dev",
        defaultBranch: "main",
        languages: [
          { name: "TypeScript", bytes: 45000, percentage: 72.5 },
          { name: "Python", bytes: 12000, percentage: 19.4 },
        ],
        topics: ["chaos", "asylum", "research"],
        license: { spdxId: "MIT", name: "MIT License" },
        visibility: "public",
        archived: false,
        fork: false,
        createdAt: "2021-02-01T00:00:00Z",
        updatedAt: new Date().toISOString(),
        pushedAt: new Date().toISOString(),
        stargazersCount: 2450,
        forksCount: 120,
        openIssuesCount: 38,
        metadataSyncedAt: new Date().toISOString(),
      },
      metrics: {
        elo: 1320,
        hype: 89.2,
        votes: 1402,
        comments: 34,
      },
      readme: {
        snapshotId: "snap_mock",
        excerpts: [
          { excerptType: "FUNNY", text: "This project was built during a 48-hour insomnia marathon." },
          { excerptType: "SUMMARY", text: "A tiny idea escalated into a chaotic utility ecosystem." },
        ],
      },
      officialCommentary: {
        oneLiner: {
          zh: "Official diagnosis: code-induced delirium.",
          en: "Official diagnosis: code-induced delirium.",
        },
        arenaReason: {
          zh: "High volatility makes it a wildcard in any battle.",
          en: "High volatility makes it a wildcard in any battle.",
        },
      },
      codeHighlights: [
        {
          title: "The Insomnia Loop",
          codeLanguage: "typescript",
          snippet: "while(true) { try { sleep(Math.random() * 1000); } catch(e) { /* ignore reality */ } }",
          explainText: "A non-blocking sleep that blocks the mind.",
        },
      ],
      repoIdentity: {
        owner: { githubLogin: "asylum-labs", githubUserId: "12345" },
        contributors: [{ githubLogin: "doctor-weird", githubUserId: "23456" }],
      },
      tags: [{ dimensionKey: "species", tagKey: "species_tooling", name: "Tooling" }],
      seoMeta: {
        title: "asylum-labs/sample-repo - Patient Report",
        description: "Deep dive into the chaos.",
      },
    };
  }

  return {};
}
