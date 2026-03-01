import { backendFetch, BackendError } from "@/shared/lib/backend-client";
import { NextResponse } from "next/server";

/**
 * BFF Route: GET /api/me
 * Proxies to Backend: GET /api/v1/me
 */
export async function GET() {
  try {
    const data = await backendFetch("/me");
    return NextResponse.json(data);
  } catch (error) {
    if (error instanceof BackendError) {
      return NextResponse.json(error.response, { status: error.response.status });
    }
    console.error("[BFF] /api/me error:", error);
    return NextResponse.json({ error: "Internal Server Error" }, { status: 500 });
  }
}
