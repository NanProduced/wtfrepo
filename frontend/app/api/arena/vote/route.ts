import { backendFetch, BackendError } from "@/shared/lib/backend-client";
import { NextResponse } from "next/server";
import { randomUUID } from "crypto";

export async function POST(request: Request) {
  try {
    const body = await request.json();
    const battleId = typeof body?.battleId === "string" ? body.battleId.trim() : "";
    const winner =
      typeof body?.winner === "string"
        ? body.winner.trim()
        : typeof body?.choice === "string"
          ? body.choice.trim()
          : "";

    if (!battleId || !winner) {
      return NextResponse.json(
        {
          error: "Bad Request",
          code: "ARENA_VOTE_INVALID_PAYLOAD",
          message: "battleId and winner are required",
        },
        { status: 400 }
      );
    }

    const idempotencyKey = body.idempotencyKey || `idem-${randomUUID()}`;
    const requestId = `req-${randomUUID()}`;

    const data = await backendFetch("/arena/vote", {
      method: "POST",
      headers: {
        "X-Request-Id": requestId,
        "X-Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify({
        battleId,
        winner,
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
