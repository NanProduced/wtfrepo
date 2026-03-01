import { backendFetch } from "@/shared/lib/backend-client";
import { resolveIdempotencyKey, toErrorResponse } from "@/shared/lib/bff-route";
import { NextResponse } from "next/server";

export async function POST(request: Request) {
  try {
    const body = await request.json();
    const idempotencyKey = resolveIdempotencyKey(request);
    const data = await backendFetch("/me/narrator-preference/merge-on-login", {
      method: "POST",
      headers: {
        "X-Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify(body),
    });
    return NextResponse.json(data);
  } catch (error) {
    return toErrorResponse(error);
  }
}
