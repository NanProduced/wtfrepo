import { auth } from "@/shared/config/auth";
import { redirect } from "next/navigation";
import { AdminAuthorizeCard } from "./admin-authorize-card";

type PageProps = {
  params: Promise<{ locale: string }>;
  searchParams: Promise<{ redirect_uri?: string; state?: string }>;
};

function isSupportedLocale(value: string): value is "en" | "zh" {
  return value === "en" || value === "zh";
}

function buildSelfCallbackPath(locale: "en" | "zh", redirectUri: string, state: string): string {
  const query = new URLSearchParams({
    redirect_uri: redirectUri,
    state,
  });
  return `/${locale}/auth/admin-authorize?${query.toString()}`;
}

export default async function AdminAuthorizePage({ params, searchParams }: PageProps) {
  const { locale: rawLocale } = await params;
  const locale: "en" | "zh" = isSupportedLocale(rawLocale) ? rawLocale : "en";
  const { redirect_uri: redirectUri, state } = await searchParams;

  if (!redirectUri || !state) {
    const fallback =
      locale === "zh"
        ? "缺少 redirect_uri 或 state 参数。"
        : "Missing redirect_uri or state parameter.";
    return (
      <div className="flex min-h-screen items-center justify-center bg-neutral-950 px-4 text-center text-sm text-neutral-300">
        {fallback}
      </div>
    );
  }

  const session = await auth();
  if (!session?.backendAccessToken) {
    const callbackUrl = buildSelfCallbackPath(locale, redirectUri, state);
    redirect(`/${locale}/auth/login?callbackUrl=${encodeURIComponent(callbackUrl)}`);
  }

  const displayName =
    session.user?.username || session.user?.name || session.user?.email || "unknown-user";

  return (
    <AdminAuthorizeCard
      locale={locale}
      redirectUri={redirectUri}
      state={state}
      displayName={displayName}
    />
  );
}

