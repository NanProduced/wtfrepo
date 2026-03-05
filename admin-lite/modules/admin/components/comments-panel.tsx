"use client";

import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { listModerationQueue, moderateComment } from "@/modules/admin/lib/request";
import { formatTime, shortText } from "@/modules/admin/lib/format";
import { useAdminI18n } from "@/modules/admin/i18n/admin-i18n-provider";
import type { ModerationQueueItem } from "@/modules/admin/types";
import { SectionCard } from "@/modules/admin/components/panel-common";

export function CommentsPanel() {
  const { locale, t } = useAdminI18n();
  const actions = [
    { key: "approve", label: t("通过", "Approve") },
    { key: "block", label: t("屏蔽", "Block") },
    { key: "unblock", label: t("解除屏蔽", "Unblock") },
    { key: "delete", label: t("删除", "Delete") },
  ] as const;

  const [items, setItems] = useState<ModerationQueueItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pendingActionKey, setPendingActionKey] = useState<string | null>(null);

  const loadQueue = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await listModerationQueue(1, 30);
      setItems(result.items);
    } catch (unknownError) {
      setError(
        unknownError instanceof Error
          ? unknownError.message
          : t("加载队列失败。", "Failed to load moderation queue."),
      );
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    void loadQueue();
  }, [loadQueue]);

  async function handleAction(
    commentId: string,
    action: "block" | "unblock" | "approve" | "delete",
  ) {
    const actionKey = `${commentId}:${action}`;
    setPendingActionKey(actionKey);
    setError(null);
    try {
      await moderateComment(commentId, action);
      await loadQueue();
    } catch (unknownError) {
      setError(unknownError instanceof Error ? unknownError.message : t("操作失败。", "Operation failed."));
    } finally {
      setPendingActionKey(null);
    }
  }

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">{t("评论治理", "Comment moderation")}</h1>
          <p className="text-sm text-muted-foreground">
            {t(
              "对待审核和风险评论执行通过、屏蔽、解除屏蔽或删除。",
              "Moderate pending and risky comments with approve, block, unblock, or delete.",
            )}
          </p>
        </div>
        <Button variant="outline" onClick={() => void loadQueue()} disabled={loading}>
          {t("刷新队列", "Refresh queue")}
        </Button>
      </header>

      {error ? <p className="text-sm text-rose-700">{error}</p> : null}

      <SectionCard
        title={t("治理队列", "Moderation queue")}
        description={t(
          "与后端 /api/v1/admin/comments/moderation-queue 对齐。",
          "Aligned with backend /api/v1/admin/comments/moderation-queue.",
        )}
      >
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="text-muted-foreground">
              <tr>
                <th className="px-2 py-2 font-medium">{t("评论", "Comment")}</th>
                <th className="px-2 py-2 font-medium">{t("标本", "Specimen")}</th>
                <th className="px-2 py-2 font-medium">{t("状态", "Status")}</th>
                <th className="px-2 py-2 font-medium">{t("风险", "Risk")}</th>
                <th className="px-2 py-2 font-medium">{t("更新时间", "Updated at")}</th>
                <th className="px-2 py-2 font-medium">{t("操作", "Actions")}</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.commentId} className="border-t border-border/80 align-top">
                  <td className="px-2 py-2">
                    <p className="max-w-[320px] text-sm">{shortText(item.contentPreview, 80)}</p>
                    <p className="text-xs text-muted-foreground">
                      {t("作者", "Author")}: {item.authorUserId}
                    </p>
                  </td>
                  <td className="px-2 py-2">{item.specimenId}</td>
                  <td className="px-2 py-2">
                    <Badge variant={item.status === "PENDING_REVIEW" ? "warning" : "neutral"}>
                      {item.status}
                    </Badge>
                  </td>
                  <td className="px-2 py-2">
                    {item.moderationRiskLevel ? `${item.moderationRiskLevel} (${item.moderationReasonCode ?? "-"})` : "-"}
                  </td>
                  <td className="px-2 py-2">{formatTime(item.updatedAt, locale)}</td>
                  <td className="px-2 py-2">
                    <div className="flex flex-wrap gap-1">
                      {actions.map((action) => {
                        const actionKey = `${item.commentId}:${action.key}`;
                        return (
                          <Button
                            key={action.key}
                            size="xs"
                            variant={action.key === "delete" ? "destructive" : "outline"}
                            disabled={pendingActionKey === actionKey}
                            onClick={() => void handleAction(item.commentId, action.key)}
                          >
                            {action.label}
                          </Button>
                        );
                      })}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {items.length === 0 && !loading ? (
            <p className="px-2 py-4 text-sm text-muted-foreground">
              {t("当前没有待治理评论。", "No comments in moderation queue.")}
            </p>
          ) : null}
        </div>
      </SectionCard>
    </div>
  );
}
