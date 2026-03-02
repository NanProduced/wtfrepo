import Link from "next/link";
import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Medal, UserRound, ChevronRight, Eye, Trophy, Flame, BellRing, Coins } from "lucide-react";
import { auth } from "@/shared/config/auth";
import { backendFetch } from "@/shared/lib/backend-client";
import type { WatchlistItem } from "@/shared/types/specimen";
import { WalletQuickPanel } from "@/modules/me/components/wallet-quick-panel";
import { getTranslations } from "next-intl/server";

interface MeHubPageProps {
  params: Promise<{ locale: string }>;
}

interface WatchlistPreviewResponse {
  items: WatchlistItem[];
}

function formatDayLabel(isoDate: string, locale: string) {
  const parsed = new Date(isoDate);
  if (Number.isNaN(parsed.getTime())) {
    return "-";
  }

  return new Intl.DateTimeFormat(locale.startsWith("zh") ? "zh-CN" : "en-US", {
    month: "short",
    day: "numeric",
  }).format(parsed);
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
  let watchlistPreview: WatchlistItem[] = [];
  try {
    const watchlist = await backendFetch<WatchlistPreviewResponse>("/watchlist/items?limit=4&sort=LATEST");
    watchlistPreview = watchlist.items;
  } catch (error) {
    console.error("[MeHub] failed to load watchlist preview:", error);
  }

  return (
    <div className="container mx-auto min-h-screen max-w-5xl px-4 pb-28 pt-24">
      <header className="mb-6 rounded-2xl border border-white/10 bg-zinc-900/70 p-6">
        <p className="text-xs text-zinc-500">{t("personal_center")}</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight text-zinc-100">{displayName}</h1>
        <WalletQuickPanel locale={locale} initialBalance={session.user?.bugBalance ?? null} />
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

        <Link
          href={`/${locale}/me/betting`}
          className="group rounded-2xl border border-white/10 bg-zinc-900/70 p-5 transition-colors hover:border-primary/40"
        >
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-zinc-500">{t("betting_block.badge")}</p>
              <h2 className="mt-1 text-xl font-semibold text-zinc-100">{t("betting_block.title")}</h2>
              <p className="mt-2 text-sm text-zinc-400">{t("betting_block.description")}</p>
            </div>
            <Coins className="h-5 w-5 text-primary" />
          </div>
          <div className="mt-4 inline-flex items-center gap-1 text-xs text-primary">
            {t("betting_block.cta")}
            <ChevronRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" />
          </div>
        </Link>

        <Link
          href={`/${locale}/me/notifications`}
          className="group rounded-2xl border border-white/10 bg-zinc-900/70 p-5 transition-colors hover:border-primary/40 md:col-span-2"
        >
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-zinc-500">{t("notifications_block.badge")}</p>
              <h2 className="mt-1 text-xl font-semibold text-zinc-100">{t("notifications_block.title")}</h2>
              <p className="mt-2 text-sm text-zinc-400">{t("notifications_block.description")}</p>
            </div>
            <BellRing className="h-5 w-5 text-primary" />
          </div>
          <div className="mt-4 inline-flex items-center gap-1 text-xs text-primary">
            {t("notifications_block.cta")}
            <ChevronRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" />
          </div>
        </Link>
      </section>

      <section className="mt-6 rounded-2xl border border-white/10 bg-zinc-900/70 p-5">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-sm text-zinc-500">{t("watchlist_block.badge")}</p>
            <h2 className="mt-1 text-xl font-semibold text-zinc-100">{t("watchlist_block.title")}</h2>
            <p className="mt-2 text-sm text-zinc-400">{t("watchlist_block.description")}</p>
          </div>
          <Eye className="h-5 w-5 text-zinc-300" />
        </div>

        {watchlistPreview.length > 0 ? (
          <ul className="mt-4 space-y-2">
            {watchlistPreview.map((item) => (
              <li key={item.specimenId}>
                <Link
                  href={`/${locale}/specimen/${item.specimenId}`}
                  className="flex items-center justify-between rounded-xl border border-white/10 bg-zinc-950/70 px-3 py-2 transition-colors hover:border-primary/40"
                >
                  <div className="min-w-0">
                    <p className="truncate text-sm text-zinc-100">{item.repoFullName}</p>
                    <p className="mt-1 text-xs text-zinc-500">
                      {t("watchlist_block.added_at", { day: formatDayLabel(item.addedAt, locale) })}
                    </p>
                  </div>
                  <div className="ml-3 flex shrink-0 items-center gap-3">
                    <span className="inline-flex items-center gap-1 font-mono text-xs text-primary">
                      <Trophy className="h-3.5 w-3.5" />
                      {t("watchlist_block.elo", { value: item.metrics.elo })}
                    </span>
                    <span className="inline-flex items-center gap-1 font-mono text-xs text-orange-300">
                      <Flame className="h-3.5 w-3.5" />
                      {t("watchlist_block.hype", { value: item.metrics.hype.toFixed(1) })}
                    </span>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-4 rounded-xl border border-dashed border-white/10 bg-zinc-950/60 p-4 text-sm text-zinc-500">
            {t("watchlist_block.empty")}
          </p>
        )}

        <div className="mt-4">
          <Link href={`/${locale}/archive`} className="inline-flex items-center gap-1 text-xs text-primary">
            {t("watchlist_block.cta")}
            <ChevronRight className="h-3.5 w-3.5" />
          </Link>
        </div>
      </section>
    </div>
  );
}
