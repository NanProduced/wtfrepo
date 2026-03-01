import Link from "next/link";
import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Medal, UserRound, Coins, ChevronRight } from "lucide-react";
import { auth } from "@/shared/config/auth";
import { getTranslations } from "next-intl/server";

interface MeHubPageProps {
  params: Promise<{ locale: string }>;
}

export async function generateMetadata({ params }: MeHubPageProps): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "me.page.meta" });

  return {
    title: t("title"),
    description: t("description"),
  };
}

export default async function MeHubPage({ params }: MeHubPageProps) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "me.page" });
  const session = await auth();

  if (!session?.backendAccessToken) {
    const callbackUrl = encodeURIComponent(`/${locale}/me`);
    redirect(`/${locale}/auth/login?callbackUrl=${callbackUrl}`);
  }

  const displayName = session.user?.username ?? session.user?.name ?? t("fallback_name");

  return (
    <div className="container mx-auto min-h-screen max-w-5xl px-4 pb-28 pt-24">
      <header className="mb-6 rounded-2xl border border-white/10 bg-zinc-900/70 p-6">
        <p className="text-xs text-zinc-500">{t("personal_center")}</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight text-zinc-100">{displayName}</h1>
        <div className="mt-4 inline-flex items-center gap-2 rounded-lg border border-white/10 bg-zinc-950/70 px-3 py-2 text-sm text-zinc-300">
          <Coins className="h-4 w-4 text-amber-300" />
          {t("bug_balance")}
          <span className="font-mono text-zinc-100">{session.user?.bugBalance ?? "-"}</span>
        </div>
      </header>

      <section className="grid gap-4 md:grid-cols-2">
        <Link
          href={`/${locale}/me/achievements`}
          className="group rounded-2xl border border-white/10 bg-zinc-900/70 p-5 transition-colors hover:border-primary/40"
        >
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-zinc-500">{t("achievement_block.badge")}</p>
              <h2 className="mt-1 text-xl font-semibold text-zinc-100">{t("achievement_block.title")}</h2>
              <p className="mt-2 text-sm text-zinc-400">
                {t("achievement_block.description")}
              </p>
            </div>
            <Medal className="h-5 w-5 text-primary" />
          </div>
          <div className="mt-4 inline-flex items-center gap-1 text-xs text-primary">
            {t("achievement_block.cta")}
            <ChevronRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" />
          </div>
        </Link>

        <article className="rounded-2xl border border-white/10 bg-zinc-900/70 p-5">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-zinc-500">{t("profile_block.badge")}</p>
              <h2 className="mt-1 text-xl font-semibold text-zinc-100">{t("profile_block.title")}</h2>
              <p className="mt-2 text-sm text-zinc-400">
                {t("profile_block.description")}
              </p>
            </div>
            <UserRound className="h-5 w-5 text-zinc-300" />
          </div>
          <p className="mt-4 text-xs text-zinc-500">
            {t("profile_block.footnote")}
          </p>
        </article>
      </section>
    </div>
  );
}
