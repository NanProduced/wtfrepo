import { backendFetch, BackendError } from "@/shared/lib/backend-client";
import { NextResponse } from "next/server";
import { randomUUID } from "crypto";

export async function GET(request: Request) {
  try {
    const { searchParams } = new URL(request.url);
    const suffix = searchParams.toString();
    const data = await backendFetch(`/comments${suffix ? `?${suffix}` : ""}`);
    return NextResponse.json(data);
  } catch (error) {
    if (error instanceof BackendError) {
      return NextResponse.json(error.response, { status: error.response.status });
    }
    return NextResponse.json({ error: "Internal Server Error" }, { status: 500 });
  }
}

export async function POST(request: Request) {
  try {
    const body = await request.json();
    const requestId = `req-${randomUUID()}`;
    const idempotencyKey = request.headers.get("X-Idempotency-Key") || `idem-${randomUUID()}`;
    const data = await backendFetch("/comments", {
      method: "POST",
      headers: {
        "X-Request-Id": requestId,
        "X-Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify(body),
    });
    return NextResponse.json(data);
  } catch (error) {
    if (error instanceof BackendError) {
      return NextResponse.json(error.response, { status: error.response.status });
    }
    return NextResponse.json({ error: "Internal Server Error" }, { status: 500 });
  }
}

