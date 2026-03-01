import { randomUUID } from "crypto";
import { auth } from "@/shared/config/auth";

const BACKEND_URL = process.env.API_URL || "http://localhost:8080/api/v1";

interface SessionTokenCarrier {
  user?: {
    backendAccessToken?: string;
  };
  backendAccessToken?: string;
}

function resolveBackendToken(session: unknown) {
  const carrier = session as SessionTokenCarrier | null;
  return carrier?.backendAccessToken || carrier?.user?.backendAccessToken || null;
}

export async function GET(request: Request) {
  const session = await auth();
  const token = resolveBackendToken(session);
  const url = new URL(request.url);
  const channels = url.searchParams.get("channels");
  const backendUrl = `${BACKEND_URL}/stream${channels ? `?channels=${encodeURIComponent(channels)}` : ""}`;
  const requestId = `req-${randomUUID()}`;
  const lastEventId = request.headers.get("Last-Event-ID");

  const headers = new Headers();
  headers.set("Accept", "text/event-stream");
  headers.set("Cache-Control", "no-cache");
  headers.set("X-Request-Id", requestId);
  if (lastEventId) {
    headers.set("Last-Event-ID", lastEventId);
  }
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(backendUrl, {
    method: "GET",
    headers,
    cache: "no-store",
  });

  if (!response.ok || !response.body) {
    return new Response("Failed to open event stream.", {
      status: response.status || 502,
    });
  }

  return new Response(response.body, {
    status: 200,
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache, no-transform",
      Connection: "keep-alive",
      "X-Accel-Buffering": "no",
    },
  });
}
