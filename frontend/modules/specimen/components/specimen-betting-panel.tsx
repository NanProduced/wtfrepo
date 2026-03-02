"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Coins, Loader2, TrendingDown, TrendingUp } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { cn } from "@/lib/utils";
import {
  getActiveBets,
  getSpecimenBetSummary,
  placeBet,
} from "@/shared/api/betting";
import { useWalletStore } from "@/shared/store/wallet";
import type {
  BetActiveOrder,
  BetBlockReasonCode,
  BetDirection,
  BetPlaceResult,
  BetSummaryView,
} from "@/shared/types/betting";

interface SpecimenBettingPanelProps {
  specimenId: string;
  locale: "zh" | "en";
}

const DIRECTION_ORDER: BetDirection[] = ["UP", "FLAT", "DOWN"];

function formatNumber(value: number, locale: "zh" | "en") {
  return new Intl.NumberFormat(locale === "zh" ? "zh-CN" : "en-US").format(value);
}

function formatOdds(value: number | null | undefined, locale: "zh" | "en") {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return "-";
  }
  return new Intl.NumberFormat(locale === "zh" ? "zh-CN" : "en-US", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}

function formatDateTimeLabel(value: string | null | undefined, locale: "zh" | "en") {
  if (!value) {
    return "-";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return "-";
  }
  return new Intl.DateTimeFormat(locale === "zh" ? "zh-CN" : "en-US", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(parsed);
}

function extractMessage(error: unknown, fallback: string) {
  if (typeof error === "object" && error !== null) {
    if ("message" in error && typeof error.message === "string") {
      return error.message;
    }
    if ("error" in error && typeof error.error === "string") {
      return error.error;
    }
  }
  return fallback;
}

export function SpecimenBettingPanel({ specimenId, locale }: SpecimenBettingPanelProps) {
  const t = useTranslations("specimen.detail.betting");
  const refreshWallet = useWalletStore((state) => state.fetchWallet);

  const [summary, setSummary] = useState<BetSummaryView | null>(null);
  const [activeOrders, setActiveOrders] = useState<BetActiveOrder[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isPlacing, setIsPlacing] = useState(false);
  const [selectedDirection, setSelectedDirection] = useState<BetDirection>("UP");
  const [amountInput, setAmountInput] = useState("100");
  const [lastPlacedOrder, setLastPlacedOrder] = useState<BetPlaceResult | null>(null);

  const loadSummary = useCallback(async () => {
    const data = await getSpecimenBetSummary(specimenId);
    setSummary(data);
  }, [specimenId]);

  const loadActiveOrders = useCallback(async () => {
    try {
      const data = await getActiveBets();
      setActiveOrders(data.orders.filter((order) => order.specimenId === specimenId));
    } catch {
      setActiveOrders([]);
    }
  }, [specimenId]);

  const loadPanel = useCallback(async () => {
    setIsLoading(true);
    try {
      await Promise.all([loadSummary(), loadActiveOrders()]);
    } catch (error) {
      console.error("Failed to load betting panel:", error);
      toast.error(extractMessage(error, t("toast.load_failed")));
    } finally {
      setIsLoading(false);
    }
  }, [loadActiveOrders, loadSummary, t]);

  useEffect(() => {
    void loadPanel();
  }, [loadPanel]);

  const amount = useMemo(() => Number.parseInt(amountInput, 10), [amountInput]);
  const isAmountValid = Number.isInteger(amount) && amount >= 100;
  const canPlaceBet = !!summary && summary.canBet && isAmountValid && !isPlacing;

  const oddsByDirection = {
    UP: summary?.oddsUp ?? null,
    FLAT: summary?.oddsFlat ?? null,
    DOWN: summary?.oddsDown ?? null,
  } as const;

  const poolByDirection = {
    UP: summary?.poolUp ?? 0,
    FLAT: summary?.poolFlat ?? 0,
    DOWN: summary?.poolDown ?? 0,
  } as const;

  const statusLabel = summary
    ? summary.poolStatus === null
      ? t("status.unavailable")
      : summary.poolStatus === "OPEN"
      ? t("status.open")
      : summary.poolStatus === "CLOSED"
        ? t("status.closed")
        : t("status.settled")
    : "-";

  const resolveBlockReason = (reasonCode: BetBlockReasonCode | null | undefined) => {
    if (!reasonCode) return t("gate.blocked");
    if (reasonCode === "AUTH_REQUIRED") return t("gate.auth_required");
    if (reasonCode === "VOTE_REQUIRED") return t("gate.vote_required");
    if (reasonCode === "IPO_LOCKED") return t("gate.ipo_locked");
    if (reasonCode === "POOL_NOT_AVAILABLE") return t("gate.pool_unavailable");
    if (reasonCode === "POOL_NOT_OPEN") return t("gate.pool_closed");
    if (reasonCode === "CUTOFF_PASSED") return t("gate.cutoff_passed");
    return t("gate.blocked");
  };

  const directionLabel = (direction: BetDirection) => {
    if (direction === "UP") return t("direction.up");
    if (direction === "FLAT") return t("direction.flat");
    return t("direction.down");
  };

  const handlePlaceBet = async () => {
    if (!summary || !isAmountValid || amount < 100) {
      toast.error(t("toast.amount_invalid"));
      return;
    }
    if (!summary.canBet) {
      toast.error(resolveBlockReason(summary.betBlockReasonCode));
      return;
    }

    setIsPlacing(true);
    try {
      const result = await placeBet({
        specimenId,
        direction: selectedDirection,
        amount,
      });
      setLastPlacedOrder(result);
      toast.success(t("toast.place_success", { amount: result.amount }));
      await Promise.all([loadSummary(), loadActiveOrders(), refreshWallet()]);
    } catch (error) {
      console.error("Failed to place bet:", error);
      toast.error(extractMessage(error, t("toast.place_failed")));
    } finally {
      setIsPlacing(false);
    }
  };

  return (
    <div className="space-y-4">
      {isLoading ? (
        <div className="rounded-xl border border-dashed border-white/15 bg-zinc-950/70 p-4 text-sm text-zinc-500">
          {t("loading")}
        </div>
      ) : !summary ? (
        <div className="rounded-xl border border-dashed border-white/15 bg-zinc-950/70 p-4 text-sm text-zinc-500">
          {t("empty")}
        </div>
      ) : (
        <>
          <div className="rounded-xl border border-white/10 bg-zinc-950/80 p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="space-y-1">
                <p className="text-xs text-zinc-500">{t("trading_day", { date: summary.date })}</p>
                <p className="text-sm text-zinc-300">
                  {summary.betCutoffAt
                    ? t("cutoff", { time: formatDateTimeLabel(summary.betCutoffAt, locale) })
                    : t("cutoff_unavailable")}
                </p>
              </div>
              <span
                className={cn(
                  "rounded-full border px-2.5 py-1 text-[11px]",
                  summary.poolStatus === "OPEN"
                    ? "border-emerald-300/40 bg-emerald-500/10 text-emerald-300"
                    : summary.poolStatus === "CLOSED"
                      ? "border-amber-300/40 bg-amber-400/10 text-amber-300"
                      : "border-zinc-300/30 bg-zinc-500/10 text-zinc-300"
                )}
              >
                {statusLabel}
              </span>
            </div>

            {!summary.canBet && (
              <div className="mt-3 rounded-lg border border-amber-300/35 bg-amber-400/10 px-3 py-2 text-xs text-amber-200">
                {resolveBlockReason(summary.betBlockReasonCode)}
              </div>
            )}

            <div className="mt-4 grid gap-2 md:grid-cols-3">
              {DIRECTION_ORDER.map((direction) => {
                const isSelected = selectedDirection === direction;
                return (
                  <button
                    key={direction}
                    type="button"
                    onClick={() => setSelectedDirection(direction)}
                    className={cn(
                      "rounded-lg border px-3 py-3 text-left transition-colors",
                      isSelected
                        ? "border-primary/50 bg-primary/10"
                        : "border-white/10 bg-zinc-900/70 hover:border-primary/30"
                    )}
                  >
                    <p className="mb-1 text-sm font-semibold text-zinc-100">{directionLabel(direction)}</p>
                    <p className="text-xs text-zinc-500">
                      {t("odds", { value: formatOdds(oddsByDirection[direction], locale) })}
                    </p>
                    <p className="mt-1 text-xs text-zinc-500">
                      {t("pool", { value: formatNumber(poolByDirection[direction], locale) })}
                    </p>
                  </button>
                );
              })}
            </div>

            <div className="mt-4 flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
              <label className="block md:max-w-xs">
                <span className="mb-1 block text-xs text-zinc-500">{t("amount_label")}</span>
                <input
                  inputMode="numeric"
                  pattern="[0-9]*"
                  value={amountInput}
                  onChange={(event) => setAmountInput(event.target.value.replace(/[^\d]/g, ""))}
                  className="h-9 w-full rounded-md border border-white/10 bg-zinc-900 px-3 text-sm text-zinc-100 outline-none transition-colors focus:border-primary/50"
                  placeholder="100"
                />
                <span className="mt-1 block text-[11px] text-zinc-500">{t("amount_min_hint")}</span>
              </label>

              <button
                type="button"
                onClick={() => void handlePlaceBet()}
                disabled={!canPlaceBet}
                className="inline-flex h-9 items-center justify-center rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {isPlacing ? (
                  <span className="inline-flex items-center gap-1.5">
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    {t("placing")}
                  </span>
                ) : (
                  t("place_button", { direction: directionLabel(selectedDirection) })
                )}
              </button>
            </div>

            <div className="mt-3 grid gap-2 text-xs text-zinc-500 md:grid-cols-2">
              <p>{t("total_bettors", { value: summary.totalBettors })}</p>
              <p>{t("rake_rate", { value: formatOdds(summary.rakeRate * 100, locale) })}</p>
              <p>{t("ipo_status", { value: summary.ipoStatus })}</p>
              <p>{t("delta_r", { value: summary.deltaRSoFar })}</p>
              <p>{t("current_elo", { value: summary.currentElo })}</p>
              <p>{t("moon_doom_threshold", { value: summary.moonDoomThreshold })}</p>
            </div>
          </div>

          {lastPlacedOrder && (
            <div className="rounded-xl border border-primary/25 bg-primary/5 p-4 text-sm text-zinc-300">
              <p className="font-medium text-zinc-100">{t("latest_order.title")}</p>
              <p className="mt-1 text-xs text-zinc-500">
                {t("latest_order.order_id", { orderId: lastPlacedOrder.orderId })}
              </p>
              <div className="mt-2 flex flex-wrap gap-3 text-xs">
                <span className="inline-flex items-center gap-1">
                  <Coins className="h-3.5 w-3.5 text-amber-300" />
                  {t("latest_order.amount", { value: lastPlacedOrder.amount })}
                </span>
                <span className="inline-flex items-center gap-1">
                  <TrendingUp className="h-3.5 w-3.5 text-emerald-300" />
                  {t("latest_order.odds", { value: formatOdds(lastPlacedOrder.oddsAtPlace, locale) })}
                </span>
              </div>
            </div>
          )}

          <div className="rounded-xl border border-white/10 bg-zinc-950/80 p-4">
            <p className="mb-3 text-sm font-semibold text-zinc-100">{t("active_orders.title")}</p>
            {activeOrders.length === 0 ? (
              <p className="text-sm text-zinc-500">{t("active_orders.empty")}</p>
            ) : (
              <ul className="space-y-2">
                {activeOrders.map((order) => (
                  <li
                    key={order.orderId}
                    className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-white/10 bg-zinc-900/70 px-3 py-2 text-xs"
                  >
                    <span className="text-zinc-300">
                      {directionLabel(order.direction)} · {t("active_orders.amount", { value: order.amount })}
                    </span>
                    <span className="text-zinc-500">
                      {t("active_orders.current_odds", { value: formatOdds(order.currentOdds, locale) })}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="rounded-lg border border-dashed border-white/10 bg-zinc-950/70 p-3 text-[11px] text-zinc-500">
            <p className="inline-flex items-center gap-1">
              <TrendingDown className="h-3.5 w-3.5" />
              {t("note")}
            </p>
          </div>
        </>
      )}
    </div>
  );
}
