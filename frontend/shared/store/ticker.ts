"use client";

import { create } from "zustand";
import { getTickerRecent } from "@/shared/api/narrator";
import { TickerRecentItem } from "@/shared/types/narrator";

const MAX_ITEMS = 60;

interface TickerStoreState {
  items: TickerRecentItem[];
  nextCursor: string | null;
  hasMore: boolean;
  isLoading: boolean;
  lastError: string | null;
  fetchRecent: (limit?: number) => Promise<void>;
  appendRealtimeEvent: (event: Partial<TickerRecentItem>) => void;
}

function normalizeRealtimeEvent(event: Partial<TickerRecentItem>): TickerRecentItem | null {
  if (!event.text || !event.eventType) {
    return null;
  }

  return {
    eventId: event.eventId ?? `rt-${Date.now()}`,
    eventType: event.eventType,
    text: event.text,
    priority: event.priority ?? "P2_AMBIENT",
    actionUrl: event.actionUrl ?? null,
    occurredAt: event.occurredAt ?? new Date().toISOString(),
    expiresAt: event.expiresAt ?? null,
  };
}

export const useTickerStore = create<TickerStoreState>((set) => ({
  items: [],
  nextCursor: null,
  hasMore: true,
  isLoading: false,
  lastError: null,

  fetchRecent: async (limit = 20) => {
    set((state) => ({ ...state, isLoading: true, lastError: null }));
    try {
      const page = await getTickerRecent({ limit });
      set((state) => ({
        ...state,
        items: page.items,
        nextCursor: page.nextCursor,
        hasMore: page.hasMore,
        isLoading: false,
      }));
    } catch (error) {
      set((state) => ({
        ...state,
        isLoading: false,
        lastError:
          typeof error === "object" && error !== null && "message" in error
            ? String((error as { message: string }).message)
            : "Failed to fetch ticker events.",
      }));
    }
  },

  appendRealtimeEvent: (event) => {
    const normalized = normalizeRealtimeEvent(event);
    if (!normalized) {
      return;
    }

    set((state) => {
      const deduped = state.items.filter((item) => item.eventId !== normalized.eventId);
      return {
        ...state,
        items: [normalized, ...deduped].slice(0, MAX_ITEMS),
      };
    });
  },
}));
