import { backendFetch, BackendError } from "@/shared/lib/backend-client";
import { NextResponse } from "next/server";
import { randomUUID } from "crypto";

export async function POST(request: Request) {
  try {
    const body = await request.json();
    const requestId = `req-${randomUUID()}`;
    const idempotencyKey = request.headers.get("X-Idempotency-Key") || `idem-${randomUUID()}`;
    const data = await backendFetch("/bet", {
      method: "POST",
      headers: {
        "X-Request-Id": requestId,
        "X-Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify(body),
    });
    return NextResponse.json(data, { status: 201 });
  } catch (error) {
    if (error instanceof BackendError) {
      return NextResponse.json(error.response, { status: error.response.status });
    }
    return NextResponse.json({ error: "Internal Server Error" }, { status: 500 });
  }
}
