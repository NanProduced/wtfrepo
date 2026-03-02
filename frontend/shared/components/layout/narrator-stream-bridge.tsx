"use client";

import { useEffect } from "react";
import { useSession } from "next-auth/react";
import { env } from "@/shared/config/env";
import { buildNarratorTrigger } from "@/shared/config/narrator-events";
import { useNarratorStore } from "@/shared/store/narrator";
import { useNotificationStore } from "@/shared/store/notifications";

type JsonRecord = Record<string, unknown>;

function toNarratorKey(payload: JsonRecord): string | null {
  if (typeof payload.eventKey === "string" && payload.eventKey.startsWith("narrator.")) {
    return payload.eventKey;
  }

  if (typeof payload.eventType === "string" && payload.eventType.startsWith("narrator.")) {
    return payload.eventType;
  }

  if (payload.type === "NARRATOR_EVENT") {
    const source = typeof payload.source === "string" ? payload.source.toLowerCase() : "";
    const result = typeof payload.result === "string" ? payload.result.toLowerCase() : "";

    if (source.includes("arena")) {
      if (result === "win") return "narrator.arena.vote.win";
      if (result === "lose") return "narrator.arena.vote.lose";
      return "narrator.arena.vote.draw";
    }

    if (source.includes("achievement")) {
      return "narrator.achievement.unlocked";
    }

    if (source.includes("bet")) {
      if (result === "win") return "narrator.bet.settled.win";
      if (result === "lose") return "narrator.bet.settled.lose";
    }
  }

  return null;
}

function toNarratorContext(payload: JsonRecord) {
  const context: Record<string, string | number | boolean> = {};
  for (const [key, value] of Object.entries(payload)) {
    if (["string", "number", "boolean"].includes(typeof value)) {
      context[key] = value as string | number | boolean;
    }
  }
  return context;
}

export function NarratorStreamBridge() {
  const { status } = useSession();
  const triggerNarrator = useNarratorStore((state) => state.trigger);
  const ingestPagerEvent = useNotificationStore((state) => state.ingestPagerEvent);

  useEffect(() => {
    const channels = status === "authenticated" ? "narrator,pager" : "narrator";
    const stream = new EventSource(`${env.WS_URL}?channels=${channels}`);

    const handleNarratorPayload = (raw: string) => {
      try {
        const payload = JSON.parse(raw) as JsonRecord;
        const key = toNarratorKey(payload);
        if (!key) {
          return;
        }

        triggerNarrator(
          buildNarratorTrigger(key, {
            context: toNarratorContext(payload),
          })
        );
      } catch {
        // ignore malformed payload
      }
    };

    const onNarrator = (event: MessageEvent<string>) => {
      handleNarratorPayload(event.data);
    };

    const onPager = (event: MessageEvent<string>) => {
      try {
        const payload = JSON.parse(event.data) as JsonRecord;
        ingestPagerEvent({
          channel: typeof payload.channel === "string" ? payload.channel : undefined,
          type: typeof payload.type === "string" ? payload.type : undefined,
          notificationUid:
            typeof payload.notificationUid === "string" ? payload.notificationUid : undefined,
          title: typeof payload.title === "string" ? payload.title : undefined,
          body: typeof payload.body === "string" ? payload.body : undefined,
          targetUrl: typeof payload.targetUrl === "string" ? payload.targetUrl : undefined,
          actorNickname:
            typeof payload.actorNickname === "string" ? payload.actorNickname : undefined,
          aggregateCount:
            typeof payload.aggregateCount === "number" ? payload.aggregateCount : undefined,
          createdAt: typeof payload.createdAt === "string" ? payload.createdAt : undefined,
        });
      } catch {
        // ignore malformed payload for notifications
      }
      handleNarratorPayload(event.data);
    };

    stream.addEventListener("narrator", onNarrator as EventListener);
    stream.addEventListener("pager", onPager as EventListener);
    return () => {
      stream.removeEventListener("narrator", onNarrator as EventListener);
      stream.removeEventListener("pager", onPager as EventListener);
      stream.close();
    };
  }, [ingestPagerEvent, status, triggerNarrator]);

  return null;
}
