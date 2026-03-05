"use client";

import { useEffect, useState } from "react";
import {
  Sheet,
  SheetContent,
  SheetTitle,
} from "@/shared/components/ui/sheet";
import { getSpecimenDrawer } from "@/shared/api/specimen";
import { SpecimenDrawerData } from "@/shared/types/specimen";
import { SpecimenTagGroup } from "@/shared/components/specimen/tag-group";
import { HypeActions } from "@/shared/components/specimen/hype-actions";
import { GitHubWarningModal } from "@/shared/components/specimen/github-warning-modal";
import { Button } from "@/shared/components/ui/button";
import { ExternalLink, Loader2, Quote, Activity, Terminal } from "lucide-react";
import { cn } from "@/lib/utils";
import Link from "next/link";
import { useLocale, useTranslations } from "next-intl";

interface SpecimenDrawerProps {
  specimenId: string | null;
  isOpen: boolean;
  onClose: () => void;
  allowFullDetail?: boolean;
}

/**
 * SpecimenDrawer
 * Fast preview drawer for Arena or Archive.
 * Neo-Brutalist / Retro Terminal Style
 */
export function SpecimenDrawer({
  specimenId,
  isOpen,
  onClose,
  allowFullDetail = true
}: SpecimenDrawerProps) {
  const locale = useLocale();
  const t = useTranslations("specimen.drawer");
  const [data, setData] = useState<SpecimenDrawerData | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [showWarning, setShowWarning] = useState(false);

  useEffect(() => {
    let isMounted = true;
    if (isOpen && specimenId) {
      setTimeout(() => {
        if (isMounted) setIsLoading(true);
      }, 0);

      getSpecimenDrawer(specimenId)
        .then(result => {
          if (isMounted) setData(result);
        })
        .catch(console.error)
        .finally(() => {
          if (isMounted) setIsLoading(false);
        });
    } else {
      setTimeout(() => setData(null), 0);
    }
    return () => { isMounted = false; };
  }, [isOpen, specimenId]);

  const handleOpenGitHub = () => {
    setShowWarning(true);
  };

  const confirmGitHub = () => {
    if (data?.githubMeta.repoHtmlUrl) {
      window.open(data.githubMeta.repoHtmlUrl, "_blank", "noopener,noreferrer");
    }
    setShowWarning(false);
  };

  const ownerLogin = data?.githubMeta.ownerLogin || data?.specimen.repoFullName.split("/")[0] || "unknown";
  const ownerAvatarUrl =
    data?.githubMeta.ownerAvatarUrl ||
    (data
      ? `https://api.dicebear.com/7.x/identicon/svg?seed=${encodeURIComponent(ownerLogin || data.specimen.specimenId)}`
      : "");
  const repoName = data?.specimen.repoFullName.split("/")[1] || data?.specimen.repoFullName || "";

  return (
    <>
      <Sheet open={isOpen} onOpenChange={(open) => !open && onClose()}>
        <SheetContent
          closeLabel={t("close")}
          className="w-full sm:max-w-md bg-zinc-950 border-l-2 border-zinc-700 p-0 shadow-2xl flex flex-col h-full font-mono"
        >
          <SheetTitle className="sr-only">
            {data?.specimen.repoFullName ?? t("sr_title")}
          </SheetTitle>

          {/* Terminal Header */}
          <div className="h-10 bg-zinc-900 border-b-2 border-zinc-700 flex items-center justify-between px-4 shrink-0">
             <div className="flex gap-2">
                <div className="w-3 h-3 bg-red-500 rounded-none" />
                <div className="w-3 h-3 bg-yellow-500 rounded-none" />
                <div className="w-3 h-3 bg-green-500 rounded-none" />
             </div>
             <div className="text-[10px] text-zinc-500 tracking-widest">
                {t("preview_mode")}
             </div>
             <div className="w-8" />
          </div>

          {isLoading ? (
            <div className="flex-1 flex flex-col items-center justify-center gap-4 text-zinc-500">
              <Loader2 className="w-8 h-8 animate-spin text-primary" />
              <p className="text-xs tracking-widest animate-pulse">{t("loading")}</p>
            </div>
          ) : data ? (
            <div className="flex-1 overflow-y-auto">

              {/* Cover / Identity */}
              <div className="relative h-48 bg-zinc-900 border-b-2 border-zinc-800 flex items-center justify-center overflow-hidden">
                <div className="absolute inset-0 opacity-10"
                     style={{ backgroundImage: 'radial-gradient(circle at center, #333 1px, transparent 1px)', backgroundSize: '20px 20px' }}
                />
                <div className="relative z-10 text-center space-y-3">
                   <div className="w-20 h-20 mx-auto bg-black border-2 border-white/20 p-1 shadow-[4px_4px_0px_0px_rgba(0,0,0,0.5)]">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img
                        src={ownerAvatarUrl}
                        alt={ownerLogin}
                        className="w-full h-full grayscale contrast-125"
                      />
                   </div>
                   <div>
                     <p className="text-[10px] text-primary tracking-widest mb-1">{ownerLogin}</p>
                     <h2 className="font-pixel text-xl text-white">
                       {repoName}
                     </h2>
                   </div>
                </div>
              </div>

              <div className="p-6 space-y-8">
                {/* Vitals */}
                <div className="grid grid-cols-2 gap-4">
                   <div className="bg-zinc-900 border border-zinc-700 p-3 text-center">
                      <div className="text-[10px] text-zinc-500 tracking-widest mb-1">{t("elo_rating")}</div>
                      <div className="font-pixel text-lg text-primary">{data.metrics.elo}</div>
                   </div>
                   <div className="bg-zinc-900 border border-zinc-700 p-3 text-center">
                      <div className="text-[10px] text-zinc-500 tracking-widest mb-1">{t("stars")}</div>
                      <div className="font-pixel text-lg text-white">{data.githubMeta.stargazersCount.toLocaleString()}</div>
                   </div>
                </div>

                {/* Diagnostics */}
                <div className="space-y-3">
                   <h4 className="flex items-center gap-2 text-[10px] text-zinc-500 tracking-widest border-b border-zinc-800 pb-2">
                      <Activity className="w-3 h-3" /> {t("diagnostics")}
                   </h4>
                   <SpecimenTagGroup tags={data.tags} className="gap-2" />
                </div>

                {/* Readme Snippet */}
                <div className="space-y-3">
                   <h4 className="flex items-center gap-2 text-[10px] text-zinc-500 tracking-widest border-b border-zinc-800 pb-2">
                      <Terminal className="w-3 h-3" /> {t("system_output")}
                   </h4>
                   <div className="bg-black border border-zinc-800 p-4 font-mono text-xs text-zinc-400 italic leading-relaxed">
                      <span className="text-primary not-italic mr-2">{">"}</span>
                      {data.readmeExcerpt?.text || data.githubMeta.description || t("no_output")}
                      <span className="inline-block w-1.5 h-3 bg-primary ml-1 animate-pulse align-middle" />
                   </div>
                </div>

                {/* Top Roast */}
                {data.topRoast && (
                  <div className="space-y-3">
                     <h4 className="flex items-center gap-2 text-[10px] text-zinc-500 tracking-widest border-b border-zinc-800 pb-2">
                        <Quote className="w-3 h-3" /> {t("community_roast")}
                     </h4>
                     <div className="bg-primary/5 border border-primary/20 p-4 text-xs text-primary font-bold italic">
                        &quot;{data.topRoast.preview_text}&quot;
                     </div>
                  </div>
                )}

                {/* Hype */}
                <div className="pt-2">
                   <HypeActions specimenId={data.specimen.specimenId} initialScore={data.metrics.hype} />
                </div>
              </div>

            </div>
          ) : null}

          {/* Footer Actions */}
          {data && (
            <div className="p-4 bg-zinc-900 border-t-2 border-zinc-700 space-y-3 shrink-0">
                <Button
                  onClick={handleOpenGitHub}
                  className="w-full h-10 bg-white text-black hover:bg-zinc-200 font-mono text-xs font-bold rounded-none border-2 border-transparent shadow-[4px_4px_0px_0px_rgba(0,0,0,0.5)] active:translate-y-1 active:shadow-none transition-all"
                >
                  {t("open_github")} <ExternalLink className="w-3 h-3 ml-2" />
               </Button>

               {allowFullDetail && (
                  <Button
                    asChild
                    disabled={!data.canOpenInternalDetail}
                    variant="outline"
                    className={cn(
                      "w-full h-10 border-zinc-600 text-zinc-300 hover:bg-zinc-800 hover:text-white font-mono text-xs rounded-none",
                      !data.canOpenInternalDetail && "opacity-50 cursor-not-allowed"
                    )}
                  >
                    <Link href={`/${locale}/specimen/${data.specimen.specimenId}`}>
                      {data.canOpenInternalDetail ? t("view_full_report") : t("vote_to_unlock")}
                    </Link>
                  </Button>
               )}
            </div>
          )}

        </SheetContent>
      </Sheet>

      {/* Warning Modal */}
      {data && (
        <GitHubWarningModal
          isOpen={showWarning}
          onClose={() => setShowWarning(false)}
          onConfirm={confirmGitHub}
          repoUrl={data.githubMeta.repoHtmlUrl}
          warningTitle={data.githubJumpWarning.title}
          warningBody={data.githubJumpWarning.body}
          destinationLabel={t("warning_target_destination")}
          confirmLabel={t("warning_confirm")}
          cancelLabel={t("warning_cancel")}
          closeLabel={t("warning_close")}
        />
      )}
    </>
  );
}
