import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { backendFetch } from "@/shared/lib/backend-client";
import { AchievementDetailResponse } from "@/shared/types/achievements";
import { ArrowLeft, Medal, Lock, Coins, Users } from "lucide-react";
import { auth } from "@/shared/config/auth";
import { getTranslations } from "next-intl/server";

interface AchievementDetailPageProps {
  params: Promise<{ achievementCode: string; locale: string }>;
}

export async function generateMetadata({
  params,
}: AchievementDetailPageProps): Promise<Metadata> {
  const { achievementCode, locale } = await params;
  const t = await getTranslations({ locale, namespace: "achievements.detail.meta" });
  try {
    const data = await backendFetch<AchievementDetailResponse>(`/me/achievements/${achievementCode}`);
    return {
      title: data.displayName,
      description: data.description,
    };
  } catch {
    return {
      title: t("fallback_title"),
    };
  }
}

export default async function AchievementDetailPage({ params }: AchievementDetailPageProps) {
  const { achievementCode, locale } = await params;
  const t = await getTranslations({ locale, namespace: "achievements.detail" });
  const session = await auth();

  if (!session?.backendAccessToken) {
    const callbackUrl = encodeURIComponent(`/${locale}/me/achievements/${achievementCode}`);
    redirect(`/${locale}/auth/login?callbackUrl=${callbackUrl}`);
  }

  const data = await backendFetch<AchievementDetailResponse>(`/me/achievements/${achievementCode}`);

  return (
    <div className="container mx-auto min-h-screen max-w-3xl px-4 pb-28 pt-24">
      <Link
        href={`/${locale}/me/achievements`}
        className="mb-4 inline-flex items-center gap-2 rounded-lg border border-white/10 px-3 py-2 text-xs text-zinc-400 hover:border-primary/40 hover:text-primary"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        {t("back")}
      </Link>

      <section className="rounded-2xl border border-white/10 bg-zinc-900/70 p-6">
        <div className="mb-4 flex items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-semibold text-zinc-100">{data.displayName}</h1>
            <p className="mt-2 text-sm text-zinc-400">{data.description}</p>
          </div>
          <div className="rounded-lg border border-white/10 bg-zinc-950/70 p-2 text-zinc-300">
            {data.isUnlocked ? <Medal className="h-5 w-5 text-primary" /> : <Lock className="h-5 w-5" />}
          </div>
        </div>

        <div className="grid gap-3 sm:grid-cols-3">
          <article className="rounded-lg border border-white/10 bg-zinc-950/70 p-3">
            <p className="text-xs text-zinc-400">{t("tier")}</p>
            <p className="mt-1 font-mono text-zinc-100">{data.tier}</p>
          </article>
          <article className="rounded-lg border border-white/10 bg-zinc-950/70 p-3">
            <p className="text-xs text-zinc-400 inline-flex items-center gap-1">
              <Coins className="h-3.5 w-3.5" /> {t("reward")}
            </p>
            <p className="mt-1 font-mono text-zinc-100">{data.rewardBug}</p>
          </article>
          <article className="rounded-lg border border-white/10 bg-zinc-950/70 p-3">
            <p className="text-xs text-zinc-400 inline-flex items-center gap-1">
              <Users className="h-3.5 w-3.5" /> {t("unlock_ratio")}
            </p>
            <p className="mt-1 font-mono text-zinc-100">{data.totalUnlockedPercentage}%</p>
          </article>
        </div>

        <div className="mt-4 rounded-lg border border-white/10 bg-zinc-950/70 p-3 text-sm text-zinc-400">
          <p>{t("status", { status: data.isUnlocked ? t("status_unlocked") : t("status_locked") })}</p>
          <p className="mt-1">{t("unlocked_at", { value: data.unlockedAt ?? "-" })}</p>
          <p className="mt-1">{t("total_unlocked_users", { count: data.totalUnlockedCount })}</p>
        </div>
      </section>
    </div>
  );
}
