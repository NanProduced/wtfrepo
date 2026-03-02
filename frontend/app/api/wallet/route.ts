import { backendFetch, BackendError } from "@/shared/lib/backend-client";
import { NextResponse } from "next/server";

export async function GET() {
  try {
    const data = await backendFetch("/wallet");
    return NextResponse.json(data);
  } catch (error) {
    if (error instanceof BackendError) {
      return NextResponse.json(error.response, { status: error.response.status });
    }
    return NextResponse.json({ error: "Internal Server Error" }, { status: 500 });
  }
}

