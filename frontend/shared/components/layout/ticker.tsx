"use client";

import { useEffect, useMemo } from "react";
import { Activity } from "lucide-react";
import { postTickerItemClick } from "@/shared/api/narrator";
import { useTickerStore } from "@/shared/store/ticker";
import { useNarratorPreferenceStore } from "@/shared/store/narrator-preference";
import { env } from "@/shared/config/env";
import { useTranslations } from "next-intl";

export function Ticker() {
  const t = useTranslations("ticker");
  const items = useTickerStore((state) => state.items);
  const fetchRecent = useTickerStore((state) => state.fetchRecent);
  const appendRealtimeEvent = useTickerStore((state) => state.appendRealtimeEvent);
  const mode = useNarratorPreferenceStore((state) => state.preference.mode);
  const tickerEnabled = useNarratorPreferenceStore((state) => state.preference.tickerEnabled);
  const hydrated = useNarratorPreferenceStore((state) => state.hydrated);
  const hydrateLocal = useNarratorPreferenceStore((state) => state.hydrateLocal);

  useEffect(() => {
    if (!hydrated) {
      hydrateLocal();
    }
  }, [hydrateLocal, hydrated]);

  useEffect(() => {
    void fetchRecent(20);
  }, [fetchRecent]);

  useEffect(() => {
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
  }, [appendRealtimeEvent]);

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

  const handleItemClick = (item: (typeof items)[number]) => {
    if (!item.actionUrl) {
      return;
    }

    void postTickerItemClick({
      eventId: item.eventId,
      eventType: item.eventType,
      actionUrl: item.actionUrl,
    }).catch(() => undefined);

    window.location.assign(item.actionUrl);
  };

  if (mode === "OFF" || !tickerEnabled) {
    return null;
  }

  return (
    <footer className="fixed bottom-0 left-0 right-0 h-8 z-40 bg-black border-t border-primary/20 flex items-center overflow-hidden">
      {/* Label */}
      <div className="h-full px-3 bg-primary text-black font-mono text-xs font-bold flex items-center shrink-0 z-10">
        <Activity className="w-3 h-3 mr-1" />
        {t("label")}
      </div>

      {/* Marquee Content */}
      <div className="flex whitespace-nowrap animate-marquee hover:[animation-play-state:paused]">
        {/* Duplicate items for seamless loop */}
        {loopItems.map((item, i) => (
          <button
            key={`${item.eventId}-${i}`}
            type="button"
            onClick={() => handleItemClick(item)}
            className="inline-flex items-center px-4 cursor-pointer"
          >
            <span className="text-xs font-mono text-primary/80">
              {item.text}
            </span>
            <span className="mx-2 text-zinc-700 text-[10px]">{"///"}</span>
          </button>
        ))}
      </div>

      {/* Overlay Gradients for smooth fade */}
      <div className="absolute left-[50px] top-0 bottom-0 w-8 bg-gradient-to-r from-black to-transparent pointer-events-none z-10" />
      <div className="absolute right-0 top-0 bottom-0 w-8 bg-gradient-to-l from-black to-transparent pointer-events-none z-10" />
    </footer>
  );
}
