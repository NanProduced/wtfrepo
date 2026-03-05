"use client";

import Link from "next/link";
import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Loader2, Search, SlidersHorizontal } from "lucide-react";
import { toast } from "sonner";
import { getArchiveInsights, getArchiveLeaderboard, getArchiveSpecimens, getTagConfig } from "@/shared/api/specimen";
import type {
  ArchiveInsightsData,
  ArchiveLeaderboardItem,
  ArchiveLeaderboardMetric,
  ArchiveSpecimen,
  TagConfigResponse,
  TagDefinition,
  TagDimension,
} from "@/shared/types/specimen";
import { Button } from "@/shared/components/ui/button";
import { cn } from "@/lib/utils";
import { ArchiveCard } from "./archive-card";

interface ErrorLike {
  message?: string;
}

const LEADERBOARD_PAGE_SIZE = 10;

interface LeaderboardState {
  items: ArchiveLeaderboardItem[];
  hasMore: boolean;
  nextCursor: string | null;
  isLoading: boolean;
}

const EMPTY_LEADERBOARD_STATE: Record<ArchiveLeaderboardMetric, LeaderboardState> = {
  ELO: {
    items: [],
    hasMore: false,
    nextCursor: null,
    isLoading: false,
  },
  HYPE: {
    items: [],
    hasMore: false,
    nextCursor: null,
    isLoading: false,
  },
};

/**
 * ArchiveList
 * Handles fetching, filtering, and infinite scroll for Specimen archive.
 */
