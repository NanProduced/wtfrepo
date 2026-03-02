"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { ArrowUpRight, Loader2, ScrollText } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/shared/components/ui/button";
import {
  getActiveBets,
  getBetHistory,
  getSettlementToday,
} from "@/shared/api/betting";
import type {
  BetDirection,
  BetHistoryOrder,
  BetOrderStatus,
  BetActiveView,
  SettlementOutcome,
  SettlementTodayItem,
  SettlementTodayPage,
} from "@/shared/types/betting";

interface BettingOverviewPanelProps {
  locale: string;
}

function toLocaleTag(locale: string) {
  return locale.startsWith("zh") ? "zh-CN" : "en-US";
}

function formatNumber(value: number, locale: string) {
  return new Intl.NumberFormat(toLocaleTag(locale)).format(value);
}

function formatDateTime(value: string | null, locale: string) {
  if (!value) {
    return "-";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return "-";
  }
  return new Intl.DateTimeFormat(toLocaleTag(locale), {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(parsed);
}

export function BettingOverviewPanel({ locale }: BettingOverviewPanelProps) {
  const t = useTranslations("me.betting");
  const [activeView, setActiveView] = useState<BetActiveView | null>(null);
  const [historyItems, setHistoryItems] = useState<BetHistoryOrder[]>([]);
  const [historyCursor, setHistoryCursor] = useState<string | null>(null);
  const [historyHasMore, setHistoryHasMore] = useState(false);
  const [settlementItems, setSettlementItems] = useState<SettlementTodayItem[]>([]);
  const [settlementCursor, setSettlementCursor] = useState<string | null>(null);
  const [settlementHasMore, setSettlementHasMore] = useState(false);
  const [settlementDate, setSettlementDate] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingMoreHistory, setIsLoadingMoreHistory] = useState(false);
  const [isLoadingMoreSettlement, setIsLoadingMoreSettlement] = useState(false);

  const directionLabel = useCallback(
    (direction: BetDirection) => {
      if (direction === "UP") return t("direction.up");
      if (direction === "FLAT") return t("direction.flat");
      return t("direction.down");
    },
    [t]
  );

  const statusLabel = useCallback(
    (status: BetOrderStatus) => {
      if (status === "PENDING") return t("status.pending");
      if (status === "WON") return t("status.won");
      if (status === "LOST") return t("status.lost");
      return t("status.cancelled");
    },
    [t]
  );

  const settlementOutcomeLabel = useCallback(
    (outcome: SettlementOutcome) => {
      if (outcome === "UP") return t("direction.up");
      if (outcome === "FLAT") return t("direction.flat");
      if (outcome === "DOWN") return t("direction.down");
      if (outcome === "FORCE_SETTLED") return t("settlement.outcome_force_settled");
      return t("settlement.outcome_unknown");
    },
    [t]
  );

  const loadInitial = useCallback(async () => {
    setIsLoading(true);
    try {
      const [active, history, settlement] = await Promise.all([
        getActiveBets(),
        getBetHistory({ limit: 20 }),
        getSettlementToday({ limit: 20 }),
      ]);

      setActiveView(active);
      setHistoryItems(history.orders);
      setHistoryCursor(history.nextCursor);
      setHistoryHasMore(history.hasMore);
      setSettlementDate(settlement.date);
      setSettlementItems(settlement.settlements);
      setSettlementCursor(settlement.nextCursor);
      setSettlementHasMore(settlement.hasMore);
    } catch (error) {
      console.error("Failed to load betting overview:", error);
      toast.error(t("toast.load_failed"));
    } finally {
      setIsLoading(false);
    }
  }, [t]);

  useEffect(() => {
    void loadInitial();
  }, [loadInitial]);

  const loadMoreHistory = async () => {
    if (!historyHasMore || !historyCursor || isLoadingMoreHistory) {
      return;
    }
    setIsLoadingMoreHistory(true);
    try {
      const page = await getBetHistory({ cursor: historyCursor, limit: 20 });
      setHistoryItems((current) => [...current, ...page.orders]);
      setHistoryCursor(page.nextCursor);
      setHistoryHasMore(page.hasMore);
    } catch (error) {
      console.error("Failed to load more history:", error);
      toast.error(t("toast.history_more_failed"));
    } finally {
      setIsLoadingMoreHistory(false);
    }
  };

  const loadMoreSettlement = async () => {
    if (!settlementHasMore || !settlementCursor || isLoadingMoreSettlement) {
      return;
    }
    setIsLoadingMoreSettlement(true);
    try {
      const page: SettlementTodayPage = await getSettlementToday({
        cursor: settlementCursor,
        limit: 20,
      });
      setSettlementItems((current) => [...current, ...page.settlements]);
      setSettlementCursor(page.nextCursor);
      setSettlementHasMore(page.hasMore);
    } catch (error) {
      console.error("Failed to load more settlement:", error);
      toast.error(t("toast.settlement_more_failed"));
    } finally {
      setIsLoadingMoreSettlement(false);
    }
  };

  const wonCount = useMemo(
    () => historyItems.filter((item) => item.status === "WON").length,
    [historyItems]
  );

  return (
    <div className="space-y-6">
      <section className="grid gap-3 md:grid-cols-3">
        <article className="rounded-xl border border-white/10 bg-zinc-900/70 p-4">
          <p className="text-xs text-zinc-500">{t("cards.active_orders")}</p>
          <p className="mt-1 text-2xl font-semibold text-zinc-100">
            {isLoading ? "-" : activeView?.orders.length ?? 0}
          </p>
        </article>
        <article className="rounded-xl border border-white/10 bg-zinc-900/70 p-4">
          <p className="text-xs text-zinc-500">{t("cards.total_staked")}</p>
          <p className="mt-1 text-2xl font-semibold text-zinc-100">
            {isLoading ? "-" : formatNumber(activeView?.totalStaked ?? 0, locale)}
          </p>
        </article>
        <article className="rounded-xl border border-white/10 bg-zinc-900/70 p-4">
          <p className="text-xs text-zinc-500">{t("cards.won_orders")}</p>
          <p className="mt-1 text-2xl font-semibold text-zinc-100">{isLoading ? "-" : wonCount}</p>
        </article>
      </section>

      <section className="rounded-2xl border border-white/10 bg-zinc-900/70 p-5">
        <div className="mb-3 flex items-center justify-between gap-2">
          <h2 className="text-base font-semibold text-zinc-100">{t("active.title")}</h2>
          <p className="text-xs text-zinc-500">{t("active.trading_day", { date: activeView?.date || "-" })}</p>
        </div>
        {isLoading ? (
          <p className="text-sm text-zinc-500">{t("loading")}</p>
        ) : !activeView || activeView.orders.length === 0 ? (
          <p className="rounded-lg border border-dashed border-white/10 bg-zinc-950/70 p-4 text-sm text-zinc-500">
            {t("active.empty")}
          </p>
        ) : (
          <ul className="space-y-2">
            {activeView.orders.map((order) => (
              <li key={order.orderId}>
                <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-white/10 bg-zinc-950/70 px-3 py-2 text-sm">
                  <div className="min-w-0">
                    <p className="truncate text-zinc-100">{order.specimenTitle}</p>
                    <p className="mt-1 text-xs text-zinc-500">
                      {directionLabel(order.direction)} · {t("active.amount", { value: order.amount })} ·{" "}
                      {t("active.current_odds", { value: order.currentOdds.toFixed(2) })}
                    </p>
                  </div>
                  <Link
                    href={`/${locale}/specimen/${order.specimenId}`}
                    className="inline-flex items-center gap-1 text-xs text-primary hover:underline"
                  >
                    {t("open_specimen")}
                    <ArrowUpRight className="h-3.5 w-3.5" />
                  </Link>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="rounded-2xl border border-white/10 bg-zinc-900/70 p-5">
        <div className="mb-3 flex items-center justify-between gap-2">
          <h2 className="text-base font-semibold text-zinc-100">{t("history.title")}</h2>
          <p className="text-xs text-zinc-500">{t("history.count", { value: historyItems.length })}</p>
        </div>
        {isLoading ? (
          <p className="text-sm text-zinc-500">{t("loading")}</p>
        ) : historyItems.length === 0 ? (
          <p className="rounded-lg border border-dashed border-white/10 bg-zinc-950/70 p-4 text-sm text-zinc-500">
            {t("history.empty")}
          </p>
        ) : (
          <ul className="space-y-2">
            {historyItems.map((order) => (
              <li key={order.orderId}>
                <div className="rounded-lg border border-white/10 bg-zinc-950/70 px-3 py-2">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="text-sm text-zinc-100">{order.specimenTitle}</p>
                    <span className="text-xs text-zinc-400">{statusLabel(order.status)}</span>
                  </div>
                  <p className="mt-1 text-xs text-zinc-500">
                    {directionLabel(order.direction)} · {t("history.amount", { value: order.amount })} ·{" "}
                    {t("history.payout", {
                      value: typeof order.payout === "number" ? formatNumber(order.payout, locale) : "-",
                    })}
                  </p>
                  <p className="mt-1 text-xs text-zinc-500">
                    {t("history.settled_at", { value: formatDateTime(order.settledAt, locale) })}
                  </p>
                </div>
              </li>
            ))}
          </ul>
        )}
        {historyHasMore && (
          <div className="mt-3 flex justify-center">
            <Button
              type="button"
              variant="outline"
              disabled={isLoadingMoreHistory}
              className="border-white/15 bg-zinc-950/70 text-xs text-zinc-300 hover:bg-zinc-900"
              onClick={() => void loadMoreHistory()}
            >
              {isLoadingMoreHistory ? (
                <span className="inline-flex items-center gap-1.5">
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  {t("history.loading_more")}
                </span>
              ) : (
                t("history.load_more")
              )}
            </Button>
          </div>
        )}
      </section>

      <section className="rounded-2xl border border-white/10 bg-zinc-900/70 p-5">
        <div className="mb-3 flex items-center justify-between gap-2">
          <h2 className="text-base font-semibold text-zinc-100">{t("settlement.title")}</h2>
          <p className="text-xs text-zinc-500">{t("settlement.date", { value: settlementDate || "-" })}</p>
        </div>
        {isLoading ? (
          <p className="text-sm text-zinc-500">{t("loading")}</p>
        ) : settlementItems.length === 0 ? (
          <p className="rounded-lg border border-dashed border-white/10 bg-zinc-950/70 p-4 text-sm text-zinc-500">
            {t("settlement.empty")}
          </p>
        ) : (
          <ul className="space-y-2">
            {settlementItems.map((item) => (
              <li key={`${item.specimenId}-${item.outcome}`}>
                <div className="rounded-lg border border-white/10 bg-zinc-950/70 px-3 py-2">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="text-sm text-zinc-100">{item.specimenTitle}</p>
                    <p className="text-xs text-zinc-500">
                      {t("settlement.delta_r", { value: item.deltaR })}
                    </p>
                  </div>
                  <p className="mt-1 text-xs text-zinc-500">
                    {t("settlement.outcome", { value: settlementOutcomeLabel(item.outcome) })} ·{" "}
                    {t("settlement.orders", { value: item.myOrders.length })}
                  </p>
                </div>
              </li>
            ))}
          </ul>
        )}
        {settlementHasMore && (
          <div className="mt-3 flex justify-center">
            <Button
              type="button"
              variant="outline"
              disabled={isLoadingMoreSettlement}
              className="border-white/15 bg-zinc-950/70 text-xs text-zinc-300 hover:bg-zinc-900"
              onClick={() => void loadMoreSettlement()}
            >
              {isLoadingMoreSettlement ? (
                <span className="inline-flex items-center gap-1.5">
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  {t("settlement.loading_more")}
                </span>
              ) : (
                t("settlement.load_more")
              )}
            </Button>
          </div>
        )}
      </section>

      <section className="rounded-xl border border-dashed border-white/10 bg-zinc-900/60 p-4">
        <p className="inline-flex items-center gap-2 text-xs text-zinc-500">
          <ScrollText className="h-3.5 w-3.5" />
          {t("note")}
        </p>
      </section>
    </div>
  );
}
