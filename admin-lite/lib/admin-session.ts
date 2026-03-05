import { cookies } from "next/headers";
import { redirect } from "next/navigation";

export const ADMIN_TOKEN_COOKIE = "wtf_admin_lite_token";

export async function getAdminToken(): Promise<string | null> {
  const cookieStore = await cookies();
  return cookieStore.get(ADMIN_TOKEN_COOKIE)?.value ?? null;
}

export async function requireAdminToken(): Promise<string> {
  const token = await getAdminToken();
  if (!token) {
    redirect("/login");
  }
  return token;
}
