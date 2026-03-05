import { OAuthLoginPanel } from "@/modules/admin/components/oauth-login-panel";
import { LoginPanel } from "@/modules/admin/components/login-panel";
import { getAdminToken } from "@/lib/admin-session";
import { redirect } from "next/navigation";

type LoginPageProps = {
  searchParams: Promise<{
    mode?: string;
  }>;
};

export default async function LoginPage({ searchParams }: LoginPageProps) {
  const params = await searchParams;
  const token = await getAdminToken();
  if (token) {
    redirect("/dashboard");
  }

  if (params.mode === "token") {
    return <LoginPanel />;
  }

  return <OAuthLoginPanel />;
}
