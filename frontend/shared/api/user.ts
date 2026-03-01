/**
 * Client-side API Client for User-related actions.
 * These call the Next.js BFF routes.
 */

export async function getMe() {
  const res = await fetch("/api/me");
  if (!res.ok) {
    const error = await res.json();
    throw error;
  }
  return res.json();
}

export async function updateUsername(username: string) {
  const res = await fetch("/api/me/username", {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ username }),
  });

  if (!res.ok) {
    const error = await res.json();
    throw error;
  }
  return res.json();
}
