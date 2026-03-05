/* eslint-disable @next/next/no-img-element */
import { backendFetch } from "@/shared/lib/backend-client";
import { SpecimenDetailData } from "@/shared/types/specimen";
import { SpecimenTagGroup } from "@/shared/components/specimen/tag-group";
import { HypeActions } from "@/shared/components/specimen/hype-actions";
import { WatchlistToggleButton } from "@/modules/specimen/components/watchlist-toggle-button";
import { SpecimenBettingAccessCard } from "@/modules/specimen/components/specimen-betting-access-card";
import { SpecimenCommentsPanel } from "@/modules/specimen/components/specimen-comments-panel";
import { ReadmeExcerptsPanel } from "@/modules/specimen/components/readme-excerpts-panel";
import { Button } from "@/shared/components/ui/button";
import { cn } from "@/lib/utils";
import {
  Activity,
  ChevronLeft,
  Coins,
  Code2,
  ExternalLink,
  FileText,
  GitFork,
  GitPullRequest,
  Globe2,
  Hash,
  MessageCircle,
  ShieldAlert,
  Siren,
  Star,
  UserRound,
} from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import type { ReactNode } from "react";

interface SpecimenPageProps {
  params: Promise<{ id: string; locale: string }>;
}

type SupportedLocale = "zh" | "en";
type LocalizedCopy = Partial<Record<SupportedLocale, string>>;

function resolveLocalizedCopy(copy: LocalizedCopy | undefined, locale: SupportedLocale, fallback: string) {
  const value = copy?.[locale];
  if (typeof value === "string" && value.trim().length > 0) {
    return value;
  }
  return fallback;
}

function formatDateLabel(timestamp: string | null | undefined, locale: SupportedLocale) {
  if (!timestamp) {
    return "-";
  }

  const parsed = new Date(timestamp);
  if (Number.isNaN(parsed.getTime())) {
    return "-";
  }

  return new Intl.DateTimeFormat(locale === "zh" ? "zh-CN" : "en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
  }).format(parsed);
}

function formatRelativeLabel(timestamp: string | null | undefined, locale: SupportedLocale) {
  if (!timestamp) {
    return "-";
  }

  const parsed = new Date(timestamp);
  if (Number.isNaN(parsed.getTime())) {
    return "-";
  }

  const diffMs = parsed.getTime() - Date.now();
  const absMs = Math.abs(diffMs);
  const rtf = new Intl.RelativeTimeFormat(locale === "zh" ? "zh-CN" : "en-US", {
    numeric: "auto",
  });

  const minute = 60_000;
  const hour = 60 * minute;
  const day = 24 * hour;

  if (absMs < hour) {
    return rtf.format(Math.round(diffMs / minute), "minute");
  }
  if (absMs < day * 7) {
    return rtf.format(Math.round(diffMs / hour), "hour");
  }
  return rtf.format(Math.round(diffMs / day), "day");
}

