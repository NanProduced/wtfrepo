import {
  BetActiveView,
  BetHistoryPage,
  BetPlacePayload,
  BetPlaceResult,
  BetSummaryView,
  SettlementTodayPage,
} from "@/shared/types/betting";

function toSearchParams(params: Record<string, string | number | null | undefined>) {
  const searchParams = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === null || typeof value === "undefined") {
      continue;
    }
    searchParams.set(key, String(value));
  }
  return searchParams.toString();
}

export async function placeBet(payload: BetPlacePayload) {
  const res = await fetch("/api/bet", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Idempotency-Key": `idem-${crypto.randomUUID()}`,
    },
    body: JSON.stringify(payload),
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<BetPlaceResult>;
}

export async function getActiveBets() {
  const res = await fetch("/api/bet/active", {
    cache: "no-store",
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<BetActiveView>;
}

export async function getBetHistory(params: { cursor?: string; limit?: number } = {}) {
  const suffix = toSearchParams({
    cursor: params.cursor,
    limit: params.limit,
  });
  const res = await fetch(`/api/bet/history${suffix ? `?${suffix}` : ""}`, {
    cache: "no-store",
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<BetHistoryPage>;
}

export async function getSettlementToday(params: { cursor?: string; limit?: number } = {}) {
  const suffix = toSearchParams({
    cursor: params.cursor,
    limit: params.limit,
  });
  const res = await fetch(`/api/settlement/today${suffix ? `?${suffix}` : ""}`, {
    cache: "no-store",
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<SettlementTodayPage>;
}

export async function getSpecimenBetSummary(specimenId: string) {
  const res = await fetch(`/api/specimens/${specimenId}/bet-summary`, {
    cache: "no-store",
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<BetSummaryView>;
}
