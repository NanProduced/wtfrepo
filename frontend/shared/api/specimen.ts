/**
 * Client-side API Client for Specimen-related actions.
 * These call the Next.js BFF routes.
 */

import {
  ArchiveSpecimen,
  SpecimenDrawerData,
  SpecimenDetailData,
  SpecimenRepoIdentitiesResponse,
  TagConfigResponse,
  WatchlistItem
} from "@/shared/types/specimen";

export async function getArchiveSpecimens(params: {
  cursor?: string;
  limit?: number;
  sort?: "HOT" | "NEW" | "INSANE";
  tags?: string;
  q?: string;
}) {
  const searchParams = new URLSearchParams();
  if (params.cursor) searchParams.set("cursor", params.cursor);
  if (params.limit) searchParams.set("limit", params.limit.toString());
  if (params.sort) searchParams.set("sort", params.sort);
  if (params.tags) searchParams.set("tags", params.tags);
  if (params.q) searchParams.set("q", params.q);

  const res = await fetch(`/api/archive/specimens?${searchParams.toString()}`);
  if (!res.ok) throw await res.json();
  return res.json() as Promise<{ items: ArchiveSpecimen[]; nextCursor: string; hasMore: boolean }>;
}

export async function getSpecimenDrawer(id: string) {
  const res = await fetch(`/api/specimens/${id}/drawer`);
  if (!res.ok) throw await res.json();
  return res.json() as Promise<SpecimenDrawerData>;
}

export async function getSpecimenDetail(id: string) {
  const res = await fetch(`/api/specimens/${id}`);
  if (!res.ok) throw await res.json();
  return res.json() as Promise<SpecimenDetailData>;
}

export async function getSpecimenRepoIdentities(id: string) {
  const res = await fetch(`/api/specimens/${id}/repo-identities`);
  if (!res.ok) throw await res.json();
  return res.json() as Promise<SpecimenRepoIdentitiesResponse>;
}

export async function getTagConfig() {
  const res = await fetch("/api/tags");
  if (!res.ok) throw await res.json();
  return res.json() as Promise<TagConfigResponse>;
}

export async function getWatchlist(params: { cursor?: string; limit?: number; sort?: string } = {}) {
  const searchParams = new URLSearchParams();
  if (params.cursor) searchParams.set("cursor", params.cursor);
  if (params.limit) searchParams.set("limit", params.limit.toString());
  if (params.sort) searchParams.set("sort", params.sort);

  const res = await fetch(`/api/watchlist/items?${searchParams.toString()}`);
  if (!res.ok) throw await res.json();
  return res.json() as Promise<{ items: WatchlistItem[]; nextCursor: string; hasMore: boolean }>;
}

export async function addToWatchlist(specimenId: string, source: string = "DETAIL") {
  const res = await fetch("/api/watchlist/items", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ specimenId, source }),
  });
  if (!res.ok) throw await res.json();
  return res.json();
}

export async function removeFromWatchlist(specimenId: string) {
  const res = await fetch(`/api/watchlist/items/${specimenId}`, {
    method: "DELETE",
  });
  if (!res.ok) throw await res.json();
  return res.json();
}

export async function submitHype(specimenId: string, dimension: string) {
  const res = await fetch(`/api/specimens/${specimenId}/hype`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      dimension,
      clientTs: new Date().toISOString(),
      idempotencyKey: crypto.randomUUID(), // For optimism, client generates first
    }),
  });
  if (!res.ok) throw await res.json();
  return res.json();
}
