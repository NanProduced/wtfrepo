import { backendFetch, BackendError } from "@/shared/lib/backend-client";
import { NextResponse } from "next/server";

/**
 * BFF Route: PATCH /api/me/username
 * Proxies to Backend: PATCH /api/v1/me/username
 */
export async function PATCH(request: Request) {
  try {
    const body = await request.json();
    const data = await backendFetch("/me/username", {
      method: "PATCH",
      body: JSON.stringify(body),
    });
    return NextResponse.json(data);
  } catch (error) {
    if (error instanceof BackendError) {
      return NextResponse.json(error.response, { status: error.response.status });
    }
    console.error("[BFF] /api/me/username error:", error);
    return NextResponse.json({ error: "Internal Server Error" }, { status: 500 });
  }
}
