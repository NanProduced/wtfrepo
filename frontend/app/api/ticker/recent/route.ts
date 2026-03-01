import { backendFetch } from "@/shared/lib/backend-client";
import { toErrorResponse } from "@/shared/lib/bff-route";
import { NextResponse } from "next/server";

export async function GET(request: Request) {
  try {
    const { searchParams } = new URL(request.url);
    const suffix = searchParams.toString();
    const path = `/ticker/recent${suffix ? `?${suffix}` : ""}`;
    const data = await backendFetch(path);
    return NextResponse.json(data);
  } catch (error) {
    return toErrorResponse(error);
  }
}
