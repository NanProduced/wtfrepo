"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { Medal, Lock, Trophy, Coins } from "lucide-react";
import { getMyAchievements, getMyAchievementsSummary } from "@/shared/api/achievements";
import { AchievementItem, AchievementsSummaryResponse } from "@/shared/types/achievements";
import { Button } from "@/shared/components/ui/button";
import { useLocale, useTranslations } from "next-intl";

type AchievementFilter = "ALL" | "UNLOCKED" | "LOCKED";

function formatTierLabel(tier: string) {
  return tier.replaceAll("_", " ").toLowerCase();
}

export function AchievementsOverview() {
  const locale = useLocale();
  const t = useTranslations("achievements.overview");
  const [items, setItems] = useState<AchievementItem[]>([]);
  const [summary, setSummary] = useState<AchievementsSummaryResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [filter, setFilter] = useState<AchievementFilter>("ALL");

  const loadData = useCallback(async () => {
    setIsLoading(true);
    try {
      const [summaryData, listData] = await Promise.all([
        getMyAchievementsSummary(),
        getMyAchievements({
          status: filter === "ALL" ? undefined : filter,
          limit: 100,
        }),
      ]);
      setSummary(summaryData);
      setItems(listData.items);
    } finally {
      setIsLoading(false);
    }
  }, [filter]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const unlocked = useMemo(() => items.filter((item) => item.isUnlocked).length, [items]);

  return (
    <div className="space-y-6">
      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <article className="rounded-xl border border-white/10 bg-zinc-900/70 p-4">
          <p className="text-xs text-zinc-400">{t("summary.total_achievements")}</p>
          <p className="mt-1 font-mono text-2xl text-zinc-100">{summary?.totalAchievements ?? "-"}</p>
        </article>
        <article className="rounded-xl border border-white/10 bg-zinc-900/70 p-4">
          <p className="text-xs text-zinc-400">{t("summary.unlocked")}</p>
          <p className="mt-1 inline-flex items-center gap-2 font-mono text-2xl text-primary">
            <Trophy className="h-4 w-4" />
            {summary?.unlockedCount ?? unlocked}
          </p>
        </article>
        <article className="rounded-xl border border-white/10 bg-zinc-900/70 p-4">
          <p className="text-xs text-zinc-400">{t("summary.completion")}</p>
          <p className="mt-1 font-mono text-2xl text-zinc-100">{summary?.unlockedPercentage ?? 0}%</p>
        </article>
        <article className="rounded-xl border border-white/10 bg-zinc-900/70 p-4">
          <p className="text-xs text-zinc-400">{t("summary.reward_bug_earned")}</p>
          <p className="mt-1 inline-flex items-center gap-2 font-mono text-2xl text-amber-300">
            <Coins className="h-4 w-4" />
            {summary?.totalBugEarned ?? 0}
          </p>
        </article>
      </section>

      <section className="flex flex-wrap items-center justify-between gap-3">
        <div className="inline-flex items-center gap-2">
          {(["ALL", "UNLOCKED", "LOCKED"] as const).map((candidate) => (
            <Button
              key={candidate}
              size="sm"
              variant={filter === candidate ? "default" : "outline"}
              onClick={() => setFilter(candidate)}
              className="text-xs"
            >
              {candidate === "ALL"
                ? t("filter.all")
                : candidate === "UNLOCKED"
                  ? t("filter.unlocked")
                  : t("filter.locked")}
            </Button>
          ))}
        </div>
        <p className="text-xs text-zinc-500">{t("config_note")}</p>
      </section>

      {isLoading ? (
        <div className="rounded-xl border border-white/10 bg-zinc-900/70 p-5 text-sm text-zinc-400">
          {t("loading")}
        </div>
      ) : items.length === 0 ? (
        <div className="rounded-xl border border-dashed border-white/10 bg-zinc-900/70 p-5 text-sm text-zinc-500">
          {t("empty")}
        </div>
      ) : (
        <section className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {items.map((item) => (
            <Link
              key={item.achievementCode}
              href={`/${locale}/me/achievements/${item.achievementCode}`}
              className="rounded-xl border border-white/10 bg-zinc-900/70 p-4 transition-colors hover:border-primary/40"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="font-medium text-zinc-100">{item.displayName}</p>
                  <p className="mt-1 text-xs text-zinc-400">{item.description}</p>
                </div>
                <div className="rounded-md border border-white/10 bg-zinc-950/70 p-1 text-zinc-300">
                  {item.isUnlocked ? <Medal className="h-4 w-4 text-primary" /> : <Lock className="h-4 w-4" />}
                </div>
              </div>
              <div className="mt-3 flex items-center justify-between text-xs text-zinc-500">
                <span>{formatTierLabel(item.tier)}</span>
                <span>{item.isUnlocked ? t("status.unlocked") : t("status.locked")}</span>
              </div>
            </Link>
          ))}
        </section>
      )}
    </div>
  );
}
