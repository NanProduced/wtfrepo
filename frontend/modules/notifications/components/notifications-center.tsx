"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { BellDot, Loader2 } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { Button } from "@/shared/components/ui/button";
import { useNotificationStore } from "@/shared/store/notifications";
import type { NotificationListItem } from "@/shared/types/notifications";
import {
  isExternalNotificationUrl,
  toLocaleNotificationPath,
} from "@/shared/lib/notification-navigation";

type NotificationFilter = "ALL" | "UNREAD" | "READ";

const PAGE_SIZE = 20;

function formatCreatedAt(isoTime: string, locale: string) {
  const parsed = new Date(isoTime);
  if (Number.isNaN(parsed.getTime())) {
    return "-";
  }

  return new Intl.DateTimeFormat(locale.startsWith("zh") ? "zh-CN" : "en-US", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(parsed);
}

export function NotificationsCenter() {
  const router = useRouter();
  const locale = useLocale();
  const t = useTranslations("notifications.page");

  const [filter, setFilter] = useState<NotificationFilter>("ALL");

  const items = useNotificationStore((state) => state.items);
  const unreadCount = useNotificationStore((state) => state.unreadCount);
  const hasMore = useNotificationStore((state) => state.hasMore);
  const isLoadingList = useNotificationStore((state) => state.isLoadingList);
  const isSubmitting = useNotificationStore((state) => state.isSubmitting);
  const lastError = useNotificationStore((state) => state.lastError);
  const fetchUnreadCount = useNotificationStore((state) => state.fetchUnreadCount);
  const fetchRecent = useNotificationStore((state) => state.fetchRecent);
  const fetchMore = useNotificationStore((state) => state.fetchMore);
  const markRead = useNotificationStore((state) => state.markRead);
  const markAllRead = useNotificationStore((state) => state.markAllRead);

  const statusFilter = useMemo(() => {
    if (filter === "ALL") {
      return undefined;
    }
    return filter;
  }, [filter]);

  const visibleItems = useMemo(() => {
    if (filter === "ALL") {
      return items;
    }
    return items.filter((item) => item.status === filter);
  }, [filter, items]);

  const filterLabel = useMemo(
    () => ({
      ALL: t("filters.all"),
      UNREAD: t("filters.unread"),
      READ: t("filters.read"),
    }),
    [t]
  );

  useEffect(() => {
    void fetchUnreadCount();
    void fetchRecent(PAGE_SIZE, { reset: true, status: statusFilter });
  }, [fetchRecent, fetchUnreadCount, statusFilter]);

  const openNotification = async (item: NotificationListItem) => {
    if (item.status === "UNREAD") {
      await markRead(item.notificationUid);
    }

    const target = item.targetUrl || item.fallbackUrl;
    if (!target) {
      return;
    }

    const nextUrl = toLocaleNotificationPath(target, locale);
    if (isExternalNotificationUrl(nextUrl)) {
      window.location.assign(nextUrl);
      return;
    }
    router.push(nextUrl);
  };

  return (
    <section className="rounded-2xl border border-white/10 bg-zinc-900/70 p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-zinc-100">{t("title")}</h1>
          <p className="mt-1 text-sm text-zinc-400">{t("description")}</p>
        </div>
        <div className="inline-flex items-center gap-2 rounded-xl border border-white/10 bg-zinc-950/70 px-3 py-2 text-xs text-zinc-300">
          <BellDot className="h-4 w-4 text-primary" />
          {t("unread_count", { count: unreadCount })}
        </div>
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-2">
        {(["ALL", "UNREAD", "READ"] as const).map((option) => (
          <Button
            key={option}
            type="button"
            variant={filter === option ? "default" : "outline"}
            size="sm"
            className="h-8 text-xs"
            onClick={() => setFilter(option)}
          >
            {filterLabel[option]}
          </Button>
        ))}
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => void markAllRead()}
          disabled={unreadCount === 0 || isSubmitting}
          className="ml-auto h-8 text-xs"
        >
          {t("actions.mark_all")}
        </Button>
      </div>

      {lastError && (
        <p className="mt-4 rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-xs text-destructive">
          {t("state.error")}
        </p>
      )}

      {isLoadingList && visibleItems.length === 0 ? (
        <p className="mt-4 rounded-xl border border-dashed border-white/10 bg-zinc-950/60 px-4 py-6 text-sm text-zinc-500">
          {t("state.loading")}
        </p>
      ) : visibleItems.length === 0 ? (
        <p className="mt-4 rounded-xl border border-dashed border-white/10 bg-zinc-950/60 px-4 py-6 text-sm text-zinc-500">
          {t("state.empty")}
        </p>
      ) : (
        <ul className="mt-4 space-y-2">
          {visibleItems.map((item) => (
            <li key={item.notificationUid}>
              <button
                type="button"
                onClick={() => void openNotification(item)}
                className="w-full rounded-xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-left transition-colors hover:border-primary/40"
              >
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-sm font-semibold text-zinc-100">{item.title}</p>
                    {item.body && <p className="mt-1 text-sm leading-6 text-zinc-400">{item.body}</p>}
                  </div>
                  {item.status === "UNREAD" && (
                    <span className="mt-1 h-2 w-2 shrink-0 rounded-full bg-primary" />
                  )}
                </div>
                <p className="mt-2 text-xs text-zinc-500">{formatCreatedAt(item.createdAt, locale)}</p>
              </button>
            </li>
          ))}
        </ul>
      )}

      {hasMore && (
        <div className="mt-4 flex justify-center">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => void fetchMore(PAGE_SIZE)}
            disabled={isLoadingList}
            className="h-8 text-xs"
          >
            {isLoadingList ? (
              <>
                <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                {t("state.loading")}
              </>
            ) : (
              t("actions.load_more")
            )}
          </Button>
        </div>
      )}
    </section>
  );
}
