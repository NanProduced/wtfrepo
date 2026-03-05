const DEFAULT_BACKEND_BASE_URL = "http://localhost:8080";

function normalizeBackendBaseUrl(rawUrl: string): string {
  const trimmed = rawUrl.trim().replace(/\/+$/, "");
  return trimmed.replace(/\/api\/v1$/i, "");
}

export function getBackendBaseUrl(): string {
  const fromServerOnly = process.env.BACKEND_BASE_URL?.trim();
  if (fromServerOnly) {
    return normalizeBackendBaseUrl(fromServerOnly);
  }
  const fromPublic = process.env.NEXT_PUBLIC_API_URL?.trim();
  if (fromPublic) {
    return normalizeBackendBaseUrl(fromPublic);
  }
  return DEFAULT_BACKEND_BASE_URL;
}
