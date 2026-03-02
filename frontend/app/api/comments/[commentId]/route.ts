import { backendFetch, BackendError } from "@/shared/lib/backend-client";
import { NextResponse } from "next/server";

export async function DELETE(
  request: Request,
  { params }: { params: Promise<{ commentId: string }> }
) {
  try {
    const { commentId } = await params;
    const idempotencyKey = request.headers.get("X-Idempotency-Key");
    const data = await backendFetch(`/comments/${commentId}`, {
      method: "DELETE",
      headers: idempotencyKey ? { "X-Idempotency-Key": idempotencyKey } : undefined,
    });
    return NextResponse.json(data);
  } catch (error) {
    if (error instanceof BackendError) {
      return NextResponse.json(error.response, { status: error.response.status });
    }
    return NextResponse.json({ error: "Internal Server Error" }, { status: 500 });
  }
}
