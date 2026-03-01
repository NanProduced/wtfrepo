import {
  NarratorPreference,
  NarratorPreferenceMergePayload,
  NarratorPreferenceMergeResult,
  NarratorPreferencePatch,
  TickerItemClickPayload,
  TickerRecentPage,
} from "@/shared/types/narrator";

function createIdempotencyKey() {
  return `idem-${crypto.randomUUID()}`;
}

export async function getNarratorPreference() {
  const res = await fetch("/api/me/narrator-preference", { cache: "no-store" });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<NarratorPreference>;
}

export async function patchNarratorPreference(payload: NarratorPreferencePatch) {
  const res = await fetch("/api/me/narrator-preference", {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
      "X-Idempotency-Key": createIdempotencyKey(),
    },
    body: JSON.stringify(payload),
  });

  if (!res.ok) throw await res.json();
  return res.json() as Promise<NarratorPreference>;
}

export async function mergeNarratorPreferenceOnLogin(payload: NarratorPreferenceMergePayload) {
  const res = await fetch("/api/me/narrator-preference/merge-on-login", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Idempotency-Key": createIdempotencyKey(),
    },
    body: JSON.stringify(payload),
  });

  if (!res.ok) throw await res.json();
  return res.json() as Promise<NarratorPreferenceMergeResult>;
}

export async function getTickerRecent(params: { cursor?: string; limit?: number } = {}) {
  const searchParams = new URLSearchParams();
  if (params.cursor) searchParams.set("cursor", params.cursor);
  if (params.limit) searchParams.set("limit", String(params.limit));

  const suffix = searchParams.toString();
  const res = await fetch(`/api/ticker/recent${suffix ? `?${suffix}` : ""}`, {
    cache: "no-store",
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<TickerRecentPage>;
}

export async function postTickerItemClick(payload: TickerItemClickPayload) {
  const res = await fetch("/api/me/ticker-item-clicks", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Idempotency-Key": createIdempotencyKey(),
    },
    body: JSON.stringify(payload),
  });

  if (!res.ok) throw await res.json();
}
