export type NotificationStatus = "UNREAD" | "READ";

export interface NotificationListItem {
  notificationUid: string;
  type: string;
  title: string;
  body: string | null;
  actorNickname: string | null;
  actorAvatarUrl: string | null;
  aggregateCount: number;
  targetUrl: string | null;
  fallbackUrl: string | null;
  status: NotificationStatus | string;
  createdAt: string;
}

export interface NotificationListPage {
  items: NotificationListItem[];
  nextCursor: string | null;
  hasMore: boolean;
}

export interface NotificationUnreadCount {
  unreadCount: number;
}

export interface NotificationReadResult {
  notificationUid: string;
  status: NotificationStatus | string | null;
  readAt: string | null;
}

export interface NotificationReadAllResult {
  updatedCount: number;
}

export interface PagerRealtimePayload {
  channel?: string;
  type?: string;
  notificationUid?: string;
  title?: string;
  body?: string;
  targetUrl?: string;
  actorNickname?: string;
  aggregateCount?: number;
  createdAt?: string;
}
