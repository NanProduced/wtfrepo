import { randomUUID } from "node:crypto";
import { NextRequest, NextResponse } from "next/server";
import { cookies } from "next/headers";
import { getBackendBaseUrl } from "@/lib/admin-env";
import { ADMIN_TOKEN_COOKIE } from "@/lib/admin-session";

const COOKIE_MAX_AGE_SECONDS = 60 * 60 * 8;

function createResponse(status: number, body: unknown) {
  return NextResponse.json(body, { status });
}

export async function GET() {
  const cookieStore = await cookies();
  const token = cookieStore.get(ADMIN_TOKEN_COOKIE)?.value;
  if (!token) {
    return createResponse(200, { authenticated: false });
  }

  const backendUrl = `${getBackendBaseUrl()}/api/v1/admin/platform/me`;
  const response = await fetch(backendUrl, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Request-Id": randomUUID(),
    },
    cache: "no-store",
  });

  if (!response.ok) {
    return createResponse(200, { authenticated: false });
  }

  const profile = await response.json();
  return createResponse(200, {
    authenticated: true,
    profile,
  });
}

export async function POST(request: NextRequest) {
  const payload = (await request.json()) as { token?: string };
  const token = payload.token?.trim();
  if (!token) {
    return createResponse(400, { message: "Missing token." });
  }

  const response = createResponse(200, { ok: true });
  response.cookies.set({
    name: ADMIN_TOKEN_COOKIE,
    value: token,
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    maxAge: COOKIE_MAX_AGE_SECONDS,
    path: "/",
  });
  return response;
}

export async function DELETE() {
  const response = createResponse(200, { ok: true });
  response.cookies.set({
    name: ADMIN_TOKEN_COOKIE,
    value: "",
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    maxAge: 0,
    path: "/",
  });
  return response;
}
