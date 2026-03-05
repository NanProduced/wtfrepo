"use client";

import { cn } from "@/lib/utils";
import { Button } from "@/shared/components/ui/button";
import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";

type SupportedLocale = "zh" | "en";

interface ReadmeExcerptItem {
  excerptType: string;
  text: string;
  translatedTextZh?: string;
  translationMeta?: {
    source?: string;
    translatedBy?: string;
    translatedAt?: string;
    [key: string]: unknown;
  };
}

interface ReadmeExcerptsPanelProps {
  excerpts: ReadmeExcerptItem[];
  locale: SupportedLocale;
}

type ReadmeLanguage = "en" | "zh";

export function ReadmeExcerptsPanel({ excerpts, locale }: ReadmeExcerptsPanelProps) {
  const t = useTranslations("specimen.detail");
  const hasZhTranslation = excerpts.some(
    (excerpt) =>
      typeof excerpt.translatedTextZh === "string" && excerpt.translatedTextZh.trim().length > 0
  );
  const [languageMode, setLanguageMode] = useState<ReadmeLanguage>(locale === "zh" ? "zh" : "en");

  const resolvedExcerpts = useMemo(
    () =>
      excerpts.map((excerpt) => {
        const translatedText =
          typeof excerpt.translatedTextZh === "string" ? excerpt.translatedTextZh.trim() : "";
        const useTranslated = languageMode === "zh" && translatedText.length > 0;
        return {
          ...excerpt,
          displayText: useTranslated ? translatedText : excerpt.text,
          usedTranslatedText: useTranslated,
        };
      }),
    [excerpts, languageMode]
  );

  const showTranslationNotice =
    languageMode === "zh" && resolvedExcerpts.some((excerpt) => excerpt.usedTranslatedText);

  const resolveExcerptTypeLabel = (excerptType: string) => {
    const normalizedType = excerptType.trim().toUpperCase();
    if (normalizedType === "FUNNY") {
      return t("readme.excerpt_types.funny");
    }
    if (normalizedType === "SUMMARY") {
      return t("readme.excerpt_types.summary");
    }
    if (normalizedType === "HIGHLIGHT") {
      return t("readme.excerpt_types.highlight");
    }
    return excerptType;
  };

  if (resolvedExcerpts.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-white/15 bg-zinc-950/70 p-4 text-sm text-zinc-500">
        {t("readme.empty")}
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-white/10 bg-zinc-950/60 px-3 py-2">
        <p className="text-xs text-zinc-400">{t("readme.language_mode_label")}</p>
        <div className="inline-flex rounded-lg border border-white/10 bg-zinc-900/70 p-1">
          <Button
            type="button"
            size="sm"
            variant={languageMode === "en" ? "secondary" : "ghost"}
            className={cn("h-7 px-2.5 text-[11px]", languageMode === "en" ? "" : "text-zinc-400")}
            onClick={() => setLanguageMode("en")}
          >
            {t("readme.language_mode.en")}
          </Button>
          <Button
            type="button"
            size="sm"
            variant={languageMode === "zh" ? "secondary" : "ghost"}
            className={cn("h-7 px-2.5 text-[11px]", languageMode === "zh" ? "" : "text-zinc-400")}
            onClick={() => setLanguageMode("zh")}
          >
            {t("readme.language_mode.zh")}
          </Button>
        </div>
      </div>

      {languageMode === "zh" && !hasZhTranslation ? (
        <div className="rounded-xl border border-dashed border-white/15 bg-zinc-950/70 p-3 text-xs text-zinc-500">
          {t("readme.translation_fallback_note")}
        </div>
      ) : null}

      {showTranslationNotice ? (
        <div className="rounded-xl border border-amber-500/35 bg-amber-500/10 p-3 text-xs text-amber-200">
          {t("readme.translation_notice")}
        </div>
      ) : null}

      <div className="space-y-3">
        {resolvedExcerpts.map((excerpt, index) => (
          <article
            key={`${excerpt.excerptType}-${index}`}
            className="rounded-xl border border-white/10 bg-zinc-950/80 p-4"
          >
            <p className="mb-2 text-[11px] font-semibold tracking-wide text-primary">
              {resolveExcerptTypeLabel(excerpt.excerptType)}
            </p>
            <p className="text-sm leading-7 text-zinc-300">{excerpt.displayText}</p>
          </article>
        ))}
      </div>
    </div>
  );
}
