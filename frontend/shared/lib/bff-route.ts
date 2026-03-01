import { randomUUID } from "crypto";
import { NextResponse } from "next/server";
import { BackendError } from "@/shared/lib/backend-client";

export function createIdempotencyKey() {
  return `idem-${randomUUID()}`;
}

export function resolveIdempotencyKey(request: Request) {
  const existing = request.headers.get("X-Idempotency-Key");
  return existing && existing.trim().length > 0 ? existing.trim() : createIdempotencyKey();
}

export function toErrorResponse(error: unknown) {
  if (error instanceof BackendError) {
    return NextResponse.json(error.response, { status: error.response.status });
  }

  return NextResponse.json({ error: "Internal Server Error" }, { status: 500 });
}
