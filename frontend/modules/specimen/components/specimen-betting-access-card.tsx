"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ArrowRight, Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import { getSpecimenBetSummary } from "@/shared/api/betting";
import type { BetBlockReasonCode, BetSummaryView } from "@/shared/types/betting";

interface SpecimenBettingAccessCardProps {
  specimenId: string;
  locale: "zh" | "en";
}

export function SpecimenBettingAccessCard({ specimenId, locale }: SpecimenBettingAccessCardProps) {
  const t = useTranslations("specimen.detail");
  const [summary, setSummary] = useState<BetSummaryView | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isMounted = true;
    getSpecimenBetSummary(specimenId)
      .then((data) => {
        if (isMounted) {
          setSummary(data);
        }
      })
      .catch(() => {
        if (isMounted) {
          setSummary(null);
        }
      })
      .finally(() => {
        if (isMounted) {
          setIsLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [specimenId]);

  const resolveBlockReason = (reasonCode: BetBlockReasonCode | null | undefined) => {
    if (!reasonCode) return t("betting.gate.blocked");
    if (reasonCode === "AUTH_REQUIRED") return t("betting.gate.auth_required");
    if (reasonCode === "VOTE_REQUIRED") return t("betting.gate.vote_required");
    if (reasonCode === "IPO_LOCKED") return t("betting.gate.ipo_locked");
    if (reasonCode === "POOL_NOT_AVAILABLE") return t("betting.gate.pool_unavailable");
    if (reasonCode === "POOL_NOT_OPEN") return t("betting.gate.pool_closed");
    if (reasonCode === "CUTOFF_PASSED") return t("betting.gate.cutoff_passed");
    return t("betting.gate.blocked");
  };

  if (isLoading) {
    return (
      <div className="rounded-xl border border-dashed border-white/15 bg-zinc-950/70 p-4 text-sm text-zinc-500">
        <span className="inline-flex items-center gap-2">
          <Loader2 className="h-4 w-4 animate-spin" />
          {t("bet_access.loading")}
        </span>
      </div>
    );
  }

  if (!summary) {
    return (
      <div className="rounded-xl border border-dashed border-white/15 bg-zinc-950/70 p-4 text-sm text-zinc-500">
        {t("bet_access.empty")}
      </div>
    );
  }

  return (
    <div className="space-y-3 rounded-xl border border-white/10 bg-zinc-950/80 p-4">
      <div
        className={cn(
          "rounded-lg border px-3 py-2 text-xs",
          summary.canBet
            ? "border-emerald-300/35 bg-emerald-500/10 text-emerald-200"
            : "border-amber-300/35 bg-amber-400/10 text-amber-200"
        )}
      >
        <p className="font-medium">
          {summary.canBet ? t("bet_access.eligible_title") : t("bet_access.blocked_title")}
        </p>
        <p className="mt-1">
          {summary.canBet
            ? t("bet_access.eligible_desc")
            : resolveBlockReason(summary.betBlockReasonCode)}
        </p>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-[11px] text-zinc-500">{t("bet_access.note")}</p>
        <Link
          href={`/${locale}/arena`}
          className="inline-flex h-9 items-center rounded-md border border-white/15 bg-zinc-900 px-3 text-xs text-zinc-100 transition-colors hover:border-primary/40 hover:text-primary"
        >
          {t("bet_access.go_arena")}
          <ArrowRight className="ml-1.5 h-3.5 w-3.5" />
        </Link>
      </div>
    </div>
  );
}
