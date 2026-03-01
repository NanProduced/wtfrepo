"use client";

import { useState, useEffect, useCallback, useMemo } from "react";
import { ArchiveSpecimen } from "@/shared/types/specimen";
import { getArchiveSpecimens } from "@/shared/api/specimen";
import { ArchiveCard } from "./archive-card";
import { Button } from "@/shared/components/ui/button";
import { Loader2, Search, SlidersHorizontal } from "lucide-react";
import { toast } from "sonner";
import { useTranslations } from "next-intl";

interface ErrorLike {
  message?: string;
}

/**
 * ArchiveList
 * Handles fetching, filtering, and infinite scroll for Specimen Archive.
 */
export function ArchiveList() {
  const t = useTranslations("archive.list");
  const [items, setItems] = useState<ArchiveSpecimen[]>([]);
  const [cursor, setCursor] = useState<string | undefined>(undefined);
  const [hasMore, setHasMore] = useState(true);
  const [isLoading, setIsLoading] = useState(false);
  const [sort, setSort] = useState<"HOT" | "NEW" | "INSANE">("HOT");
  const [query, setQuery] = useState("");

  const spotlight = useMemo(() => {
    if (items.length === 0) {
      return {
        byElo: null,
        byHype: null,
        byMomentum: null,
      };
    }

    const byElo = [...items].sort((a, b) => b.metrics.elo - a.metrics.elo)[0];
    const byHype = [...items].sort((a, b) => b.metrics.hype - a.metrics.hype)[0];
    const byMomentum = [...items].sort(
      (a, b) => (b.metrics.delta24h ?? Number.MIN_SAFE_INTEGER) - (a.metrics.delta24h ?? Number.MIN_SAFE_INTEGER)
    )[0];

    return { byElo, byHype, byMomentum };
  }, [items]);

  const fetchItems = useCallback(async (isInitial = false) => {
    if (isLoading || (!hasMore && !isInitial)) return;

    setIsLoading(true);
    try {
      const result = await getArchiveSpecimens({
        cursor: isInitial ? undefined : cursor,
        sort,
        q: query || undefined,
        limit: 20,
      });

      setItems((prev) => isInitial ? result.items : [...prev, ...result.items]);
      setCursor(result.nextCursor);
      setHasMore(result.hasMore);
    } catch (error: unknown) {
      console.error("Failed to fetch archive:", error);
      const maybeError = error as ErrorLike;
      toast.error(t("toast.communication_error"), {
        description: maybeError?.message || t("toast.mainframe_unreachable"),
      });
    } finally {
      setIsLoading(false);
    }
  }, [cursor, hasMore, isLoading, query, sort, t]);

  // Initial Fetch
  useEffect(() => {
    void fetchItems(true);
    // fetchItems depends on cursor/hasMore for pagination; initial load should only react to sort.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sort]);

  // Debounced search could be added here

  return (
    <div className="space-y-8">
      {/* Controls: Search & Sort */}
      <div className="flex flex-col md:flex-row gap-4 items-center justify-between sticky top-20 z-30 bg-background/80 backdrop-blur-md py-4 border-b border-white/5">
        <div className="relative w-full md:w-96">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-500" />
          <input
            type="text"
            placeholder={t("search_placeholder")}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && fetchItems(true)}
            className="w-full h-11 bg-black/40 border border-white/10 rounded-xl pl-10 pr-4 font-mono text-sm focus:outline-none focus:border-primary/50 transition-colors"
          />
        </div>

        <div className="flex items-center gap-2">
          {(["HOT", "NEW", "INSANE"] as const).map((s) => (
            <Button
              key={s}
              variant={sort === s ? "default" : "outline"}
              size="sm"
              onClick={() => setSort(s)}
            className="font-mono text-[10px] h-8 px-4"
            >
              {s === "HOT" ? t("sort.hot") : s === "NEW" ? t("sort.new") : t("sort.insane")}
            </Button>
          ))}
          <Button variant="outline" size="icon" className="h-8 w-8 border-white/10">
            <SlidersHorizontal className="w-4 h-4 text-zinc-400" />
          </Button>
        </div>
      </div>

      {/* Grid */}
      <section className="grid gap-3 md:grid-cols-3">
        <div className="rounded-xl border border-white/10 bg-zinc-900/70 p-4">
          <p className="text-xs text-zinc-500">{t("spotlight.by_elo")}</p>
          <p className="mt-2 text-sm text-zinc-200">
            {spotlight.byElo ? spotlight.byElo.repoFullName : t("spotlight.waiting")}
          </p>
          <p className="mt-1 font-mono text-primary">{spotlight.byElo ? spotlight.byElo.metrics.elo : "-"}</p>
        </div>
        <div className="rounded-xl border border-white/10 bg-zinc-900/70 p-4">
          <p className="text-xs text-zinc-500">{t("spotlight.by_hype")}</p>
          <p className="mt-2 text-sm text-zinc-200">
            {spotlight.byHype ? spotlight.byHype.repoFullName : t("spotlight.waiting")}
          </p>
          <p className="mt-1 font-mono text-orange-300">
            {spotlight.byHype ? `${spotlight.byHype.metrics.hype.toFixed(1)}%` : "-"}
          </p>
        </div>
        <div className="rounded-xl border border-white/10 bg-zinc-900/70 p-4">
          <p className="text-xs text-zinc-500">{t("spotlight.momentum_24h")}</p>
          <p className="mt-2 text-sm text-zinc-200">
            {spotlight.byMomentum ? spotlight.byMomentum.repoFullName : t("spotlight.waiting")}
          </p>
          <p className="mt-1 font-mono text-emerald-300">
            {spotlight.byMomentum?.metrics.delta24h !== undefined
              ? `${spotlight.byMomentum.metrics.delta24h > 0 ? "+" : ""}${spotlight.byMomentum.metrics.delta24h}`
              : "-"}
          </p>
        </div>
      </section>

      {items.length === 0 && !isLoading ? (
        <div className="py-20 text-center border border-dashed border-white/10 rounded-3xl">
          <p className="font-mono text-zinc-500">{t("empty")}</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          {items.map((item) => (
            <ArchiveCard key={item.specimenId} specimen={item} />
          ))}

          {/* Loading Skeletons */}
          {isLoading && Array.from({ length: 4 }).map((_, i) => (
            <div key={`skeleton-${i}`} className="h-64 bg-zinc-900/40 rounded-2xl animate-pulse border border-white/5" />
          ))}
        </div>
      )}

      {/* Load More Trigger */}
      {hasMore && (
        <div className="flex justify-center pt-8">
          <Button
            variant="ghost"
            onClick={() => fetchItems()}
            disabled={isLoading}
            className="font-mono text-zinc-500 hover:text-primary"
          >
            {isLoading ? <Loader2 className="w-5 h-5 animate-spin mr-2" /> : t("load_more")}
          </Button>
        </div>
      )}
    </div>
  );
}
