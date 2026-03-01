import {
  AchievementDetailResponse,
  AchievementsListResponse,
  AchievementsSummaryResponse,
} from "@/shared/types/achievements";

export async function getMyAchievements(params: {
  status?: string;
  cursor?: string;
  limit?: number;
} = {}) {
  const searchParams = new URLSearchParams();
  if (params.status) searchParams.set("status", params.status);
  if (params.cursor) searchParams.set("cursor", params.cursor);
  if (typeof params.limit === "number") searchParams.set("limit", String(params.limit));

  const suffix = searchParams.toString();
  const res = await fetch(`/api/me/achievements${suffix ? `?${suffix}` : ""}`, {
    cache: "no-store",
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<AchievementsListResponse>;
}

export async function getMyAchievementsSummary() {
  const res = await fetch("/api/me/achievements/summary", { cache: "no-store" });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<AchievementsSummaryResponse>;
}

export async function getAchievementDetail(achievementCode: string) {
  const res = await fetch(`/api/me/achievements/${achievementCode}`, {
    cache: "no-store",
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<AchievementDetailResponse>;
}
