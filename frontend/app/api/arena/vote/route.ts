import { backendFetch, BackendError } from "@/shared/lib/backend-client";
import { NextResponse } from "next/server";
import { randomUUID } from "crypto";

export async function POST(request: Request) {
  try {
    const body = await request.json();
    const idempotencyKey = body.idempotencyKey || `idem-${randomUUID()}`;
    const requestId = `req-${randomUUID()}`;

    const data = await backendFetch("/arena/vote", {
      method: "POST",
      headers: {
        "X-Request-Id": requestId,
        "X-Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify({
        ...body,
        idempotencyKey,
      }),
    });

    return NextResponse.json(data);
  } catch (error) {
    if (error instanceof BackendError) {
      return NextResponse.json(error.response, { status: error.response.status });
    }

    return NextResponse.json({ error: "Internal Server Error" }, { status: 500 });
  }
}
