import { NextResponse } from "next/server";
import { backendFetch, BackendError } from "@/shared/lib/backend-client";

interface OAuthAuthorizePayload {
  redirectUri?: string;
  state?: string;
}

interface OAuthAuthorizeResponse {
  code: string;
  redirectUri: string;
  state: string;
  expiresAt: string;
}

const ADMIN_ROLE_REQUIRED = "admin_role_required";

function isNonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function tryBuildDenyRedirect(redirectUri: string, state: string, reason: string): string | null {
  try {
    const url = new URL(redirectUri);
    if (url.protocol !== "http:" && url.protocol !== "https:") {
      return null;
    }
    url.searchParams.set("error", reason);
    url.searchParams.set("state", state);
    return url.toString();
  } catch {
    return null;
  }
}

export async function POST(request: Request) {
  let payload: OAuthAuthorizePayload;
  try {
    payload = (await request.json()) as OAuthAuthorizePayload;
  } catch {
    return NextResponse.json({ message: "Invalid request payload." }, { status: 400 });
  }

  if (!isNonEmptyString(payload.redirectUri) || !isNonEmptyString(payload.state)) {
    return NextResponse.json(
      { message: "Missing redirectUri or state." },
      { status: 400 },
    );
  }

  try {
    const result = await backendFetch<OAuthAuthorizeResponse>("/admin/platform/oauth/authorize", {
      method: "POST",
      body: JSON.stringify({
        redirectUri: payload.redirectUri.trim(),
        state: payload.state.trim(),
      }),
    });

    const successRedirect = new URL(result.redirectUri);
    successRedirect.searchParams.set("code", result.code);
    successRedirect.searchParams.set("state", result.state);

    return NextResponse.json({
      redirectTo: successRedirect.toString(),
      expiresAt: result.expiresAt,
    });
  } catch (error) {
    if (error instanceof BackendError) {
      if (error.response.status === 401) {
        return NextResponse.json(error.response, { status: 401 });
      }
      if (error.response.status === 403) {
        const shouldRedirectBack = error.response.message === ADMIN_ROLE_REQUIRED;
        const redirectTo = shouldRedirectBack
          ? tryBuildDenyRedirect(payload.redirectUri.trim(), payload.state.trim(), "access_denied")
          : null;
        return NextResponse.json(
          {
            ...error.response,
            ...(redirectTo ? { redirectTo } : {}),
          },
          { status: 403 },
        );
      }
      return NextResponse.json(error.response, { status: error.response.status });
    }

    return NextResponse.json(
      { message: "Failed to issue admin authorization code." },
      { status: 500 },
    );
  }
}
