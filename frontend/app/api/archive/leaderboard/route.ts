import { backendFetch, BackendError } from "@/shared/lib/backend-client";
import { NextResponse } from "next/server";

export async function GET(request: Request) {
  try {
    const { searchParams } = new URL(request.url);
    const data = await backendFetch(`/archive/leaderboard?${searchParams.toString()}`);
    return NextResponse.json(data);
  } catch (error) {
    if (error instanceof BackendError) {
      return NextResponse.json(error.response, { status: error.response.status });
    }
    return NextResponse.json({ error: "Internal Server Error" }, { status: 500 });
  }
}