function formatCompactNumber(value: number | undefined, locale: SupportedLocale) {
  if (typeof value !== "number") {
    return "-";
  }

  return new Intl.NumberFormat(locale === "zh" ? "zh-CN" : "en-US", {
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(value);
}

function buildSparklinePath(values: number[], width: number, height: number) {
  if (values.length === 0) {
    return "";
  }

  const min = Math.min(...values);
  const max = Math.max(...values);
  const spread = Math.max(1, max - min);

  return values
    .map((value, index) => {
      const x = (index / Math.max(1, values.length - 1)) * width;
      const y = height - ((value - min) / spread) * height;
      return `${index === 0 ? "M" : "L"}${x.toFixed(2)},${y.toFixed(2)}`;
    })
    .join(" ");
}

function buildSparklineAreaPath(values: number[], width: number, height: number) {
  if (values.length === 0) {
    return "";
  }

  const min = Math.min(...values);
  const max = Math.max(...values);
  const spread = Math.max(1, max - min);

  const points = values.map((value, index) => {
    const x = (index / Math.max(1, values.length - 1)) * width;
    const y = height - ((value - min) / spread) * height;
    return { x, y };
  });

  const linePath = points
    .map((point, index) => `${index === 0 ? "M" : "L"}${point.x.toFixed(2)},${point.y.toFixed(2)}`)
    .join(" ");
  const firstPoint = points[0];
  const lastPoint = points[points.length - 1];

  return `${linePath} L${lastPoint.x.toFixed(2)},${height.toFixed(2)} L${firstPoint.x.toFixed(2)},${height.toFixed(2)} Z`;
}

function DetailSection({
  title,
  icon,
  monoTitle = false,
  children,
}: {
  title: string;
  icon: ReactNode;
  monoTitle?: boolean;
  children: ReactNode;
}) {
  return (
    <section className="rounded-2xl border border-white/10 bg-zinc-900/60 p-5 md:p-6 backdrop-blur-xl">
      <div className="mb-4 flex items-center gap-2 border-b border-white/10 pb-3">
        <span className="text-primary">{icon}</span>
        <h2 className={monoTitle ? "font-mono text-sm tracking-wider text-zinc-200" : "text-sm font-semibold text-zinc-100"}>
          {title}
        </h2>
      </div>
      {children}
    </section>
  );
}

function InfoRow({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4 text-xs">
      <span className="text-zinc-500">{label}</span>
      <span className="text-right font-mono text-zinc-200">{value}</span>
    </div>
  );
}

export async function generateMetadata({ params }: SpecimenPageProps): Promise<Metadata> {
  const { id, locale } = await params;
  const preferredLocale: SupportedLocale = locale.startsWith("zh") ? "zh" : "en";
  const t = await getTranslations({ locale, namespace: "specimen.detail" });

  try {
    const data = await backendFetch<SpecimenDetailData>(`/specimens/${id}`);
    const oneLiner = resolveLocalizedCopy(
      data.officialCommentary?.oneLiner,
      preferredLocale,
      t("defaults.no_diagnosis")
    );
    const description = data.githubMeta.description?.trim() || oneLiner || t("meta.description_fallback");

    return {
      title: t("meta.title", { repo: data.specimen.repoFullName }),
      description,
      openGraph: {
        title: t("meta.open_graph_title", { repo: data.specimen.repoFullName }),
        description,
        ...(data.githubMeta.owner?.avatarUrl ? { images: [data.githubMeta.owner.avatarUrl] } : {}),
      },
    };
  } catch {
    return { title: t("meta.not_found_title") };
  }
}

export default async function SpecimenDetailPage({ params }: SpecimenPageProps) {
  const { id, locale } = await params;
  const preferredLocale: SupportedLocale = locale.startsWith("zh") ? "zh" : "en";
  const t = await getTranslations({ locale, namespace: "specimen.detail" });

  let data: SpecimenDetailData;
  try {
    data = await backendFetch<SpecimenDetailData>(`/specimens/${id}`);
  } catch (error) {
    console.error("Failed to fetch specimen detail:", error);
    return notFound();
  }

  const { specimen, githubMeta, metrics, readme, tags, officialCommentary, codeHighlights, repoIdentity } = data;

  const [ownerPart, repoPart] = specimen.repoFullName.split("/");
  const ownerLogin = githubMeta.owner.login || ownerPart || "unknown";
  const ownerAvatarUrl =
    githubMeta.owner.avatarUrl ||
    `https://api.dicebear.com/7.x/identicon/svg?seed=${encodeURIComponent(ownerLogin || specimen.specimenId)}`;
  const ownerHtmlUrl = githubMeta.owner.htmlUrl || githubMeta.repoHtmlUrl;
  const ownerProfileName = repoIdentity?.owner?.githubLogin || ownerLogin;
  const ownerUserId = repoIdentity?.owner?.githubUserId || githubMeta.owner.id || "-";
  const oneLiner = resolveLocalizedCopy(
    officialCommentary?.oneLiner,
    preferredLocale,
    t("defaults.no_diagnosis")
  );
  const arenaReason = resolveLocalizedCopy(
    officialCommentary?.arenaReason,
    preferredLocale,
    t("defaults.no_arena_reason")
  );

  const readmeExcerpts = readme?.excerpts || [];
  const highlights = codeHighlights || [];
  const contributors = repoIdentity?.contributors || [];
  const eloSeries =
    (data.eloHistory ?? []).map((point) => ({
      at: point.at,
      value: point.elo,
    })) || [];
  const hasLiveTrend = eloSeries.length > 1;
  const trendSeries = eloSeries;
  const trendValues = trendSeries.map((point) => point.value);
  const trendPath = buildSparklinePath(trendValues, 640, 180);
  const trendAreaPath = buildSparklineAreaPath(trendValues, 640, 180);
  const trendMin = trendSeries.length > 0 ? Math.min(...trendSeries.map((point) => point.value)) : metrics.elo;
  const trendMax = trendSeries.length > 0 ? Math.max(...trendSeries.map((point) => point.value)) : metrics.elo;
  const trendFirst = trendSeries[0];
  const trendLast = trendSeries[trendSeries.length - 1];
  const trendDelta = trendFirst && trendLast ? trendLast.value - trendFirst.value : 0;

  return (
    <div className="relative min-h-screen overflow-hidden bg-background pb-20 pt-24 text-foreground">
      <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(rgba(255,255,255,0.02)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.02)_1px,transparent_1px)] bg-[size:28px_28px]" />
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_900px_at_50%_120px,rgba(217,70,239,0.18),transparent)]" />

      <div className="relative z-10 mx-auto max-w-7xl px-4 md:px-6">
        <nav className="mb-6 flex items-center justify-between">
          <Link
            href={`/${locale}/archive`}
            className="group inline-flex items-center gap-2 rounded-xl border border-white/10 bg-zinc-900/60 px-3 py-2 text-xs text-zinc-300 transition-colors hover:border-primary/50 hover:text-primary"
          >
            <ChevronLeft className="h-4 w-4 transition-transform group-hover:-translate-x-0.5" />
            {t("nav.back_to_archive")}
          </Link>

          <div className="inline-flex items-center gap-2 rounded-xl border border-white/10 bg-zinc-900/50 px-3 py-2 text-[11px] text-zinc-400">
            <span className="h-2 w-2 rounded-full bg-emerald-400" />
            {t("nav.dossier_online")}
          </div>
        </nav>

        <section className="rounded-3xl border border-white/10 bg-zinc-900/70 p-5 md:p-7 backdrop-blur-xl">
          <div className="flex flex-col gap-5 md:flex-row md:items-start">
            <div className="relative">
              <div className="h-20 w-20 overflow-hidden rounded-2xl border border-white/15 bg-zinc-950 shadow-[0_0_0_1px_rgba(255,255,255,0.02)] md:h-24 md:w-24">
                <img
                  src={ownerAvatarUrl}
                  alt={ownerLogin}
                  className="h-full w-full object-cover grayscale contrast-125 transition duration-300 hover:grayscale-0"
                />
              </div>
              <div className="absolute -bottom-2 -right-2 rounded-md border border-black bg-primary px-2 py-0.5 font-mono text-[10px] font-semibold text-black">
                {t("hero.badge")}
              </div>
            </div>

            <div className="min-w-0 flex-1 space-y-3">
              <p className="font-mono text-xs tracking-wider text-primary">
                {ownerPart || ownerLogin} /
              </p>
              <h1 className="font-mono text-2xl font-semibold tracking-tight text-zinc-100 md:text-4xl">{repoPart || specimen.repoFullName}</h1>

              <div className="flex flex-wrap gap-2">
                {githubMeta.topics?.length > 0 ? (
                  githubMeta.topics.map((topic) => (
                    <span
                      key={topic}
                      className="inline-flex items-center gap-1 rounded-md border border-white/10 bg-zinc-950/80 px-2 py-1 font-mono text-[10px] text-zinc-300"
                    >
                      <Hash className="h-3 w-3 text-zinc-500" />
                      {topic}
                    </span>
                  ))
                ) : (
                  <span className="rounded-md border border-dashed border-white/10 px-2 py-1 font-mono text-[10px] text-zinc-500">
                    {t("hero.no_topics")}
                  </span>
                )}
              </div>

              <div className="rounded-2xl border border-primary/20 bg-primary/5 p-4">
                <p className="text-sm leading-7 text-zinc-200">{githubMeta.description || oneLiner}</p>
              </div>

              <SpecimenTagGroup tags={tags} variant="outline" className="gap-2" />
            </div>
          </div>

          <div className="mt-5 grid gap-3 md:grid-cols-3">
            <div className="rounded-xl border border-white/10 bg-zinc-950/70 p-3">
              <p className="mb-1 text-[11px] text-zinc-500">{t("stats.primary_language")}</p>
              <p className="font-mono text-sm text-zinc-100">{githubMeta.languages?.[0]?.name || t("stats.unknown_language")}</p>
            </div>
            <div className="rounded-xl border border-white/10 bg-zinc-950/70 p-3">
              <p className="mb-1 text-[11px] text-zinc-500">{t("stats.stars")}</p>
              <p className="font-mono text-sm text-zinc-100">{githubMeta.stargazersCount.toLocaleString()}</p>
            </div>
            <div className="rounded-xl border border-white/10 bg-zinc-950/70 p-3">
              <p className="mb-1 text-[11px] text-zinc-500">{t("stats.last_push")}</p>
              <p className="font-mono text-sm text-zinc-100">{formatRelativeLabel(githubMeta.pushedAt, preferredLocale)}</p>
            </div>
          </div>
        </section>

        <div className="mt-6 grid grid-cols-1 gap-6 xl:grid-cols-12">
          <main className="space-y-6 xl:col-span-8">
            <DetailSection title={t("section.official_commentary")} icon={<Siren className="h-4 w-4" />}>
              <div className="grid gap-4 md:grid-cols-2">
                <div className="rounded-xl border border-primary/25 bg-primary/5 p-4">
                  <p className="mb-2 text-xs font-semibold tracking-wide text-primary">{t("commentary.diagnosis_label")}</p>
                  <p className="text-sm leading-7 text-zinc-200">&quot;{oneLiner}&quot;</p>
                </div>
                <div className="rounded-xl border border-violet-500/25 bg-violet-500/5 p-4">
                  <p className="mb-2 text-xs font-semibold tracking-wide text-violet-300">{t("commentary.arena_reason_label")}</p>
                  <p className="text-sm leading-7 text-zinc-300">{arenaReason}</p>
                </div>
              </div>
            </DetailSection>

            <DetailSection title={t("section.market_pulse")} icon={<Activity className="h-4 w-4" />}>
              <div className="rounded-xl border border-white/10 bg-zinc-950/80 p-4">
                <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
                  <p className="text-xs text-zinc-400">{t("market.trend_intraday")}</p>
                  <div className="inline-flex items-center gap-3 text-xs text-zinc-500">
                    <span
                      className={cn(
                        "rounded-full border px-2 py-0.5 font-mono text-[10px]",
                        hasLiveTrend
                          ? "border-emerald-300/40 bg-emerald-400/10 text-emerald-300"
                          : "border-zinc-300/30 bg-zinc-500/10 text-zinc-300"
                      )}
                    >
                      {hasLiveTrend ? t("market.source_live") : t("market.source_unavailable")}
                    </span>
                    <span>{t("market.max", { value: trendMax })}</span>
                    <span>{t("market.min", { value: trendMin })}</span>
                  </div>
                </div>
                {hasLiveTrend ? (
                  <>
                    <svg viewBox="0 0 640 180" className="h-44 w-full">
                      <defs>
                        <linearGradient id="elo-line-gradient" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="0%" stopColor="#d946ef" stopOpacity="0.95" />
                          <stop offset="100%" stopColor="#d946ef" stopOpacity="0.25" />
                        </linearGradient>
                        <linearGradient id="elo-area-gradient" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="0%" stopColor="#d946ef" stopOpacity="0.22" />
                          <stop offset="100%" stopColor="#d946ef" stopOpacity="0" />
                        </linearGradient>
                      </defs>
                      <path d={trendAreaPath} fill="url(#elo-area-gradient)" />
                      <path d={trendPath} fill="none" stroke="url(#elo-line-gradient)" strokeWidth="3" />
                    </svg>
                    <div className="mt-3 grid gap-2 text-[11px] text-zinc-500 md:grid-cols-3">
                      <span>{t("market.start", { value: formatDateLabel(trendFirst?.at, preferredLocale) })}</span>
                      <span>{t("market.end", { value: formatDateLabel(trendLast?.at, preferredLocale) })}</span>
                      <span
                        className={cn(
                          "font-mono",
                          trendDelta > 0 ? "text-emerald-300" : trendDelta < 0 ? "text-rose-300" : "text-zinc-400"
                        )}
                      >
                        {t("market.delta", { value: `${trendDelta > 0 ? "+" : ""}${trendDelta}` })}
                      </span>
                    </div>
                  </>
                ) : (
                  <div className="rounded-xl border border-dashed border-white/15 bg-zinc-900/70 px-4 py-10 text-center text-sm text-zinc-500">
                    {t("market.unavailable_note")}
                  </div>
                )}
                <p className="mt-2 text-[11px] text-zinc-500">
                  {hasLiveTrend ? t("market.live_note") : t("market.unavailable_note")}
                </p>
              </div>
            </DetailSection>

            <DetailSection title={t("section.market_access")} icon={<Coins className="h-4 w-4" />}>
              <SpecimenBettingAccessCard specimenId={specimen.specimenId} locale={preferredLocale} />
            </DetailSection>

            <DetailSection title={t("section.technical_dossier")} icon={<FileText className="h-4 w-4" />} monoTitle>
              <div className="space-y-6">
                <div className="space-y-3">
                  <p className="text-xs font-semibold tracking-wide text-zinc-400">{t("dossier.readme_excerpts")}</p>
                  <ReadmeExcerptsPanel excerpts={readmeExcerpts} locale={preferredLocale} />
                </div>

                <div className="space-y-3">
                  <p className="text-xs font-semibold tracking-wide text-zinc-400">{t("dossier.code_highlights")}</p>
                  {highlights.length > 0 ? (
                    <div className="space-y-4">
                      {highlights.map((highlight, index) => (
                        <article key={`${highlight.title}-${index}`} className="rounded-xl border border-white/10 bg-black/70">
                          <header className="flex items-center justify-between border-b border-white/10 px-4 py-2">
                            <p className="font-mono text-xs text-zinc-300">{highlight.title}</p>
                            <p className="font-mono text-[11px] text-primary">{highlight.codeLanguage}</p>
                          </header>
                          <pre className="overflow-x-auto px-4 py-3 text-xs text-emerald-300">
                            <code>{highlight.snippet}</code>
                          </pre>
                          <footer className="border-t border-white/10 px-4 py-2 text-xs text-zinc-500">{highlight.explainText}</footer>
                        </article>
                      ))}
                    </div>
                  ) : (
                    <div className="rounded-xl border border-dashed border-white/15 bg-zinc-950/70 p-4 text-sm text-zinc-500">
                      {t("code.empty")}
                    </div>
                  )}
                </div>
              </div>
            </DetailSection>

            <DetailSection title={t("section.comments")} icon={<MessageCircle className="h-4 w-4" />}>
              <SpecimenCommentsPanel
                specimenId={specimen.specimenId}
                locale={preferredLocale}
                ownerUserId={repoIdentity?.owner?.githubUserId}
                contributorUserIds={contributors.map((item) => item.githubUserId)}
              />
            </DetailSection>
          </main>

          <aside className="space-y-4 xl:col-span-4 xl:sticky xl:top-24 xl:self-start">
            <section className="rounded-2xl border border-white/10 bg-zinc-900/70 p-5 backdrop-blur-xl">
              <p className="mb-1 text-xs tracking-wide text-zinc-500">{t("sidebar.global_ranking")}</p>
              <p className="font-pixel text-4xl text-primary drop-shadow-[2px_2px_0px_rgba(0,0,0,1)]">{metrics.elo}</p>

              <div className="mt-4 grid grid-cols-2 gap-3">
                <div className="rounded-lg border border-white/10 bg-zinc-950/70 p-3 text-center">
                  <Star className="mx-auto mb-1 h-4 w-4 text-yellow-400" />
                  <p className="font-mono text-sm text-zinc-100">{formatCompactNumber(githubMeta.stargazersCount, preferredLocale)}</p>
                  <p className="text-[11px] text-zinc-500">{t("sidebar.stars")}</p>
                </div>
                <div className="rounded-lg border border-white/10 bg-zinc-950/70 p-3 text-center">
                  <GitFork className="mx-auto mb-1 h-4 w-4 text-sky-400" />
                  <p className="font-mono text-sm text-zinc-100">{formatCompactNumber(githubMeta.forksCount, preferredLocale)}</p>
                  <p className="text-[11px] text-zinc-500">{t("sidebar.forks")}</p>
                </div>
              </div>

              <div className="mt-4 space-y-2">
                <div className="flex items-center justify-between text-xs">
                  <span className="text-zinc-500">{t("sidebar.hype")}</span>
                  <span className="font-mono text-orange-400">{metrics.hype.toFixed(1)}%</span>
                </div>
                <div className="h-2 overflow-hidden rounded-full bg-zinc-800">
                  <div
                    className="h-full bg-gradient-to-r from-yellow-400 to-red-500"
                    style={{ width: `${Math.max(0, Math.min(metrics.hype, 100))}%` }}
                  />
                </div>
                <HypeActions specimenId={specimen.specimenId} initialScore={metrics.hype} />
              </div>

              <div className="mt-4 grid grid-cols-2 gap-2">
                <div className="rounded-lg border border-white/10 bg-zinc-950/70 p-3 text-center">
                  <p className="font-mono text-sm text-zinc-100">{metrics.votes.toLocaleString()}</p>
                  <p className="text-[11px] text-zinc-500">{t("sidebar.votes")}</p>
                </div>
                <div className="rounded-lg border border-white/10 bg-zinc-950/70 p-3 text-center">
                  <p className="font-mono text-sm text-zinc-100">{typeof metrics.comments === "number" ? metrics.comments.toLocaleString() : "-"}</p>
                  <p className="text-[11px] text-zinc-500">{t("sidebar.comments")}</p>
                </div>
              </div>
            </section>

            <section className="rounded-2xl border border-white/10 bg-zinc-900/70 p-5 backdrop-blur-xl">
              <p className="mb-4 text-sm font-semibold text-zinc-100">{t("sidebar.repository_profile")}</p>
              <div className="space-y-6">
                <div>
                  <p className="mb-3 text-xs font-semibold tracking-wide text-zinc-400">{t("sidebar.repository_meta")}</p>
                  <div className="space-y-3">
                    <InfoRow label={t("repo_meta.license")} value={githubMeta.license?.name || "-"} />
                    <InfoRow
                      label={t("repo_meta.homepage")}
                      value={
                        githubMeta.homepage ? (
                          <a
                            href={githubMeta.homepage}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-primary transition-colors hover:text-primary/80"
                          >
                            {t("repo_meta.homepage_available")}
                          </a>
                        ) : (
                          "-"
                        )
                      }
                    />
                    <InfoRow label={t("repo_meta.last_push")} value={formatDateLabel(githubMeta.pushedAt, preferredLocale)} />
                    <InfoRow label={t("repo_meta.metadata_synced")} value={formatDateLabel(githubMeta.metadataSyncedAt, preferredLocale)} />
                  </div>
                </div>

                <div>
                  <p className="mb-3 text-xs font-semibold tracking-wide text-zinc-400">{t("sidebar.repo_identity")}</p>
                  <div className="rounded-lg border border-primary/25 bg-primary/5 p-3">
                    <p className="mb-2 inline-flex items-center gap-1 text-[11px] text-primary">
                      <UserRound className="h-3.5 w-3.5" /> {t("identity.owner")}
                    </p>
                    <div className="flex items-center gap-3">
                      <img
                        src={ownerAvatarUrl}
                        alt={ownerLogin}
                        className="h-9 w-9 rounded-md border border-white/10 object-cover"
                      />
                      <div className="min-w-0">
                        <a
                          href={ownerHtmlUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="truncate font-mono text-sm text-zinc-100 transition-colors hover:text-primary"
                        >
                          {ownerProfileName}
                        </a>
                        <p className="font-mono text-[11px] text-zinc-500">
                          {t("identity.owner_user_id", {
                            id: ownerUserId,
                          })}
                        </p>
                      </div>
                    </div>
                    <p className="mt-2 text-[11px] text-zinc-500">{t("identity.contributor_note")}</p>
                  </div>
                </div>
              </div>
            </section>

            <section className="rounded-2xl border border-white/10 bg-zinc-900/70 p-5 backdrop-blur-xl">
              <p className="mb-3 text-sm font-semibold text-zinc-100">{t("sidebar.actions")}</p>
              <div className="space-y-3">
                <Button asChild className="h-11 w-full border border-white/20 bg-white text-black hover:bg-zinc-200">
                  <a href={githubMeta.repoHtmlUrl} target="_blank" rel="noopener noreferrer">
                    <ExternalLink className="mr-2 h-4 w-4" />
                    {t("actions.open_github")}
                  </a>
                </Button>

                {githubMeta.homepage && (
                  <Button asChild variant="outline" className="h-10 w-full border-white/20 bg-zinc-950/80 hover:bg-zinc-800">
                    <a href={githubMeta.homepage} target="_blank" rel="noopener noreferrer">
                      <Globe2 className="mr-2 h-4 w-4" />
                      {t("actions.open_homepage")}
                    </a>
                  </Button>
                )}

                <div className="grid grid-cols-2 gap-3">
                  <WatchlistToggleButton
                    specimenId={specimen.specimenId}
                    source="DETAIL"
                    className="h-10 border-white/20 bg-zinc-950/80 text-zinc-200 hover:bg-zinc-800"
                  />
                  <Button
                    variant="outline"
                    disabled
                    className="h-10 border-white/20 bg-zinc-950/80 text-zinc-500 hover:bg-zinc-950 hover:text-zinc-500"
                  >
                    <ShieldAlert className="mr-2 h-4 w-4" />
                    {t("actions.report_soon")}
                  </Button>
                </div>
              </div>

              <div className="mt-4 rounded-lg border border-dashed border-white/10 bg-zinc-950/80 p-3 text-[11px] text-zinc-500">
                <p className="inline-flex items-center gap-1">
                  <Code2 className="h-3.5 w-3.5" />
                  {t("contract.official_commentary")}
                </p>
                <p className="mt-1 inline-flex items-center gap-1">
                  <GitPullRequest className="h-3.5 w-3.5" />
                  {t("contract.comments_placeholder")}
                </p>
              </div>
            </section>
          </aside>
        </div>
      </div>
    </div>
  );
}
