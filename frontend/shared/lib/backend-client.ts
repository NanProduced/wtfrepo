import { auth } from "@/shared/config/auth";
import { randomUUID } from "crypto";

const BACKEND_URL = process.env.API_URL || "http://localhost:8080/api/v1";
const ENABLE_MOCK_FALLBACK = resolveBooleanEnv("BFF_ENABLE_MOCK_FALLBACK", false);
const LOG_MOCK_FALLBACK = resolveBooleanEnv("BFF_LOG_MOCK_FALLBACK", true);

interface SessionTokenCarrier {
  user?: {
    backendAccessToken?: string;
  };
  backendAccessToken?: string;
}

function isNonEmptyObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && Object.keys(value).length > 0;
}

function resolveBooleanEnv(name: string, defaultValue: boolean): boolean {
  const value = process.env[name];
  if (!value) {
    return defaultValue;
  }
  return value.toLowerCase() === "true" || value === "1";
}

function shouldUseMockFallback(error: unknown): boolean {
  if (process.env.NODE_ENV === "production") {
    return false;
  }
  if (!ENABLE_MOCK_FALLBACK) {
    return false;
  }
  return !(error instanceof BackendError);
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
    if (shouldUseMockFallback(error)) {
      if (LOG_MOCK_FALLBACK) {
        console.warn(`[BFF] Backend request failed for ${path}. Falling back to mock data.`);
      }
      const mockData = getMockDataForPath(path, options.method, options.body);
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
function getMockDataForPath(path: string, method?: string, body?: BodyInit | null): unknown {
  const normalizedPath = path.split("?")[0];
  const requestMethod = (method || "GET").toUpperCase();

  if (normalizedPath.includes("/arena/duel")) {
    return {
      battleId: `battle_${Date.now()}`,
      matchMeta: {
        matchType: "ADJACENT",
        matchProfileVersion: "v1.0.0",
        isIpoMatch: false,
      },
      shouldResetExcludeSet: false,
      wallet: {
        balance: 42,
        voteCost: 1,
      },
      left: {
        specimenId: "arena_sp_left",
        title: "is-thirteen/is-thirteen",
        tagline: "Check if a number is 13. A masterclass in over-engineering.",
        species: "species_tooling",
        diagnosisTags: ["diag_funny", "diag_genius"],
        elo: 1320,
        matchesPlayed: 1402,
        ipoStatus: "POST_IPO",
        thumbnailUrl: "https://api.dicebear.com/7.x/identicon/svg?seed=left",
      },
      right: {
        specimenId: "arena_sp_right",
        title: "kelseyhightower/nocode",
        tagline: "Write nothing, deploy nowhere, sleep better.",
        species: "species_art",
        diagnosisTags: ["diag_insane", "diag_funny"],
        elo: 1368,
        matchesPlayed: 26711,
        ipoStatus: "POST_IPO",
        thumbnailUrl: "https://api.dicebear.com/7.x/identicon/svg?seed=right",
      },
    };
  }

  if (normalizedPath.includes("/arena/vote") && requestMethod === "POST") {
    const payload = (() => {
      if (typeof body !== "string") {
        return {};
      }
      try {
        return JSON.parse(body) as {
          winner?: string;
          choice?: string;
        };
      } catch {
        return {};
      }
    })();

    const winner =
      payload.winner === "LEFT" || payload.winner === "RIGHT" || payload.winner === "BOTH_BAD"
        ? payload.winner
        : payload.choice === "LEFT" || payload.choice === "RIGHT" || payload.choice === "BOTH_BAD"
          ? payload.choice
          : "LEFT";

    return {
      battleId: "battle_mock",
      winner,
      leftDelta: 12,
      rightDelta: -12,
      leftEloAfter: 1332,
      rightEloAfter: 1356,
      leftPhase: "GROWING",
      rightPhase: "STABLE",
      bugCost: 1,
      walletBalanceAfter: 41,
    };
  }

  if (normalizedPath.includes("/archive/insights")) {
    return {
      summary: {
        totalSpecimens: 128,
        totalVotes: 18642,
        todayArenaBattles: 932,
        averageElo: 1496.4,
        averageHype: 47.8,
        tradingDay: new Date().toISOString().slice(0, 10),
      },
      momentum: {
        rising: 49,
        unchanged: 21,
        falling: 43,
      },
      topRising: [
        {
          specimenId: "mock_sp_2",
          repoFullName: "asylum-labs/sample-repo-2",
          rank: 6,
          previousRank: 14,
          rankDelta: 8,
          metrics: {
            elo: 1612,
            hype: 58.4,
            votes: 442,
            delta24h: 36,
          },
        },
        {
          specimenId: "mock_sp_8",
          repoFullName: "asylum-labs/sample-repo-8",
          rank: 12,
          previousRank: 18,
          rankDelta: 6,
          metrics: {
            elo: 1540,
            hype: 54.1,
            votes: 331,
            delta24h: 24,
          },
        },
      ],
      topFalling: [
        {
          specimenId: "mock_sp_3",
          repoFullName: "asylum-labs/sample-repo-3",
          rank: 15,
          previousRank: 9,
          rankDelta: -6,
          metrics: {
            elo: 1490,
            hype: 44.8,
            votes: 298,
            delta24h: -29,
          },
        },
        {
          specimenId: "mock_sp_11",
          repoFullName: "asylum-labs/sample-repo-11",
          rank: 22,
          previousRank: 17,
          rankDelta: -5,
          metrics: {
            elo: 1458,
            hype: 39.7,
            votes: 212,
            delta24h: -17,
          },
        },
      ],
    };
  }

  if (normalizedPath.includes("/archive/leaderboard")) {
    const params = new URLSearchParams(path.includes("?") ? path.split("?")[1] : "");
    const metric = params.get("metric") === "HYPE" ? "HYPE" : "ELO";
    const cursor = Number.parseInt(params.get("cursor") || "0", 10);
    const pageIndex = Number.isNaN(cursor) || cursor < 0 ? 0 : cursor;
    const limitRaw = Number.parseInt(params.get("limit") || "10", 10);
    const limit = Number.isNaN(limitRaw) || limitRaw <= 0 ? 10 : Math.min(limitRaw, 100);

    const baseItems = Array.from({ length: 30 }).map((_, i) => ({
      specimenId: `mock_sp_${i}`,
      repoFullName: `asylum-labs/sample-repo-${i}`,
      rank: i + 1,
      rankDelta: metric === "ELO" ? (i % 3 === 0 ? 3 : i % 3 === 1 ? -2 : 0) : null,
      score: metric === "ELO" ? 1680 - i * 9 : 92 - i * 1.4,
      metrics: {
        elo: 1680 - i * 9,
        hype: 92 - i * 1.4,
        votes: 980 - i * 13,
        delta24h: i % 2 === 0 ? 12 : -7,
      },
    }));

    const start = pageIndex * limit;
    const end = Math.min(baseItems.length, start + limit);
    const items = start >= baseItems.length ? [] : baseItems.slice(start, end);
    const hasMore = end < baseItems.length;
    const nextCursor = hasMore ? String(pageIndex + 1) : null;

    return {
      metric,
      items,
      nextCursor,
      hasMore,
      total: baseItems.length,
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
      canOpenInternalDetail: false,
      githubJumpWarning: {
        title: "Warning: high radiation zone",
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

  if (normalizedPath.includes("/notifications/unread-count") && requestMethod === "GET") {
    return {
      unreadCount: 3,
    };
  }

  if (normalizedPath === "/notifications" && requestMethod === "GET") {
    return {
      items: [
        {
          notificationUid: "ntf_mock_1",
          type: "MENTIONED_IN_COMMENT",
          title: "You were mentioned in a comment",
          body: "@doctor-cortex referenced your diagnosis note.",
          actorNickname: "doctor-cortex",
          actorAvatarUrl: null,
          aggregateCount: 1,
          targetUrl: "/specimen/mock_sp_0#comment-1",
          fallbackUrl: "/specimen/mock_sp_0",
          status: "UNREAD",
          createdAt: new Date().toISOString(),
        },
        {
          notificationUid: "ntf_mock_2",
          type: "ACHIEVEMENT_UNLOCKED",
          title: "Achievement unlocked",
          body: "You unlocked your first certification badge.",
          actorNickname: null,
          actorAvatarUrl: null,
          aggregateCount: 1,
          targetUrl: "/me/achievements",
          fallbackUrl: "/me",
          status: "READ",
          createdAt: new Date(Date.now() - 3600_000).toISOString(),
        },
      ],
      nextCursor: null,
      hasMore: false,
    };
  }

  if (normalizedPath.match(/\/notifications\/[^/]+\/read$/) && requestMethod === "PATCH") {
    const notificationUid = normalizedPath.split("/")[2];
    return {
      notificationUid,
      status: "READ",
      readAt: new Date().toISOString(),
    };
  }

  if (normalizedPath.includes("/notifications/read-all") && requestMethod === "PATCH") {
    return {
      updatedCount: 3,
    };
  }

  if (normalizedPath === "/me/achievements/summary" && requestMethod === "GET") {
    return {
      totalAchievements: 6,
      unlockedCount: 2,
      unlockedPercentage: 33,
      totalBugEarned: 180,
      latestUnlock: {
        achievementCode: "FIRST_CHECKIN",
        displayName: "First check-in",
        unlockedAt: new Date(Date.now() - 2 * 86400_000).toISOString(),
      },
      tierBreakdown: {
        BRONZE: { total: 3, unlocked: 2 },
        SILVER: { total: 2, unlocked: 0 },
        GOLD: { total: 1, unlocked: 0 },
      },
    };
  }

  if (normalizedPath === "/me/achievements" && requestMethod === "GET") {
    return {
      items: [
        {
          achievementCode: "FIRST_CHECKIN",
          displayName: "First check-in",
          description: "Complete your first diagnosis session.",
          iconUrl: null,
          tier: "BRONZE",
          isSecret: false,
          isUnlocked: true,
          unlockedAt: new Date(Date.now() - 2 * 86400_000).toISOString(),
          rewardBug: 50,
        },
        {
          achievementCode: "WATCHLIST_STARTER",
          displayName: "Watchlist starter",
          description: "Add your first specimen to watchlist.",
          iconUrl: null,
          tier: "BRONZE",
          isSecret: false,
          isUnlocked: true,
          unlockedAt: new Date(Date.now() - 86400_000).toISOString(),
          rewardBug: 30,
        },
        {
          achievementCode: "MOMENTUM_HUNTER",
          displayName: "Momentum hunter",
          description: "Track a top mover before settlement.",
          iconUrl: null,
          tier: "SILVER",
          isSecret: false,
          isUnlocked: false,
          unlockedAt: null,
          rewardBug: 100,
        },
      ],
      nextCursor: null,
      hasMore: false,
    };
  }

  if (normalizedPath.match(/\/me\/achievements\/[^/]+$/) && requestMethod === "GET") {
    const achievementCode = normalizedPath.split("/").at(-1) || "FIRST_CHECKIN";
    const unlocked = achievementCode === "FIRST_CHECKIN" || achievementCode === "WATCHLIST_STARTER";

    return {
      achievementCode,
      displayName:
        achievementCode === "FIRST_CHECKIN"
          ? "First check-in"
          : achievementCode === "WATCHLIST_STARTER"
            ? "Watchlist starter"
            : "Momentum hunter",
      description:
        achievementCode === "FIRST_CHECKIN"
          ? "Complete your first diagnosis session."
          : achievementCode === "WATCHLIST_STARTER"
            ? "Add your first specimen to watchlist."
            : "Track a top mover before settlement.",
      iconUrl: null,
      tier: achievementCode === "MOMENTUM_HUNTER" ? "SILVER" : "BRONZE",
      rewardBug: achievementCode === "MOMENTUM_HUNTER" ? 100 : 50,
      totalUnlockedCount: achievementCode === "MOMENTUM_HUNTER" ? 120 : 892,
      totalUnlockedPercentage: achievementCode === "MOMENTUM_HUNTER" ? 6.8 : 51.4,
      isUnlocked: unlocked,
      unlockedAt: unlocked ? new Date(Date.now() - 86400_000).toISOString() : null,
    };
  }

  if (normalizedPath === "/wallet" && requestMethod === "GET") {
    return {
      userId: "mock_user_1",
      balance: 42,
      totalEarned: 128,
      totalSpent: 86,
      dailyClaimed: false,
      dailyAmount: 8,
      voteCost: 1,
    };
  }

  if (normalizedPath === "/wallet/ledger" && requestMethod === "GET") {
    return {
      items: [
        {
          ledgerId: "ledger_mock_1",
          delta: 8,
          balanceAfter: 42,
          reason: "DAILY",
          refId: "daily_mock",
          refType: "SYSTEM",
          createdAt: new Date().toISOString(),
        },
        {
          ledgerId: "ledger_mock_2",
          delta: -1,
          balanceAfter: 34,
          reason: "VOTE",
          refId: "battle_mock",
          refType: "ARENA",
          createdAt: new Date(Date.now() - 3600_000).toISOString(),
        },
      ],
      nextCursor: null,
      hasMore: false,
    };
  }

  if (normalizedPath === "/wallet/daily" && requestMethod === "POST") {
    return {
      claimed: true,
      amount: 8,
      balanceAfter: 50,
      alreadyClaimed: false,
    };
  }

  if (normalizedPath === "/bet" && requestMethod === "POST") {
    const payload = (() => {
      if (typeof body !== "string") {
        return {};
      }
      try {
        return JSON.parse(body) as {
          specimenId?: string;
          direction?: string;
          amount?: number;
        };
      } catch {
        return {};
      }
    })();

    const amount = Number.isFinite(payload.amount) ? Number(payload.amount) : 100;
    const direction = typeof payload.direction === "string" ? payload.direction : "UP";
    const specimenId = typeof payload.specimenId === "string" ? payload.specimenId : "mock_sp_0";

    return {
      orderId: `bet_ord_${Date.now()}`,
      specimenId,
      direction,
      amount,
      oddsAtPlace: 1.92,
      settleDate: new Date().toISOString().slice(0, 10),
      status: "PENDING",
      walletBalanceAfter: 42 - amount,
    };
  }

  if (normalizedPath === "/bet/active" && requestMethod === "GET") {
    return {
      date: new Date().toISOString().slice(0, 10),
      orders: [
        {
          orderId: "bet_ord_active_1",
          specimenId: "mock_sp_0",
          specimenTitle: "asylum-labs/sample-repo",
          direction: "UP",
          amount: 120,
          oddsAtPlace: 1.88,
          currentOdds: 1.92,
          status: "PENDING",
        },
      ],
      totalStaked: 120,
    };
  }

  if (normalizedPath === "/bet/history" && requestMethod === "GET") {
    return {
      orders: [
        {
          orderId: "bet_ord_hist_1",
          specimenId: "mock_sp_0",
          specimenTitle: "asylum-labs/sample-repo",
          direction: "UP",
          amount: 100,
          oddsAtPlace: 1.9,
          status: "WON",
          payout: 188,
          moonDoomBonus: 0,
          settleDate: new Date().toISOString().slice(0, 10),
          settledAt: new Date(Date.now() - 3600_000).toISOString(),
        },
        {
          orderId: "bet_ord_hist_2",
          specimenId: "mock_sp_1",
          specimenTitle: "asylum-labs/sample-repo-1",
          direction: "DOWN",
          amount: 90,
          oddsAtPlace: 2.1,
          status: "LOST",
          payout: 0,
          moonDoomBonus: 0,
          settleDate: new Date(Date.now() - 86400_000).toISOString().slice(0, 10),
          settledAt: new Date(Date.now() - 86400_000).toISOString(),
        },
      ],
      nextCursor: null,
      hasMore: false,
    };
  }

  if (normalizedPath === "/settlement/today" && requestMethod === "GET") {
    return {
      date: new Date().toISOString().slice(0, 10),
      settlements: [
        {
          specimenId: "mock_sp_0",
          specimenTitle: "asylum-labs/sample-repo",
          eloOpen: 1320,
          eloClose: 1334,
          deltaR: 14,
          outcome: "UP",
          isMoonDoom: false,
          myOrders: [
            {
              orderId: "bet_ord_active_1",
              direction: "UP",
              amount: 120,
              status: "WON",
              payout: 230,
              moonDoomBonus: 0,
            },
          ],
        },
      ],
      nextCursor: null,
      hasMore: false,
    };
  }

  if (normalizedPath.match(/\/specimens\/[^/]+\/bet-summary$/) && requestMethod === "GET") {
    const specimenId = normalizedPath.split("/")[2];
    return {
      specimenId,
      date: new Date().toISOString().slice(0, 10),
      poolUp: 1400,
      poolFlat: 620,
      poolDown: 980,
      houseUp: 300,
      houseFlat: 300,
      houseDown: 300,
      oddsUp: 1.92,
      oddsFlat: 3.05,
      oddsDown: 2.34,
      rakeRate: 0.05,
      betCutoffAt: new Date(Date.now() + 6 * 3600_000).toISOString(),
      poolStatus: "OPEN",
      totalBettors: 38,
      houseActive: true,
      ipoStatus: "IPO",
      currentElo: 1332,
      eloOpenToday: 1320,
      deltaRSoFar: 12,
      correctionToday: 0,
      moonDoomThreshold: 45,
      canBet: false,
      betBlockReasonCode: "VOTE_REQUIRED",
      hasVotedForSpecimenToday: false,
    };
  }

  if (normalizedPath === "/comments" && requestMethod === "GET") {
    const specimenId = path.includes("specimenId=")
      ? new URLSearchParams(path.split("?")[1]).get("specimenId")
      : "mock_sp_0";
    return {
      items: [
        {
          commentId: "cmt_mock_1",
          specimenId: specimenId || "mock_sp_0",
          authorUserId: "12345",
          contentPreview: "This repo compiles only when the moon phase is correct.",
          resonanceCount: 7,
          isChiefConclusion: false,
          status: "ACTIVE",
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
        {
          commentId: "cmt_mock_2",
          specimenId: specimenId || "mock_sp_0",
          authorUserId: "23456",
          contentPreview: "I admire the confidence of shipping this into production.",
          resonanceCount: 4,
          isChiefConclusion: false,
          status: "ACTIVE",
          createdAt: new Date(Date.now() - 5400_000).toISOString(),
          updatedAt: new Date(Date.now() - 5400_000).toISOString(),
        },
      ],
      nextCursor: null,
    };
  }

  if (normalizedPath === "/comments" && requestMethod === "POST") {
    const payload = (() => {
      if (typeof body !== "string") {
        return {};
      }
      try {
        return JSON.parse(body) as {
          specimenId?: string;
        };
      } catch {
        return {};
      }
    })();
    return {
      commentId: `cmt_mock_${Date.now()}`,
      specimenId: payload.specimenId || "mock_sp_0",
      status: "ACTIVE",
      bugCost: 1,
      balanceAfter: 41,
      billingLedgerId: "ledger_mock_new",
      createdAt: new Date().toISOString(),
    };
  }

  if (normalizedPath.match(/\/comments\/[^/]+\/resonance$/) && requestMethod === "POST") {
    const commentId = normalizedPath.split("/")[2];
    return {
      commentId,
      resonanceCount: 8,
      resonated: true,
    };
  }

  if (normalizedPath.match(/\/comments\/[^/]+\/report$/) && requestMethod === "POST") {
    return {
      reported: true,
      ticketId: `ticket_${Date.now()}`,
    };
  }

  if (normalizedPath.match(/\/comments\/[^/]+\/context$/) && requestMethod === "GET") {
    const commentId = normalizedPath.split("/")[2];
    return {
      commentId,
      author: {
        userId: "12345",
        username: "asylum-labs",
        avatarUrl: "https://api.dicebear.com/7.x/identicon/svg?seed=asylum",
      },
      contentPreview: "This repo compiles only when the moon phase is correct.",
      status: "ACTIVE",
    };
  }

  if (normalizedPath.match(/\/comments\/[^/]+$/) && requestMethod === "DELETE") {
    return {
      deleted: true,
      refundDelta: 1,
    };
  }

  if (normalizedPath.match(/\/specimens\/[^/]+\/comments\/top-roast$/) && requestMethod === "GET") {
    return {
      hasTopRoast: true,
      commentId: "cmt_mock_1",
      author: {
        userId: "12345",
        username: "asylum-labs",
        avatarUrl: "https://api.dicebear.com/7.x/identicon/svg?seed=asylum",
      },
      contentPreview: "This repo compiles only when the moon phase is correct.",
      resonanceCount: 7,
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
        repoId: 123456789,
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
