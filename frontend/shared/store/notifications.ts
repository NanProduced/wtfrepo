"use client";

import { create } from "zustand";
import {
  getNotificationUnreadCount,
  getNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from "@/shared/api/notifications";
import {
  NotificationListItem,
  NotificationStatus,
  PagerRealtimePayload,
} from "@/shared/types/notifications";
import { deriveNotificationFallbackPath } from "@/shared/lib/notification-navigation";

const MAX_NOTIFICATION_ITEMS_IN_MEMORY = 200;
type NotificationFilterStatus = "UNREAD" | "READ" | null;

function isUnread(status: string | null | undefined) {
  return status === "UNREAD";
}

function mergeItems(
  currentItems: NotificationListItem[],
  incomingItems: NotificationListItem[],
  options: { maxItems?: number } = {}
) {
  const maxItems = options.maxItems ?? MAX_NOTIFICATION_ITEMS_IN_MEMORY;
  const mergedMap = new Map<string, NotificationListItem>();
  for (const item of currentItems) {
    mergedMap.set(item.notificationUid, item);
  }
  for (const item of incomingItems) {
    mergedMap.set(item.notificationUid, item);
  }

  return [...mergedMap.values()]
    .sort((left, right) => {
      const leftTimestamp = new Date(left.createdAt).getTime();
      const rightTimestamp = new Date(right.createdAt).getTime();
      return rightTimestamp - leftTimestamp;
    })
    .slice(0, maxItems);
}

function toNotificationListItem(payload: PagerRealtimePayload): NotificationListItem | null {
  if (!payload.notificationUid || !payload.type || !payload.title) {
    return null;
  }

  return {
    notificationUid: payload.notificationUid,
    type: payload.type,
    title: payload.title,
    body: payload.body ?? null,
    actorNickname: payload.actorNickname ?? null,
    actorAvatarUrl: null,
    aggregateCount: typeof payload.aggregateCount === "number" ? payload.aggregateCount : 1,
    targetUrl: payload.targetUrl ?? null,
    fallbackUrl: deriveNotificationFallbackPath(payload.targetUrl),
    status: "UNREAD",
    createdAt: payload.createdAt ?? new Date().toISOString(),
  };
}

interface NotificationState {
  items: NotificationListItem[];
  unreadCount: number;
  nextCursor: string | null;
  hasMore: boolean;
  currentFilterStatus: NotificationFilterStatus;
  isLoadingList: boolean;
  isLoadingUnreadCount: boolean;
  isSubmitting: boolean;
  lastError: string | null;
  fetchUnreadCount: () => Promise<void>;
  fetchRecent: (
    limit?: number,
    options?: { reset?: boolean; status?: "UNREAD" | "READ" }
  ) => Promise<void>;
  fetchMore: (limit?: number) => Promise<void>;
  markRead: (notificationUid: string) => Promise<void>;
  markAllRead: () => Promise<void>;
  ingestPagerEvent: (payload: PagerRealtimePayload) => void;
  reset: () => void;
}

const initialState = {
  items: [],
  unreadCount: 0,
  nextCursor: null as string | null,
  hasMore: false,
  currentFilterStatus: null as NotificationFilterStatus,
  isLoadingList: false,
  isLoadingUnreadCount: false,
  isSubmitting: false,
  lastError: null as string | null,
};

export const useNotificationStore = create<NotificationState>((set, get) => ({
  ...initialState,

  fetchUnreadCount: async () => {
    set((state) => ({ ...state, isLoadingUnreadCount: true, lastError: null }));
    try {
      const result = await getNotificationUnreadCount();
      set((state) => ({
        ...state,
        unreadCount: result.unreadCount,
        isLoadingUnreadCount: false,
      }));
    } catch (error) {
      set((state) => ({
        ...state,
        isLoadingUnreadCount: false,
        lastError:
          typeof error === "object" && error !== null && "message" in error
            ? String((error as { message: string }).message)
            : "Failed to fetch unread count.",
      }));
    }
  },

  fetchRecent: async (limit = 8, options = {}) => {
    set((state) => ({ ...state, isLoadingList: true, lastError: null }));
    try {
      const page = await getNotifications({ limit, status: options.status });
      set((state) => ({
        ...state,
        items: mergeItems(options.reset ? [] : state.items, page.items),
        nextCursor: page.nextCursor,
        hasMore: page.hasMore,
        currentFilterStatus: options.status ?? null,
        isLoadingList: false,
      }));
    } catch (error) {
      set((state) => ({
        ...state,
        isLoadingList: false,
        lastError:
          typeof error === "object" && error !== null && "message" in error
            ? String((error as { message: string }).message)
            : "Failed to fetch notifications.",
      }));
    }
  },

  fetchMore: async (limit = 20) => {
    const { hasMore, nextCursor, currentFilterStatus } = get();
    if (!hasMore || !nextCursor) {
      return;
    }

    set((state) => ({ ...state, isLoadingList: true, lastError: null }));
    try {
      const page = await getNotifications({
        limit,
        cursor: nextCursor,
        status: currentFilterStatus ?? undefined,
      });
      set((state) => ({
        ...state,
        items: mergeItems(state.items, page.items),
        nextCursor: page.nextCursor,
        hasMore: page.hasMore,
        isLoadingList: false,
      }));
    } catch (error) {
      set((state) => ({
        ...state,
        isLoadingList: false,
        lastError:
          typeof error === "object" && error !== null && "message" in error
            ? String((error as { message: string }).message)
            : "Failed to load more notifications.",
      }));
    }
  },

  markRead: async (notificationUid) => {
    set((state) => ({ ...state, isSubmitting: true, lastError: null }));
    const previousItem = get().items.find((item) => item.notificationUid === notificationUid);
    try {
      const result = await markNotificationRead(notificationUid);
      const nextStatus = (result.status ?? "READ") as NotificationStatus | string;
      set((state) => {
        const nextItems = state.items.map((item) => {
          if (item.notificationUid !== notificationUid) {
            return item;
          }
          return { ...item, status: nextStatus };
        });

        const shouldDecrement = previousItem && isUnread(previousItem.status) && !isUnread(nextStatus);
        return {
          ...state,
          items: nextItems,
          unreadCount: shouldDecrement ? Math.max(0, state.unreadCount - 1) : state.unreadCount,
          isSubmitting: false,
        };
      });
    } catch (error) {
      set((state) => ({
        ...state,
        isSubmitting: false,
        lastError:
          typeof error === "object" && error !== null && "message" in error
            ? String((error as { message: string }).message)
            : "Failed to mark notification as read.",
      }));
    }
  },

  markAllRead: async () => {
    set((state) => ({ ...state, isSubmitting: true, lastError: null }));
    try {
      await markAllNotificationsRead();
      set((state) => ({
        ...state,
        items: state.items.map((item) => ({ ...item, status: "READ" })),
        unreadCount: 0,
        isSubmitting: false,
      }));
    } catch (error) {
      set((state) => ({
        ...state,
        isSubmitting: false,
        lastError:
          typeof error === "object" && error !== null && "message" in error
            ? String((error as { message: string }).message)
            : "Failed to mark all notifications as read.",
      }));
    }
  },

  ingestPagerEvent: (payload) => {
    const item = toNotificationListItem(payload);
    if (!item) {
      return;
    }

    set((state) => {
      const existing = state.items.find((current) => current.notificationUid === item.notificationUid);
      const shouldIncrementUnread = !existing || !isUnread(existing.status);
      return {
        ...state,
        items: mergeItems(state.items, [item]),
        unreadCount: shouldIncrementUnread ? state.unreadCount + 1 : state.unreadCount,
      };
    });
  },

  reset: () => {
    set({ ...initialState });
  },
}));
