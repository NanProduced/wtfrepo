export const ADMIN_OAUTH_STATE_STORAGE_KEY = "wtf_admin_lite_oauth_state";

export function generateOAuthState(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

