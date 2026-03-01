import { backendFetch } from "@/shared/lib/backend-client";
import { toErrorResponse } from "@/shared/lib/bff-route";
import { NextResponse } from "next/server";

export async function GET() {
  try {
    const data = await backendFetch("/me/achievements/summary");
    return NextResponse.json(data);
  } catch (error) {
    return toErrorResponse(error);
  }
}
