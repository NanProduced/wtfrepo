"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Flag, Loader2, MessageCircle, Sparkles } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { cn } from "@/lib/utils";
import { Button } from "@/shared/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/shared/components/ui/dialog";
import {
  getSpecimenComments,
  getSpecimenTopRoast,
  publishComment,
  reportComment,
  resonateComment,
} from "@/shared/api/comments";
import type {
  CommentListItem,
  CommentReportReasonCode,
  CommentSort,
  CommentTopRoastResponse,
} from "@/shared/types/comments";

const REPORT_REASONS: CommentReportReasonCode[] = [
  "SPAM",
  "HATE",
  "PORN",
  "POLITICAL",
  "COPYRIGHT",
  "OTHER",
];

interface SpecimenCommentsPanelProps {
  specimenId: string;
  locale: "zh" | "en";
  ownerUserId?: string | null;
  contributorUserIds?: string[];
}

function formatCommentTime(iso: string, locale: "zh" | "en") {
  const parsed = new Date(iso);
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

export function SpecimenCommentsPanel({
  specimenId,
  locale,
  ownerUserId,
  contributorUserIds = [],
}: SpecimenCommentsPanelProps) {
  const t = useTranslations("specimen.detail.comments");
  const [sort, setSort] = useState<CommentSort>("hot");
  const [items, setItems] = useState<CommentListItem[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [draft, setDraft] = useState("");
  const [topRoast, setTopRoast] = useState<CommentTopRoastResponse | null>(null);

  const [isReportDialogOpen, setIsReportDialogOpen] = useState(false);
  const [reportingCommentId, setReportingCommentId] = useState<string | null>(null);
  const [reportReason, setReportReason] = useState<CommentReportReasonCode>("SPAM");
  const [reportMessage, setReportMessage] = useState("");
  const [isReporting, setIsReporting] = useState(false);

  const contributorIdSet = useMemo(() => new Set(contributorUserIds), [contributorUserIds]);

  const loadComments = useCallback(async () => {
    setIsLoading(true);
    try {
      const page = await getSpecimenComments({ specimenId, sort, limit: 20 });
      setItems(page.items);
      setNextCursor(page.nextCursor);
      try {
        const roast = await getSpecimenTopRoast(specimenId);
        setTopRoast(roast);
      } catch {
        setTopRoast(null);
      }
    } catch (error) {
      console.error("Failed to load comments:", error);
      toast.error(t("toast.load_failed"));
    } finally {
      setIsLoading(false);
    }
  }, [sort, specimenId, t]);

  useEffect(() => {
    void loadComments();
  }, [loadComments]);

  const handleLoadMore = async () => {
    if (!nextCursor || isLoadingMore) {
      return;
    }
    setIsLoadingMore(true);
    try {
      const page = await getSpecimenComments({
        specimenId,
        sort,
        cursor: nextCursor,
        limit: 20,
      });
      setItems((current) => [...current, ...page.items]);
      setNextCursor(page.nextCursor);
    } catch (error) {
      console.error("Failed to load more comments:", error);
      toast.error(t("toast.load_more_failed"));
    } finally {
      setIsLoadingMore(false);
    }
  };

  const handlePublish = async () => {
    const content = draft.trim();
    if (!content) {
      toast.error(t("toast.content_required"));
      return;
    }

    setIsSubmitting(true);
    try {
      await publishComment({
        specimenId,
        contentMd: content,
      });
      setDraft("");
      await loadComments();
      toast.success(t("toast.publish_success"));
    } catch (error) {
      console.error("Failed to publish comment:", error);
      toast.error(t("toast.publish_failed"));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleResonate = async (commentId: string) => {
    try {
      const result = await resonateComment(commentId);
      setItems((current) =>
        current.map((item) =>
          item.commentId === commentId
            ? { ...item, resonanceCount: result.resonanceCount }
            : item
        )
      );
      if (topRoast?.commentId === commentId) {
        setTopRoast((current) =>
          current
            ? {
                ...current,
                resonanceCount: result.resonanceCount,
              }
            : current
        );
      }
      toast.success(
        result.resonated ? t("toast.resonance_success") : t("toast.resonance_canceled")
      );
    } catch (error) {
      console.error("Failed to resonate comment:", error);
      toast.error(t("toast.resonance_failed"));
    }
  };

  const openReportDialog = (commentId: string) => {
    setReportingCommentId(commentId);
    setReportReason("SPAM");
    setReportMessage("");
    setIsReportDialogOpen(true);
  };

  const submitReport = async () => {
    if (!reportingCommentId || isReporting) {
      return;
    }
    setIsReporting(true);
    try {
      await reportComment(reportingCommentId, {
        reasonCode: reportReason,
        message: reportMessage.trim() || undefined,
      });
      setIsReportDialogOpen(false);
      toast.success(t("toast.report_success"));
    } catch (error) {
      console.error("Failed to report comment:", error);
      toast.error(t("toast.report_failed"));
    } finally {
      setIsReporting(false);
    }
  };

  const getAuthorRole = (authorUserId: string) => {
    if (ownerUserId && authorUserId === ownerUserId) {
      return "OWNER";
    }
    if (contributorIdSet.has(authorUserId)) {
      return "CONTRIBUTOR";
    }
    return null;
  };

  const statusLabelMap: Record<string, string> = {
    ACTIVE: t("status.active"),
    PENDING_REVIEW: t("status.pending_review"),
    BLOCKED: t("status.blocked"),
    DELETED: t("status.deleted"),
  };

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-white/10 bg-zinc-950/80 p-4">
        <p className="mb-2 inline-flex items-center gap-2 text-xs font-semibold text-primary">
          <Sparkles className="h-3.5 w-3.5" />
          {t("top_roast_label")}
        </p>
        {topRoast?.hasTopRoast ? (
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-xs text-zinc-500">
              <span className="font-mono text-zinc-300">
                {topRoast.author?.username || t("anonymous")}
              </span>
              {topRoast.author?.userId && getAuthorRole(topRoast.author.userId) === "OWNER" && (
                <span className="rounded border border-primary/40 bg-primary/10 px-2 py-0.5 text-[10px] text-primary">
                  {t("badge.owner")}
                </span>
              )}
              {topRoast.author?.userId &&
                getAuthorRole(topRoast.author.userId) === "CONTRIBUTOR" && (
                  <span className="rounded border border-violet-300/40 bg-violet-400/10 px-2 py-0.5 text-[10px] text-violet-200">
                    {t("badge.contributor")}
                  </span>
                )}
            </div>
            <p className="text-sm text-zinc-300">{topRoast.contentPreview}</p>
            <p className="text-[11px] text-zinc-500">
              {t("resonance_count", { count: topRoast.resonanceCount || 0 })}
            </p>
          </div>
        ) : (
          <p className="text-sm text-zinc-500">{t("top_roast_empty")}</p>
        )}
      </div>

      <div className="rounded-xl border border-white/10 bg-zinc-950/80 p-4">
        <p className="mb-2 text-xs text-zinc-500">{t("composer_label")}</p>
        <textarea
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder={t("composer_placeholder")}
          maxLength={4000}
          className="h-24 w-full resize-y rounded-lg border border-white/10 bg-zinc-900/80 px-3 py-2 text-sm text-zinc-100 outline-none transition-colors placeholder:text-zinc-500 focus:border-primary/50"
        />
        <div className="mt-3 flex items-center justify-between">
          <p className="text-[11px] text-zinc-500">
            {t("composer_limit", { count: draft.length })}
          </p>
          <Button
            type="button"
            onClick={() => void handlePublish()}
            disabled={isSubmitting}
            className="h-9 bg-primary px-4 text-xs text-primary-foreground hover:bg-primary/90"
          >
            {isSubmitting ? (
              <span className="inline-flex items-center gap-1.5">
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                {t("composer_submitting")}
              </span>
            ) : (
              t("composer_submit")
            )}
          </Button>
        </div>
      </div>

      <div className="flex items-center justify-between gap-2">
        <p className="text-xs text-zinc-500">{t("sort_label")}</p>
        <div className="inline-flex items-center gap-2 rounded-lg border border-white/10 bg-zinc-950/70 p-1">
          <button
            type="button"
            onClick={() => setSort("hot")}
            className={cn(
              "rounded-md px-2.5 py-1 text-xs transition-colors",
              sort === "hot" ? "bg-primary/20 text-primary" : "text-zinc-400 hover:text-zinc-200"
            )}
          >
            {t("sort.hot")}
          </button>
          <button
            type="button"
            onClick={() => setSort("new")}
            className={cn(
              "rounded-md px-2.5 py-1 text-xs transition-colors",
              sort === "new" ? "bg-primary/20 text-primary" : "text-zinc-400 hover:text-zinc-200"
            )}
          >
            {t("sort.new")}
          </button>
        </div>
      </div>

      {isLoading ? (
        <div className="rounded-xl border border-dashed border-white/15 bg-zinc-950/70 p-4 text-sm text-zinc-500">
          {t("loading")}
        </div>
      ) : items.length === 0 ? (
        <div className="rounded-xl border border-dashed border-white/15 bg-zinc-950/70 p-4 text-sm text-zinc-500">
          {t("empty")}
        </div>
      ) : (
        <ul className="space-y-3">
          {items.map((item) => {
            const authorRole = getAuthorRole(item.authorUserId);
            return (
              <li key={item.commentId}>
                <article className="rounded-xl border border-white/10 bg-zinc-950/80 p-4">
                  <div className="mb-2 flex items-center justify-between gap-2">
                    <div className="flex min-w-0 items-center gap-2">
                      <span className="truncate rounded-md border border-white/10 px-2 py-0.5 font-mono text-[11px] text-zinc-300">
                        {item.authorUserId}
                      </span>
                      {authorRole === "OWNER" && (
                        <span className="rounded border border-primary/40 bg-primary/10 px-2 py-0.5 text-[10px] text-primary">
                          {t("badge.owner")}
                        </span>
                      )}
                      {authorRole === "CONTRIBUTOR" && (
                        <span className="rounded border border-violet-300/40 bg-violet-400/10 px-2 py-0.5 text-[10px] text-violet-200">
                          {t("badge.contributor")}
                        </span>
                      )}
                    </div>
                    <span className="text-[11px] text-zinc-500">{formatCommentTime(item.createdAt, locale)}</span>
                  </div>

                  <p className="text-sm leading-6 text-zinc-300">{item.contentPreview}</p>

                  <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
                    <span className="text-[11px] text-zinc-500">
                      {statusLabelMap[item.status] || item.status}
                    </span>
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => void handleResonate(item.commentId)}
                        className="inline-flex items-center gap-1 rounded border border-white/15 px-2 py-1 text-[11px] text-zinc-300 transition-colors hover:border-primary/40 hover:text-primary"
                      >
                        <MessageCircle className="h-3.5 w-3.5" />
                        {t("resonate")}
                        <span className="font-mono text-zinc-500">{item.resonanceCount}</span>
                      </button>
                      <button
                        type="button"
                        onClick={() => openReportDialog(item.commentId)}
                        className="inline-flex items-center gap-1 rounded border border-white/15 px-2 py-1 text-[11px] text-zinc-300 transition-colors hover:border-rose-300/40 hover:text-rose-300"
                      >
                        <Flag className="h-3.5 w-3.5" />
                        {t("report")}
                      </button>
                    </div>
                  </div>
                </article>
              </li>
            );
          })}
        </ul>
      )}

      {nextCursor && (
        <div className="flex justify-center">
          <Button
            type="button"
            variant="outline"
            onClick={() => void handleLoadMore()}
            disabled={isLoadingMore}
            className="h-9 border-white/15 bg-zinc-950/70 text-xs text-zinc-300 hover:bg-zinc-900"
          >
            {isLoadingMore ? (
              <span className="inline-flex items-center gap-1.5">
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                {t("loading_more")}
              </span>
            ) : (
              t("load_more")
            )}
          </Button>
        </div>
      )}

      <Dialog open={isReportDialogOpen} onOpenChange={setIsReportDialogOpen}>
        <DialogContent className="border-white/10 bg-zinc-900 text-zinc-100" closeLabel={t("report_dialog.close")}>
          <DialogHeader>
            <DialogTitle className="text-base text-zinc-100">{t("report_dialog.title")}</DialogTitle>
            <DialogDescription className="text-xs text-zinc-400">
              {t("report_dialog.description")}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-3">
            <label className="block space-y-1">
              <span className="text-xs text-zinc-500">{t("report_dialog.reason_label")}</span>
              <select
                value={reportReason}
                onChange={(event) =>
                  setReportReason(event.target.value as CommentReportReasonCode)
                }
                className="h-9 w-full rounded-md border border-white/10 bg-zinc-950 px-2 text-sm text-zinc-100 outline-none focus:border-primary/50"
              >
                {REPORT_REASONS.map((reason) => (
                  <option key={reason} value={reason}>
                    {t(`report_reason.${reason.toLowerCase()}`)}
                  </option>
                ))}
              </select>
            </label>

            <label className="block space-y-1">
              <span className="text-xs text-zinc-500">{t("report_dialog.message_label")}</span>
              <textarea
                value={reportMessage}
                onChange={(event) => setReportMessage(event.target.value)}
                placeholder={t("report_dialog.message_placeholder")}
                maxLength={1000}
                className="h-24 w-full resize-y rounded-md border border-white/10 bg-zinc-950 px-3 py-2 text-sm text-zinc-100 outline-none placeholder:text-zinc-500 focus:border-primary/50"
              />
            </label>
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              className="border-white/15 bg-zinc-950 text-zinc-300 hover:bg-zinc-900"
              onClick={() => setIsReportDialogOpen(false)}
            >
              {t("report_dialog.cancel")}
            </Button>
            <Button
              type="button"
              onClick={() => void submitReport()}
              disabled={isReporting}
              className="bg-rose-500/90 text-white hover:bg-rose-500"
            >
              {isReporting ? t("report_dialog.submitting") : t("report_dialog.submit")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
