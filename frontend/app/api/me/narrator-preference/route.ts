import { backendFetch } from "@/shared/lib/backend-client";
import { resolveIdempotencyKey, toErrorResponse } from "@/shared/lib/bff-route";
import { NextResponse } from "next/server";

export async function GET() {
  try {
    const data = await backendFetch("/me/narrator-preference");
    return NextResponse.json(data);
  } catch (error) {
    return toErrorResponse(error);
  }
}

export async function PATCH(request: Request) {
  try {
    const body = await request.json();
    const idempotencyKey = resolveIdempotencyKey(request);
    const data = await backendFetch("/me/narrator-preference", {
      method: "PATCH",
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