export function ArchiveList() {
  const t = useTranslations("archive.list");
  const locale = useLocale();
  const [items, setItems] = useState<ArchiveSpecimen[]>([]);
  const [insights, setInsights] = useState<ArchiveInsightsData | null>(null);
  const [isInsightsLoading, setIsInsightsLoading] = useState(false);
  const [leaderboards, setLeaderboards] = useState<Record<ArchiveLeaderboardMetric, LeaderboardState>>(
    EMPTY_LEADERBOARD_STATE
  );
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(true);
  const [isLoading, setIsLoading] = useState(false);
  const [sort, setSort] = useState<"HOT" | "NEW" | "INSANE">("HOT");
  const [query, setQuery] = useState("");
  const [tagConfig, setTagConfig] = useState<TagConfigResponse | null>(null);
  const [isTagConfigLoading, setIsTagConfigLoading] = useState(false);
  const [isFilterPanelOpen, setIsFilterPanelOpen] = useState(false);
  const [selectedTagKeys, setSelectedTagKeys] = useState<string[]>([]);
  const requestSequenceRef = useRef(0);

  const selectedTagSet = useMemo(() => new Set(selectedTagKeys), [selectedTagKeys]);

  const tagLookup = useMemo(() => {
    const lookup: Record<string, { label: string; dimensionKey: string }> = {};
    const dimensions = tagConfig?.dimensions ?? [];

    for (const dimension of dimensions) {
      for (const tag of dimension.tags) {
        lookup[tag.tagKey] = {
          label: locale.startsWith("zh") ? tag.nameZh : tag.nameEn,
          dimensionKey: dimension.dimensionKey,
        };
      }
    }

    return lookup;
  }, [locale, tagConfig]);

  const numberFormatter = useMemo(
    () =>
      new Intl.NumberFormat(locale.startsWith("zh") ? "zh-CN" : "en-US", {
        maximumFractionDigits: 0,
      }),
    [locale]
  );
  const decimalFormatter = useMemo(
    () =>
      new Intl.NumberFormat(locale.startsWith("zh") ? "zh-CN" : "en-US", {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1,
      }),
    [locale]
  );
  const rankingByElo = useMemo(
    () =>
      leaderboards.ELO.items.reduce((accumulator: Record<string, number>, item) => {
        accumulator[item.specimenId] = item.rank;
        return accumulator;
      }, {}),
    [leaderboards.ELO.items]
  );
  const rankingByHype = useMemo(
    () =>
      leaderboards.HYPE.items.reduce((accumulator: Record<string, number>, item) => {
        accumulator[item.specimenId] = item.rank;
        return accumulator;
      }, {}),
    [leaderboards.HYPE.items]
  );
  const formatRankDelta = useCallback(
    (rankDelta: number | null) => {
      if (typeof rankDelta !== "number") {
        return t("insights.leaderboard.rank_delta_unknown");
      }
      if (rankDelta > 0) {
        return t("insights.leaderboard.rank_delta_up", { value: rankDelta });
      }
      if (rankDelta < 0) {
        return t("insights.leaderboard.rank_delta_down", { value: Math.abs(rankDelta) });
      }
      return t("insights.leaderboard.rank_delta_flat");
    },
    [t]
  );

  const sortedDimensions = useMemo(() => {
    const dimensions = tagConfig?.dimensions ?? [];
    return [...dimensions].sort((left, right) => left.sortOrder - right.sortOrder);
  }, [tagConfig]);

  const resolveDimensionLabel = useCallback(
    (dimension: TagDimension) => (locale.startsWith("zh") ? dimension.nameZh : dimension.nameEn),
    [locale]
  );
  const resolveTagLabel = useCallback(
    (tag: TagDefinition) => (locale.startsWith("zh") ? tag.nameZh : tag.nameEn),
    [locale]
  );

  const toggleTagSelection = useCallback((dimension: TagDimension, tagKey: string) => {
    setSelectedTagKeys((previous) => {
      const isSelected = previous.includes(tagKey);
      if (isSelected) {
        return previous.filter((key) => key !== tagKey);
      }

      if (dimension.selectMode === "single") {
        const dimensionTagKeys = new Set(dimension.tags.map((tag) => tag.tagKey));
        const preserved = previous.filter((key) => !dimensionTagKeys.has(key));
        return [...preserved, tagKey];
      }

      return [...previous, tagKey];
    });
  }, []);

  const clearTagSelection = useCallback(() => {
    setSelectedTagKeys([]);
  }, []);

  useEffect(() => {
    let canceled = false;

    const loadTagConfig = async () => {
      setIsTagConfigLoading(true);
      try {
        const config = await getTagConfig();
        if (canceled) {
          return;
        }
        setTagConfig(config);
      } catch (error: unknown) {
        if (canceled) {
          return;
        }
        const maybeError = error as ErrorLike;
        toast.error(t("filters.config_load_error"), {
          description: maybeError?.message || t("filters.config_load_error_fallback"),
        });
      } finally {
        if (!canceled) {
          setIsTagConfigLoading(false);
        }
      }
    };

    void loadTagConfig();
    return () => {
      canceled = true;
    };
  }, [t]);

  const fetchItems = useCallback(
    async (isInitial = false) => {
      if (!isInitial && (isLoading || !hasMore)) {
        return;
      }

      const requestSequence = ++requestSequenceRef.current;
      setIsLoading(true);
      try {
        const result = await getArchiveSpecimens({
          cursor: isInitial ? undefined : (cursor ?? undefined),
          sort,
          q: query || undefined,
          tags: selectedTagKeys,
          limit: 20,
        });

        if (requestSequence !== requestSequenceRef.current) {
          return;
        }

        setItems((previousItems) => (isInitial ? result.items : [...previousItems, ...result.items]));
        setCursor(result.nextCursor);
        setHasMore(result.hasMore);
      } catch (error: unknown) {
        console.error("Failed to fetch archive:", error);
        const maybeError = error as ErrorLike;
        toast.error(t("toast.communication_error"), {
          description: maybeError?.message || t("toast.mainframe_unreachable"),
        });
      } finally {
        if (requestSequence === requestSequenceRef.current) {
          setIsLoading(false);
        }
      }
    },
    [cursor, hasMore, isLoading, query, selectedTagKeys, sort, t]
  );

  const fetchInsights = useCallback(async () => {
    setIsInsightsLoading(true);
    try {
      const result = await getArchiveInsights();
      setInsights(result);
    } catch (error: unknown) {
      const maybeError = error as ErrorLike;
      toast.error(t("toast.communication_error"), {
        description: maybeError?.message || t("toast.mainframe_unreachable"),
      });
    } finally {
      setIsInsightsLoading(false);
    }
  }, [t]);

  const fetchLeaderboard = useCallback(
    async (
      metric: ArchiveLeaderboardMetric,
      options: {
        append?: boolean;
        cursor?: string | null;
      } = {}
    ) => {
      const append = options.append ?? false;
      setLeaderboards((previous) => ({
        ...previous,
        [metric]: {
          ...previous[metric],
          isLoading: true,
        },
      }));

      try {
        const result = await getArchiveLeaderboard({
          metric,
          cursor: append ? (options.cursor ?? undefined) : undefined,
          limit: LEADERBOARD_PAGE_SIZE,
        });

        setLeaderboards((previous) => ({
          ...previous,
          [metric]: {
            items: append ? [...previous[metric].items, ...result.items] : result.items,
            hasMore: result.hasMore,
            nextCursor: result.nextCursor,
            isLoading: false,
          },
        }));
      } catch (error: unknown) {
        const maybeError = error as ErrorLike;
        toast.error(t("toast.communication_error"), {
          description: maybeError?.message || t("toast.mainframe_unreachable"),
        });
        setLeaderboards((previous) => ({
          ...previous,
          [metric]: {
            ...previous[metric],
            isLoading: false,
          },
        }));
      }
    },
    [t]
  );

  useEffect(() => {
    void fetchItems(true);
    // fetchItems depends on pagination state; refresh trigger should only react to sorting and selected tags.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sort, selectedTagKeys]);

  useEffect(() => {
    void fetchInsights();
    void fetchLeaderboard("ELO");
    void fetchLeaderboard("HYPE");
  }, [fetchInsights, fetchLeaderboard]);

  return (
    <div className="space-y-8">
      <div className="sticky top-20 z-30 flex flex-col items-center justify-between gap-4 border-b border-white/5 bg-background/80 py-4 backdrop-blur-md md:flex-row">
        <div className="relative w-full md:w-96">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-500" />
          <input
            type="text"
            placeholder={t("search_placeholder")}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => event.key === "Enter" && fetchItems(true)}
            className="h-11 w-full rounded-xl border border-white/10 bg-black/40 pl-10 pr-4 font-mono text-sm transition-colors focus:border-primary/50 focus:outline-none"
          />
        </div>

        <div className="flex items-center gap-2">
          {(["HOT", "NEW", "INSANE"] as const).map((sortOption) => (
            <Button
              key={sortOption}
              variant={sort === sortOption ? "default" : "outline"}
              size="sm"
              onClick={() => setSort(sortOption)}
              className="h-8 px-4 font-mono text-[10px]"
            >
              {sortOption === "HOT" ? t("sort.hot") : sortOption === "NEW" ? t("sort.new") : t("sort.insane")}
            </Button>
          ))}
          <Button
            variant={isFilterPanelOpen ? "default" : "outline"}
            size="icon"
            className={cn("h-8 w-8 border-white/10", isFilterPanelOpen && "border-primary/40")}
            onClick={() => setIsFilterPanelOpen((previous) => !previous)}
            title={t("filters.toggle")}
          >
            <SlidersHorizontal className="h-4 w-4 text-zinc-400" />
          </Button>
        </div>
      </div>

      {isFilterPanelOpen && (
        <section className="rounded-2xl border border-white/10 bg-zinc-900/70 p-4">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <div>
              <p className="text-sm text-zinc-100">{t("filters.title")}</p>
              <p className="text-xs text-zinc-500">{t("filters.selected_count", { count: selectedTagKeys.length })}</p>
            </div>
            <Button
              variant="ghost"
              size="sm"
              className="h-8 px-3 text-xs text-zinc-400 hover:text-primary"
              onClick={clearTagSelection}
              disabled={selectedTagKeys.length === 0}
            >
              {t("filters.clear")}
            </Button>
          </div>

          {isTagConfigLoading ? (
            <p className="text-xs text-zinc-500">{t("filters.loading")}</p>
          ) : sortedDimensions.length === 0 ? (
            <p className="text-xs text-zinc-500">{t("filters.empty")}</p>
          ) : (
            <div className="space-y-4">
              {sortedDimensions.map((dimension) => (
                <div key={dimension.dimensionKey}>
                  <p className="mb-2 text-[11px] text-zinc-400">{resolveDimensionLabel(dimension)}</p>
                  <div className="flex flex-wrap gap-2">
                    {dimension.tags.map((tag) => {
                      const active = selectedTagSet.has(tag.tagKey);
                      return (
                        <button
                          key={tag.tagKey}
                          type="button"
                          onClick={() => toggleTagSelection(dimension, tag.tagKey)}
                          className={cn(
                            "rounded-md border px-2 py-1 font-mono text-[11px] transition-colors",
                            active
                              ? "border-primary/40 bg-primary/10 text-primary"
                              : "border-white/10 bg-zinc-950/70 text-zinc-400 hover:border-white/25 hover:text-zinc-200"
                          )}
                        >
                          {resolveTagLabel(tag)}
                        </button>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          )}

          {selectedTagKeys.length > 0 && (
            <div className="mt-4 border-t border-white/10 pt-3">
              <p className="mb-2 text-[11px] text-zinc-500">{t("filters.active")}</p>
              <div className="flex flex-wrap gap-2">
                {selectedTagKeys.map((tagKey) => (
                  <button
                    key={tagKey}
                    type="button"
                    onClick={() =>
                      setSelectedTagKeys((previous) => previous.filter((existingTagKey) => existingTagKey !== tagKey))
                    }
                    className="rounded-md border border-primary/40 bg-primary/10 px-2 py-1 font-mono text-[11px] text-primary"
                    title={t("filters.remove")}
                  >
                    {tagLookup[tagKey]?.label ?? tagKey}
                  </button>
                ))}
              </div>
            </div>
          )}
        </section>
      )}

      <section className="grid gap-3 xl:grid-cols-3">
        <article className="rounded-xl border border-white/10 bg-zinc-900/70 p-4">
          <p className="text-xs text-zinc-500">{t("insights.summary.title")}</p>
          {isInsightsLoading && !insights ? (
            <p className="mt-3 text-xs text-zinc-500">{t("filters.loading")}</p>
          ) : (
            <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
              <div className="rounded-lg border border-white/10 bg-zinc-950/70 p-3">
                <p className="text-zinc-500">{t("insights.summary.total_specimens")}</p>
                <p className="mt-1 font-mono text-zinc-100">
                  {numberFormatter.format(insights?.summary.totalSpecimens ?? 0)}
                </p>
              </div>
              <div className="rounded-lg border border-white/10 bg-zinc-950/70 p-3">
                <p className="text-zinc-500">{t("insights.summary.today_arena_battles")}</p>
                <p className="mt-1 font-mono text-zinc-100">
                  {numberFormatter.format(insights?.summary.todayArenaBattles ?? 0)}
                </p>
              </div>
              <div className="rounded-lg border border-white/10 bg-zinc-950/70 p-3">
                <p className="text-zinc-500">{t("insights.summary.average_elo")}</p>
                <p className="mt-1 font-mono text-zinc-100">
                  {numberFormatter.format(insights?.summary.averageElo ?? 0)}
                </p>
              </div>
              <div className="rounded-lg border border-white/10 bg-zinc-950/70 p-3">
                <p className="text-zinc-500">{t("insights.summary.total_votes")}</p>
                <p className="mt-1 font-mono text-zinc-100">
                  {numberFormatter.format(insights?.summary.totalVotes ?? 0)}
                </p>
              </div>
              <div className="col-span-2 rounded-lg border border-white/10 bg-zinc-950/70 p-3">
                <p className="text-zinc-500">{t("insights.summary.average_hype")}</p>
                <p className="mt-1 font-mono text-zinc-100">
                  {decimalFormatter.format(insights?.summary.averageHype ?? 0)}
                </p>
              </div>
            </div>
          )}
        </article>

        <article className="rounded-xl border border-white/10 bg-zinc-900/70 p-4">
          <p className="text-xs text-zinc-500">{t("insights.leaderboard.title")}</p>
          <div className="mt-3 space-y-4">
            <div>
              <p className="text-[11px] text-zinc-500">{t("insights.leaderboard.by_elo")}</p>
              {leaderboards.ELO.items.length > 0 ? (
                <ul className="mt-2 space-y-2">
                  {leaderboards.ELO.items.map((item) => (
                    <li key={`elo-${item.specimenId}`}>
                      <Link
                        href={`/${locale}/specimen/${item.specimenId}`}
                        className="flex items-center justify-between gap-2 rounded-md border border-white/10 bg-zinc-950/70 px-2 py-1.5 text-xs transition-colors hover:border-primary/50"
                      >
                        <span className="truncate text-zinc-300">{item.repoFullName}</span>
                        <div className="flex flex-col items-end">
                          <span className="font-mono text-primary">#{item.rank}</span>
                          <span className="font-mono text-[10px] text-zinc-500">
                            {t("insights.leaderboard.score", { value: numberFormatter.format(item.score) })}
                          </span>
                          <span className="font-mono text-[10px] text-zinc-500">{formatRankDelta(item.rankDelta)}</span>
                        </div>
                      </Link>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="mt-2 text-xs text-zinc-500">{t("insights.leaderboard.empty")}</p>
              )}
              {leaderboards.ELO.hasMore && (
                <Button
                  variant="ghost"
                  size="sm"
                  className="mt-2 h-7 px-2 text-[11px] text-zinc-400 hover:text-primary"
                  disabled={leaderboards.ELO.isLoading || !leaderboards.ELO.nextCursor}
                  onClick={() =>
                    fetchLeaderboard("ELO", { append: true, cursor: leaderboards.ELO.nextCursor })
                  }
                >
                  {leaderboards.ELO.isLoading ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  ) : (
                    t("insights.leaderboard.load_more")
                  )}
                </Button>
              )}
            </div>

            <div>
              <p className="text-[11px] text-zinc-500">{t("insights.leaderboard.by_hype")}</p>
              {leaderboards.HYPE.items.length > 0 ? (
                <ul className="mt-2 space-y-2">
                  {leaderboards.HYPE.items.map((item) => (
                    <li key={`hype-${item.specimenId}`}>
                      <Link
                        href={`/${locale}/specimen/${item.specimenId}`}
                        className="flex items-center justify-between gap-2 rounded-md border border-white/10 bg-zinc-950/70 px-2 py-1.5 text-xs transition-colors hover:border-orange-300/50"
                      >
                        <span className="truncate text-zinc-300">{item.repoFullName}</span>
                        <div className="flex flex-col items-end">
                          <span className="font-mono text-orange-300">#{item.rank}</span>
                          <span className="font-mono text-[10px] text-zinc-500">
                            {t("insights.leaderboard.score", { value: decimalFormatter.format(item.score) })}
                          </span>
                        </div>
                      </Link>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="mt-2 text-xs text-zinc-500">{t("insights.leaderboard.empty")}</p>
              )}
              {leaderboards.HYPE.hasMore && (
                <Button
                  variant="ghost"
                  size="sm"
                  className="mt-2 h-7 px-2 text-[11px] text-zinc-400 hover:text-primary"
                  disabled={leaderboards.HYPE.isLoading || !leaderboards.HYPE.nextCursor}
                  onClick={() =>
                    fetchLeaderboard("HYPE", { append: true, cursor: leaderboards.HYPE.nextCursor })
                  }
                >
                  {leaderboards.HYPE.isLoading ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  ) : (
                    t("insights.leaderboard.load_more")
                  )}
                </Button>
              )}
            </div>
          </div>
        </article>

        <article className="rounded-xl border border-white/10 bg-zinc-900/70 p-4">
          <p className="text-xs text-zinc-500">{t("insights.momentum.title")}</p>
          <div className="mt-3 grid grid-cols-3 gap-2 text-xs">
            <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/5 p-3 text-center">
              <p className="text-zinc-500">{t("insights.momentum.rising")}</p>
              <p className="mt-1 font-mono text-emerald-300">
                {numberFormatter.format(insights?.momentum.rising ?? 0)}
              </p>
            </div>
            <div className="rounded-lg border border-zinc-500/30 bg-zinc-500/10 p-3 text-center">
              <p className="text-zinc-500">{t("insights.momentum.unchanged")}</p>
              <p className="mt-1 font-mono text-zinc-200">
                {numberFormatter.format(insights?.momentum.unchanged ?? 0)}
              </p>
            </div>
            <div className="rounded-lg border border-rose-500/30 bg-rose-500/5 p-3 text-center">
              <p className="text-zinc-500">{t("insights.momentum.falling")}</p>
              <p className="mt-1 font-mono text-rose-300">
                {numberFormatter.format(insights?.momentum.falling ?? 0)}
              </p>
            </div>
          </div>

          <div className="mt-4 space-y-4">
            <div className="rounded-lg border border-white/10 bg-zinc-950/70 p-3">
              <p className="text-[11px] text-zinc-500">{t("insights.momentum.top_rising")}</p>
              {insights?.topRising && insights.topRising.length > 0 ? (
                <ul className="mt-2 space-y-2">
                  {insights.topRising.map((item) => (
                    <li key={`rising-${item.specimenId}`}>
                      <Link
                        href={`/${locale}/specimen/${item.specimenId}`}
                        className="flex items-center justify-between text-xs transition-colors hover:text-primary"
                      >
                        <span className="truncate text-zinc-300">{item.repoFullName}</span>
                        <span className="font-mono text-emerald-300">+{item.rankDelta}</span>
                      </Link>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="mt-2 text-xs text-zinc-500">{t("insights.momentum.no_rank_delta")}</p>
              )}
            </div>
            <div className="rounded-lg border border-white/10 bg-zinc-950/70 p-3">
              <p className="text-[11px] text-zinc-500">{t("insights.momentum.top_falling")}</p>
              {insights?.topFalling && insights.topFalling.length > 0 ? (
                <ul className="mt-2 space-y-2">
                  {insights.topFalling.map((item) => (
                    <li key={`falling-${item.specimenId}`}>
                      <Link
                        href={`/${locale}/specimen/${item.specimenId}`}
                        className="flex items-center justify-between text-xs transition-colors hover:text-primary"
                      >
                        <span className="truncate text-zinc-300">{item.repoFullName}</span>
                        <span className="font-mono text-rose-300">{item.rankDelta}</span>
                      </Link>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="mt-2 text-xs text-zinc-500">{t("insights.momentum.no_rank_delta")}</p>
              )}
            </div>
          </div>
        </article>
      </section>

      {items.length === 0 && !isLoading ? (
        <div className="rounded-3xl border border-dashed border-white/10 py-20 text-center">
          <p className="font-mono text-zinc-500">{t("empty")}</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {items.map((item) => (
            <ArchiveCard
              key={item.specimenId}
              specimen={item}
              eloRank={rankingByElo[item.specimenId]}
              hypeRank={rankingByHype[item.specimenId]}
            />
          ))}

          {isLoading &&
            Array.from({ length: 4 }).map((_, index) => (
              <div
                key={`skeleton-${index}`}
                className="h-64 animate-pulse rounded-2xl border border-white/5 bg-zinc-900/40"
              />
            ))}
        </div>
      )}

      {hasMore && (
        <div className="flex justify-center pt-8">
          <Button
            variant="ghost"
            onClick={() => fetchItems()}
            disabled={isLoading}
            className="font-mono text-zinc-500 hover:text-primary"
          >
            {isLoading ? <Loader2 className="mr-2 h-5 w-5 animate-spin" /> : t("load_more")}
          </Button>
        </div>
      )}
    </div>
  );
}
