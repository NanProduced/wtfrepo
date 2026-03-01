import { backendFetch, BackendError } from "@/shared/lib/backend-client";
import { NextResponse } from "next/server";
import { randomUUID } from "crypto";

export async function POST(
  request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;
    const body = await request.json();

    // Ensure we have an idempotency key, either from client or generated here
    const idempotencyKey = body.idempotencyKey || `idem-${randomUUID()}`;
    const requestId = `req-${randomUUID()}`;

    const data = await backendFetch(`/specimens/${id}/hype`, {
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
