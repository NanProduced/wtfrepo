import { backendFetch } from "@/shared/lib/backend-client";
import { toErrorResponse } from "@/shared/lib/bff-route";
import { NextResponse } from "next/server";

export async function GET(
  _request: Request,
  { params }: { params: Promise<{ achievementCode: string }> }
) {
  try {
    const { achievementCode } = await params;
    const data = await backendFetch(`/me/achievements/${achievementCode}`);
    return NextResponse.json(data);
  } catch (error) {
    return toErrorResponse(error);
  }
}
