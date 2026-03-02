import {
  CommentContextResponse,
  CommentDeleteResult,
  CommentListPage,
  CommentPublishPayload,
  CommentPublishResult,
  CommentReportPayload,
  CommentReportResult,
  CommentResonanceResult,
  CommentSort,
  CommentTopRoastResponse,
} from "@/shared/types/comments";

function createClientRequestId() {
  return `cmtrid-${crypto.randomUUID()}`;
}

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

export async function getSpecimenComments(params: {
  specimenId: string;
  sort?: CommentSort;
  cursor?: string;
  limit?: number;
}) {
  const suffix = toSearchParams({
    specimenId: params.specimenId,
    sort: params.sort,
    cursor: params.cursor,
    limit: params.limit,
  });

  const res = await fetch(`/api/comments?${suffix}`, { cache: "no-store" });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<CommentListPage>;
}

export async function publishComment(payload: Omit<CommentPublishPayload, "clientRequestId">) {
  const res = await fetch("/api/comments", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Idempotency-Key": `idem-${crypto.randomUUID()}`,
    },
    body: JSON.stringify({
      ...payload,
      clientRequestId: createClientRequestId(),
    }),
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<CommentPublishResult>;
}

export async function resonateComment(commentId: string) {
  const res = await fetch(`/api/comments/${commentId}/resonance`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Idempotency-Key": `idem-${crypto.randomUUID()}`,
    },
    body: JSON.stringify({
      clientRequestId: createClientRequestId(),
    }),
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<CommentResonanceResult>;
}

export async function reportComment(
  commentId: string,
  payload: Omit<CommentReportPayload, "clientRequestId">
) {
  const res = await fetch(`/api/comments/${commentId}/report`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Idempotency-Key": `idem-${crypto.randomUUID()}`,
    },
    body: JSON.stringify({
      ...payload,
      clientRequestId: createClientRequestId(),
    }),
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<CommentReportResult>;
}

export async function getCommentContext(commentId: string) {
  const res = await fetch(`/api/comments/${commentId}/context`, {
    cache: "no-store",
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<CommentContextResponse>;
}

export async function deleteComment(commentId: string) {
  const res = await fetch(`/api/comments/${commentId}`, {
    method: "DELETE",
    headers: {
      "X-Idempotency-Key": `idem-${crypto.randomUUID()}`,
    },
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<CommentDeleteResult>;
}

export async function getSpecimenTopRoast(specimenId: string) {
  const res = await fetch(`/api/specimens/${specimenId}/comments/top-roast`, {
    cache: "no-store",
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<CommentTopRoastResponse>;
}

