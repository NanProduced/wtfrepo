import {
  NotificationListPage,
  NotificationReadAllResult,
  NotificationReadResult,
  NotificationUnreadCount,
} from "@/shared/types/notifications";

function createIdempotencyKey() {
  return `idem-${crypto.randomUUID()}`;
}

export async function getNotifications(params: {
  status?: "UNREAD" | "READ";
  type?: string;
  cursor?: string;
  limit?: number;
} = {}) {
  const searchParams = new URLSearchParams();
  if (params.status) searchParams.set("status", params.status);
  if (params.type) searchParams.set("type", params.type);
  if (params.cursor) searchParams.set("cursor", params.cursor);
  if (typeof params.limit === "number") searchParams.set("limit", String(params.limit));

  const suffix = searchParams.toString();
  const res = await fetch(`/api/notifications${suffix ? `?${suffix}` : ""}`, {
    cache: "no-store",
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<NotificationListPage>;
}

export async function getNotificationUnreadCount() {
  const res = await fetch("/api/notifications/unread-count", {
    cache: "no-store",
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<NotificationUnreadCount>;
}

export async function markNotificationRead(notificationUid: string) {
  const res = await fetch(`/api/notifications/${notificationUid}/read`, {
    method: "PATCH",
    headers: {
      "X-Idempotency-Key": createIdempotencyKey(),
    },
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<NotificationReadResult>;
}

export async function markAllNotificationsRead() {
  const res = await fetch("/api/notifications/read-all", {
    method: "PATCH",
    headers: {
      "X-Idempotency-Key": createIdempotencyKey(),
    },
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<NotificationReadAllResult>;
}
