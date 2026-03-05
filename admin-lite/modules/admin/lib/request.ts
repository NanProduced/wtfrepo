"use client";

import type {
  AdminAlert,
  AuditLogItem,
  BannedUser,
  BroadcastItem,
  ImportSpecimenResult,
  MatchQualityReport,
  ModerationQueueItem,
  PageMeta,
  SafetyTicket,
  SessionStatus,
} from "@/modules/admin/types";

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  query?: Record<string, string | number | boolean | null | undefined>;
  body?: unknown;
}

interface PageResponse<T> {
  items: T[];
  pagination: PageMeta;
}

export interface SubmitSpecimenInput {
  languages: Array<{ name: string; percentage?: number }>;
  tags: Array<{ dimensionKey: string; tagKey: string }>;
  readmeCuration: {
    excerpts: Array<{
      excerptType: string;
      candidateId?: string | null;
      text: string;
      translatedTextZh?: string | null;
      translationMeta?: Record<string, unknown> | null;
      priority?: number | null;
    }>;
  };
  readmeSnapshotDecision?: {
    enabled: boolean;
  };
  officialCommentary: {
    oneLinerZh: string;
    oneLinerEn: string;
    arenaReasonZh: string;
    arenaReasonEn: string;
  };
  codeHighlights?: Array<{
    title: string;
    candidateId?: string | null;
    codeLanguage: string;
    snippet: string;
    explainText: string;
    priority?: number | null;
  }>;
  repoIdentity: {
    owner: {
      githubLogin: string;
      githubUserId: string;
      githubAvatarUrl?: string;
      githubHtmlUrl?: string;
      contributions?: number;
    };
    maintainers?: Array<{
      githubLogin: string;
      githubUserId: string;
      githubAvatarUrl?: string;
      githubHtmlUrl?: string;
      contributions?: number;
    }>;
    contributors?: Array<{
      githubLogin: string;
      githubUserId: string;
      githubAvatarUrl?: string;
      githubHtmlUrl?: string;
      contributions?: number;
    }>;
  };
  note?: string;
}

function buildUrl(path: string, query?: RequestOptions["query"]) {
  const params = new URLSearchParams();
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined || value === null || value === "") {
        continue;
      }
      params.set(key, String(value));
    }
  }
  const suffix = params.toString();
  return suffix ? `/api/admin/proxy/${path}?${suffix}` : `/api/admin/proxy/${path}`;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await fetch(buildUrl(path, options.query), {
    method: options.method ?? "GET",
    headers: options.body
      ? {
          "Content-Type": "application/json",
        }
      : undefined,
    body: options.body ? JSON.stringify(options.body) : undefined,
    cache: "no-store",
  });

  const contentType = response.headers.get("content-type") ?? "";
  const payload = contentType.includes("application/json")
    ? ((await response.json()) as T)
    : (({ message: await response.text() } as unknown) as T);

  if (!response.ok) {
    const message =
      typeof payload === "object" &&
      payload !== null &&
      "message" in payload &&
      typeof (payload as Record<string, unknown>).message === "string"
        ? ((payload as Record<string, unknown>).message as string)
        : `Request failed: ${response.status}`;
    throw new Error(message);
  }

  return payload;
}

export async function getSessionStatus() {
  const response = await fetch("/api/admin/session", {
    method: "GET",
    cache: "no-store",
  });
  const payload = (await response.json()) as SessionStatus;
  if (!response.ok) {
    throw new Error("Failed to load session.");
  }
  return payload;
}

export async function getProfile() {
  return request<SessionStatus["profile"]>("platform/me");
}

export async function listSafetyTickets(page = 1, pageSize = 8) {
  return request<PageResponse<SafetyTicket>>("platform/safety-tickets", {
    query: { page, pageSize },
  });
}

export async function listAlerts(page = 1, pageSize = 8) {
  return request<PageResponse<AdminAlert>>("platform/alerts", {
    query: { page, pageSize },
  });
}

export async function acknowledgeAlert(alertId: string) {
  return request<AdminAlert>(`platform/alerts/${alertId}/acknowledge`, {
    method: "PATCH",
  });
}

export async function listAuditLogs(
  page = 1,
  pageSize = 20,
  filters?: {
    action?: string;
    targetType?: string;
    targetId?: string;
    operatorId?: string;
  },
) {
  return request<PageResponse<AuditLogItem>>("platform/audit-logs", {
    query: {
      page,
      pageSize,
      action: filters?.action,
      targetType: filters?.targetType,
      targetId: filters?.targetId,
      operatorId: filters?.operatorId,
    },
  });
}

