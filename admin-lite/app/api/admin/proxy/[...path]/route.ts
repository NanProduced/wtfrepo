import { randomUUID } from "node:crypto";
import { NextRequest, NextResponse } from "next/server";
import { cookies } from "next/headers";
import { getBackendBaseUrl } from "@/lib/admin-env";
import { ADMIN_TOKEN_COOKIE } from "@/lib/admin-session";

type RouteContext = {
  params: Promise<{
    path: string[];
  }>;
};

const WRITABLE_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

async function handleProxy(request: NextRequest, context: RouteContext) {
  const cookieStore = await cookies();
  const token = cookieStore.get(ADMIN_TOKEN_COOKIE)?.value;

  if (!token) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { path } = await context.params;
  if (!path || path.length === 0) {
    return NextResponse.json({ message: "Missing admin path." }, { status: 400 });
  }

  const baseUrl = getBackendBaseUrl();
  const joinedPath = path.join("/");
  const search = request.nextUrl.search;
  const upstreamUrl = `${baseUrl}/api/v1/admin/${joinedPath}${search}`;
  const method = request.method.toUpperCase();

  const headers = new Headers();
  headers.set("Authorization", `Bearer ${token}`);
  headers.set("X-Request-Id", request.headers.get("X-Request-Id") ?? randomUUID());

  if (WRITABLE_METHODS.has(method)) {
    headers.set(
      "X-Idempotency-Key",
      request.headers.get("X-Idempotency-Key") ?? randomUUID(),
    );
  }

  const contentType = request.headers.get("Content-Type");
  if (contentType) {
    headers.set("Content-Type", contentType);
  }

  const bodyText =
    method === "GET" || method === "HEAD" ? undefined : await request.text();

  const upstream = await fetch(upstreamUrl, {
    method,
    headers,
    body: bodyText && bodyText.length > 0 ? bodyText : undefined,
    cache: "no-store",
  });

  const responseText = await upstream.text();
  const responseContentType =
    upstream.headers.get("content-type") ?? "application/json; charset=utf-8";

  return new NextResponse(responseText, {
    status: upstream.status,
    headers: {
      "content-type": responseContentType,
    },
  });
}

export async function GET(request: NextRequest, context: RouteContext) {
  return handleProxy(request, context);
}

export async function POST(request: NextRequest, context: RouteContext) {
  return handleProxy(request, context);
}

export async function PUT(request: NextRequest, context: RouteContext) {
  return handleProxy(request, context);
}

export async function PATCH(request: NextRequest, context: RouteContext) {
  return handleProxy(request, context);
}

export async function DELETE(request: NextRequest, context: RouteContext) {
  return handleProxy(request, context);
}
