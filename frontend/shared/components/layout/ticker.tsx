"use client";

import { useEffect, useMemo, useState } from "react";
import { Activity, BellDot, ChevronRight } from "lucide-react";
import { postTickerItemClick } from "@/shared/api/narrator";
import { useTickerStore } from "@/shared/store/ticker";
import { useNarratorPreferenceStore } from "@/shared/store/narrator-preference";
import { env } from "@/shared/config/env";
import { Button } from "@/shared/components/ui/button";
import { useRouter } from "next/navigation";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/shared/components/ui/sheet";
import { useLocale, useTranslations } from "next-intl";
import {
  isExternalNotificationUrl,
  toLocaleNotificationPath,
} from "@/shared/lib/notification-navigation";

export function Ticker() {
  const t = useTranslations("ticker");
  const locale = useLocale();
  const router = useRouter();
  const items = useTickerStore((state) => state.items);
  const fetchRecent = useTickerStore((state) => state.fetchRecent);
  const appendRealtimeEvent = useTickerStore((state) => state.appendRealtimeEvent);
  const mode = useNarratorPreferenceStore((state) => state.preference.mode);
  const tickerEnabled = useNarratorPreferenceStore((state) => state.preference.tickerEnabled);
  const hydrated = useNarratorPreferenceStore((state) => state.hydrated);
  const hydrateLocal = useNarratorPreferenceStore((state) => state.hydrateLocal);
  const [sheetOpen, setSheetOpen] = useState(false);
  const isTickerVisible = mode !== "OFF" && tickerEnabled;

  useEffect(() => {
    if (!hydrated) {
      hydrateLocal();
    }
  }, [hydrateLocal, hydrated]);

  useEffect(() => {
    if (!isTickerVisible) {
      return;
    }
    void fetchRecent(20);
  }, [fetchRecent, isTickerVisible]);

  useEffect(() => {
    if (!isTickerVisible) {
      return;
    }

    const source = new EventSource(`${env.WS_URL}?channels=ticker`);

    const handleTicker = (event: MessageEvent<string>) => {
      try {
        const payload = JSON.parse(event.data) as Record<string, unknown>;
        appendRealtimeEvent({
          eventId: typeof payload.eventId === "string" ? payload.eventId : undefined,
          eventType: typeof payload.eventType === "string" ? payload.eventType : "ticker.realtime",
          text:
            typeof payload.text === "string"
              ? payload.text
              : typeof payload.message === "string"
                ? payload.message
                : undefined,
          priority: typeof payload.priority === "string" ? payload.priority : undefined,
          actionUrl: typeof payload.actionUrl === "string" ? payload.actionUrl : null,
          occurredAt: typeof payload.occurredAt === "string" ? payload.occurredAt : undefined,
          expiresAt: typeof payload.expiresAt === "string" ? payload.expiresAt : undefined,
        });
      } catch {
        // Ignore malformed realtime ticker payloads.
      }
    };

    source.addEventListener("ticker", handleTicker as EventListener);
    return () => {
      source.removeEventListener("ticker", handleTicker as EventListener);
      source.close();
    };
  }, [appendRealtimeEvent, isTickerVisible]);

  const loopItems = useMemo(() => {
    if (items.length === 0) {
      return [
        {
          eventId: "fallback-empty",
          eventType: "ticker.empty",
          text: t("warming_up"),
          priority: "P2_AMBIENT",
          actionUrl: null,
          occurredAt: null,
          expiresAt: null,
        },
      ];
    }

    return [...items, ...items, ...items];
  }, [items, t]);

  const mobileRecentItems = useMemo(() => items.slice(0, 5), [items]);

  const dateTimeFormatter = useMemo(
    () =>
      new Intl.DateTimeFormat(locale.startsWith("zh") ? "zh-CN" : "en-US", {
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      }),
    [locale]
  );

  const formatOccurredAt = (value: string | null) => {
    if (!value) {
      return t("mobile.just_now");
    }

    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
      return t("mobile.just_now");
    }

    return dateTimeFormatter.format(parsed);
  };

  const handleItemClick = (item: (typeof items)[number]) => {
    if (!item.actionUrl) {
      return;
    }

    void postTickerItemClick({
      eventId: item.eventId,
      eventType: item.eventType,
      actionUrl: item.actionUrl,
    }).catch(() => undefined);

    const nextUrl = toLocaleNotificationPath(item.actionUrl, locale);
    if (isExternalNotificationUrl(nextUrl)) {
      window.location.assign(nextUrl);
      return;
    }

    router.push(nextUrl);
  };

  if (!isTickerVisible) {
    return null;
  }

  return (
    <>
      <footer className="fixed bottom-0 left-0 right-0 z-40 hidden h-8 items-center overflow-hidden border-t border-primary/20 bg-black md:flex">
        <div className="z-10 flex h-full shrink-0 items-center bg-primary px-3 font-mono text-xs font-bold text-black">
          <Activity className="mr-1 h-3 w-3" />
          {t("label")}
        </div>

        <div className="flex animate-marquee whitespace-nowrap hover:[animation-play-state:paused]">
          {loopItems.map((item, index) => (
            <button
              key={`${item.eventId}-${index}`}
              type="button"
              onClick={() => handleItemClick(item)}
              className="inline-flex cursor-pointer items-center px-4"
            >
              <span className="font-mono text-xs text-primary/80">{item.text}</span>
              <span className="mx-2 text-[10px] text-zinc-700">{"///"}</span>
            </button>
          ))}
        </div>

        <div className="pointer-events-none absolute bottom-0 left-[50px] top-0 z-10 w-8 bg-gradient-to-r from-black to-transparent" />
        <div className="pointer-events-none absolute bottom-0 right-0 top-0 z-10 w-8 bg-gradient-to-l from-black to-transparent" />
      </footer>

      <Sheet
        open={isTickerVisible && sheetOpen}
        onOpenChange={(open) => {
          if (!isTickerVisible && open) {
            return;
          }
          setSheetOpen(open);
        }}
      >
        <SheetTrigger asChild>
          <Button
            type="button"
            size="icon"
            className="fixed bottom-4 left-4 z-40 h-11 w-11 rounded-full border border-primary/30 bg-black/90 text-primary shadow-[0_0_0_1px_rgba(217,70,239,0.2)] hover:bg-black md:hidden"
            title={t("mobile.open")}
          >
            <BellDot className="h-5 w-5" />
            <span className="sr-only">{t("mobile.open")}</span>
          </Button>
        </SheetTrigger>
        <SheetContent
          side="bottom"
          closeLabel={t("mobile.close")}
          className="max-h-[75vh] border-t border-primary/20 bg-black/95 p-0 text-zinc-100 md:hidden"
        >
          <SheetHeader className="border-b border-white/10 pb-3">
            <SheetTitle className="flex items-center gap-2 font-mono text-sm text-primary">
              <Activity className="h-4 w-4" />
              {t("mobile.title")}
            </SheetTitle>
            <SheetDescription className="text-xs text-zinc-400">
              {t("mobile.subtitle")}
            </SheetDescription>
          </SheetHeader>
          <div className="max-h-[60vh] overflow-y-auto px-4 pb-6">
            {mobileRecentItems.length > 0 ? (
              <ul className="space-y-2">
                {mobileRecentItems.map((item) => {
                  const hasAction = Boolean(item.actionUrl);
                  return (
                    <li key={item.eventId}>
                      <button
                        type="button"
                        onClick={() => handleItemClick(item)}
                        disabled={!hasAction}
                        className="flex w-full items-start justify-between gap-2 rounded-lg border border-white/10 bg-zinc-950/80 px-3 py-2 text-left transition-colors enabled:hover:border-primary/40 enabled:hover:bg-zinc-950"
                      >
                        <div className="min-w-0">
                          <p className="text-xs leading-5 text-zinc-200">{item.text}</p>
                          <p className="mt-1 font-mono text-[11px] text-zinc-500">
                            {formatOccurredAt(item.occurredAt)}
                          </p>
                        </div>
                        {hasAction && <ChevronRight className="mt-1 h-4 w-4 shrink-0 text-zinc-500" />}
                      </button>
                    </li>
                  );
                })}
              </ul>
            ) : (
              <p className="rounded-lg border border-dashed border-white/10 bg-zinc-950/80 px-3 py-4 text-xs text-zinc-500">
                {t("mobile.empty")}
              </p>
            )}
          </div>
        </SheetContent>
      </Sheet>
    </>
  );
}