export async function importSpecimen(githubUrl: string) {
  return request<ImportSpecimenResult>("specimens/import", {
    method: "POST",
    body: { githubUrl },
  });
}

export async function submitSpecimen(specimenId: string, payload: SubmitSpecimenInput) {
  return request<{ specimenId: string; status: string }>(`specimens/${specimenId}/submit`, {
    method: "POST",
    body: payload,
  });
}

export async function reviewSpecimen(specimenId: string, action: "APPROVE" | "REJECT", reason?: string) {
  return request<{ specimenId: string; status: string; reviewedBy: string; reviewedAt: string }>(
    `specimens/${specimenId}/review`,
    {
      method: "POST",
      body: {
        action,
        reason: reason?.trim() || undefined,
      },
    },
  );
}

export async function deactivateSpecimen(specimenId: string, reason?: string) {
  return request<{ specimenId: string; status: string; reviewedBy: string; reviewedAt: string }>(
    `specimens/${specimenId}/deactivate`,
    {
      method: "POST",
      body: { reason: reason?.trim() || undefined },
    },
  );
}

export async function updateSpecimenTags(specimenId: string, tags: Array<{ dimensionKey: string; tagKey: string }>) {
  return request<{ specimenId: string; status: string }>(`specimens/${specimenId}/tags`, {
    method: "POST",
    body: { tags },
  });
}

export async function listModerationQueue(page = 1, pageSize = 20) {
  return request<PageResponse<ModerationQueueItem>>("comments/moderation-queue", {
    query: { page, pageSize },
  });
}

export async function moderateComment(
  commentId: string,
  action: "block" | "unblock" | "approve" | "delete",
) {
  return request<{
    commentId: string;
    specimenId: string;
    authorUserId: string;
    previousStatus: string;
    status: string;
    refundAmount: number;
  }>(`comments/${commentId}/${action}`, {
    method: "POST",
  });
}

export async function getMatchQuality() {
  return request<MatchQualityReport>("arena/match-quality");
}

export async function forceRecalc(reason?: string) {
  return request<unknown>("arena/force-recalc", {
    method: "POST",
    body: reason ? { reason } : {},
  });
}

export async function resetElo(reason?: string) {
  return request<unknown>("arena/reset-elo", {
    method: "POST",
    body: reason ? { reason } : {},
  });
}

export async function grantBug(input: {
  userId: string;
  delta: number;
  reason: string;
  note?: string;
}) {
  return request<unknown>("economy/grant-bug", {
    method: "POST",
    body: input,
  });
}

export async function forceSettle(input: {
  specimenId: string;
  reason?: string;
  confirm: boolean;
}) {
  return request<unknown>("betting/force-settle", {
    method: "POST",
    body: input,
  });
}

export async function updateHouseConfig(input: {
  specimenId: string;
  houseBudget: number;
  weightUp: number;
  weightFlat: number;
  weightDown: number;
}) {
  return request<unknown>("betting/house-config", {
    method: "POST",
    body: input,
  });
}

export async function listBannedUsers(page = 1, pageSize = 10, active?: boolean) {
  return request<PageResponse<BannedUser>>("users/banned", {
    query: { page, pageSize, active },
  });
}

export async function banUser(userId: string, input: { banType: string; reason: string; expiresAt?: string }) {
  return request<unknown>(`users/${userId}/ban`, {
    method: "POST",
    body: input,
  });
}

export async function unbanUser(userId: string) {
  return request<unknown>(`users/${userId}/unban`, {
    method: "POST",
  });
}

export async function listBroadcasts(page = 1, pageSize = 10) {
  return request<PageResponse<BroadcastItem>>("platform/broadcasts", {
    query: { page, pageSize },
  });
}

export async function createBroadcast(input: {
  title: string;
  body?: string;
  targetUrl?: string;
}) {
  return request<BroadcastItem>("platform/broadcasts", {
    method: "POST",
    body: input,
  });
}

export async function saveAdminToken(token: string) {
  await fetch("/api/admin/session", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token }),
  });
}

export async function exchangeToken(input: {
  mode: "auto" | "login" | "bootstrap";
  userToken: string;
  email?: string;
}) {
  const response = await fetch("/api/admin/session/exchange", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  const payload = (await response.json()) as unknown;
  if (!response.ok) {
    const message =
      typeof payload === "object" &&
      payload !== null &&
      "message" in payload &&
      typeof (payload as Record<string, unknown>).message === "string"
        ? ((payload as Record<string, unknown>).message as string)
        : "Token exchange failed.";
    throw new Error(message);
  }
  return payload;
}

export async function clearAdminSession() {
  await fetch("/api/admin/session", {
    method: "DELETE",
  });
}
