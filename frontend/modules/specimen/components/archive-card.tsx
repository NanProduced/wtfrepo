/* eslint-disable @next/next/no-img-element */
"use client";

import { ArchiveSpecimen } from "@/shared/types/specimen";
import { SpotlightCard } from "@/shared/components/ui/spotlight-card";
import { Star, Flame, Trophy, ExternalLink } from "lucide-react";
import { cn } from "@/lib/utils";
import Link from "next/link";
import { useLocale, useTranslations } from "next-intl";

interface ArchiveCardProps {
  specimen: ArchiveSpecimen;
  eloRank?: number;
  hypeRank?: number;
}

/**
 * ArchiveCard - Neo-Brutalist Edition
 * Display a specimen in the Archive list.
 */
export function ArchiveCard({ specimen, eloRank, hypeRank }: ArchiveCardProps) {
  const { githubMeta, metrics, tags } = specimen;
  const locale = useLocale();
  const t = useTranslations("archive.card");
  const [ownerPart, repoPart] = specimen.repoFullName.split("/");
  const ownerLogin = githubMeta.ownerLogin || ownerPart || "unknown";
  const ownerAvatarUrl =
    githubMeta.ownerAvatarUrl ||
    `https://api.dicebear.com/7.x/identicon/svg?seed=${encodeURIComponent(ownerLogin || specimen.specimenId)}`;
  const repoName = repoPart || specimen.repoFullName;

  return (
    <SpotlightCard
      spotlightColor="rgba(34, 197, 94, 0.15)" // Greenish spotlight
      className="group relative flex flex-col h-full bg-card border-2 border-border hover:border-primary hover:translate-x-[-2px] hover:translate-y-[-2px] hover:shadow-[4px_4px_0px_0px_var(--primary)] transition-all duration-200"
    >
      {/* Terminal Header Strip */}
      <div className="h-6 bg-muted border-b-2 border-border flex items-center px-2 justify-between">
        <div className="flex gap-1">
          <div className="w-2 h-2 bg-red-500 rounded-none" />
          <div className="w-2 h-2 bg-yellow-500 rounded-none" />
          <div className="w-2 h-2 bg-green-500 rounded-none" />
        </div>
        <div className="font-mono text-[8px] text-muted-foreground tracking-widest">
          {t("log_label", { id: specimen.specimenId })}
        </div>
      </div>

      <div className="p-4 flex flex-col flex-1">
        {/* Header: Owner/Name + Rank/Elo */}
        <div className="flex justify-between items-start mb-3 gap-2">
          <Link
            href={`/${locale}/specimen/${specimen.specimenId}`}
            className="flex-1 min-w-0 group-hover:text-primary transition-colors"
          >
            <div className="flex items-center gap-2 mb-1">
              {/* Pixelated Avatar Placeholder or Real Avatar */}
              <div className="w-5 h-5 border border-border bg-muted flex items-center justify-center overflow-hidden">
                 <img
                    src={ownerAvatarUrl}
                    alt={ownerLogin}
                    className="w-full h-full object-cover grayscale opacity-80 group-hover:grayscale-0 group-hover:opacity-100"
                  />
              </div>
              <span className="font-mono text-[10px] text-muted-foreground truncate tracking-wider">
                {ownerLogin}
              </span>
            </div>
            <h3 className="font-pixel text-sm md:text-base leading-tight text-foreground truncate">
              {repoName}
            </h3>
          </Link>

          <div className="flex flex-col items-end shrink-0">
            <div className="flex items-center gap-1.5 bg-primary/10 px-2 py-0.5 border border-primary/20">
              <Trophy className="w-3 h-3 text-primary" />
              <span className="font-mono text-xs font-bold text-primary">
                {metrics.elo}
              </span>
            </div>
            <div className="mt-2 flex flex-wrap justify-end gap-1">
              {typeof eloRank === "number" && (
                <span className="rounded border border-primary/40 bg-primary/10 px-1.5 py-0.5 font-mono text-[10px] text-primary">
                  {t("rank_elo", { rank: eloRank })}
                </span>
              )}
              {typeof hypeRank === "number" && (
                <span className="rounded border border-orange-400/40 bg-orange-500/10 px-1.5 py-0.5 font-mono text-[10px] text-orange-300">
                  {t("rank_hype", { rank: hypeRank })}
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Summary / Snippet */}
        <div className="relative border-l-2 border-muted pl-3 py-1 mb-4 min-h-[40px]">
          <p className="text-muted-foreground text-xs line-clamp-2 font-mono leading-relaxed">
            <span className="text-primary mr-1">{">"}</span>
            {specimen.oneLiner || t("pending")}
          </p>
        </div>

        {/* Tags */}
        <div className="flex-1 mb-4 flex flex-wrap gap-1 content-start">
           {tags.map((tag) => (
             <span key={tag.tagKey} className="text-[10px] font-mono border border-border px-1 py-0.5 text-muted-foreground bg-muted/30">
               #{tag.name}
             </span>
           ))}
        </div>

        {/* Footer Metrics */}
        <div className="flex items-center justify-between pt-3 border-t-2 border-dashed border-border mt-auto">
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1 text-muted-foreground font-mono text-[10px]" title={t("stars")}>
              <Star className="w-3 h-3" />
              {githubMeta.stargazersCount.toLocaleString()}
            </div>
            <div className="flex items-center gap-1 text-muted-foreground font-mono text-[10px]" title={t("hype")}>
              <Flame className="w-3 h-3 text-orange-500" />
              {metrics.hype.toFixed(1)}
            </div>
             {metrics.delta24h !== undefined && (
              <span className={cn(
                "font-mono text-[10px]",
                metrics.delta24h >= 0 ? "text-green-500" : "text-red-500"
              )}>
                {metrics.delta24h >= 0 ? "▲" : "▼"}{Math.abs(metrics.delta24h)}
              </span>
            )}
          </div>

          <Link
            href={`/${locale}/specimen/${specimen.specimenId}`}
            className="text-muted-foreground hover:text-primary transition-colors p-1 hover:bg-muted"
          >
            <ExternalLink className="w-3.5 h-3.5" />
          </Link>
        </div>
      </div>
    </SpotlightCard>
  );
}
