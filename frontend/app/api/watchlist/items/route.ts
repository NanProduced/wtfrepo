import { backendFetch, BackendError } from "@/shared/lib/backend-client";
import { NextResponse } from "next/server";
import { randomUUID } from "crypto";

export async function GET(request: Request) {
  try {
    const { searchParams } = new URL(request.url);
    const data = await backendFetch(`/watchlist/items?${searchParams.toString()}`);
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
    const data = await backendFetch("/watchlist/items", {
      method: "POST",
      headers: {
        "X-Request-Id": requestId,
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
