"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useSession, signOut } from "next-auth/react";
import { motion, AnimatePresence } from "framer-motion";
import { Bell, ChevronRight, Eye, EyeOff } from "lucide-react";
import { Button } from "@/shared/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/shared/components/ui/dropdown-menu";
import { Avatar, AvatarFallback, AvatarImage } from "@/shared/components/ui/avatar";
import { LoginModal } from "@/shared/components/auth/login-modal";
import { useNarratorPreferenceStore } from "@/shared/store/narrator-preference";
import { useNotificationStore } from "@/shared/store/notifications";
import { useWalletStore } from "@/shared/store/wallet";
import type { NotificationListItem } from "@/shared/types/notifications";
import { useLocale, useTranslations } from "next-intl";
import {
  isExternalNotificationUrl,
  toLocaleNotificationPath,
} from "@/shared/lib/notification-navigation";

function formatUnreadBadge(unreadCount: number) {
  if (unreadCount > 99) {
    return "99+";
  }
  return String(unreadCount);
}

export function Header() {
  const { data: session, status } = useSession();
  const router = useRouter();
  const locale = useLocale();
  const t = useTranslations("header");
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);
  const [isNotificationMenuOpen, setIsNotificationMenuOpen] = useState(false);

  const mode = useNarratorPreferenceStore((state) => state.preference.mode);
  const hydrateLocal = useNarratorPreferenceStore((state) => state.hydrateLocal);
  const syncFromServer = useNarratorPreferenceStore((state) => state.syncFromServer);
  const mergeOnLogin = useNarratorPreferenceStore((state) => state.mergeOnLogin);
  const cycleModeLocal = useNarratorPreferenceStore((state) => state.cycleModeLocal);
  const cycleModeRemote = useNarratorPreferenceStore((state) => state.cycleModeRemote);

  const notificationItems = useNotificationStore((state) => state.items);
  const unreadCount = useNotificationStore((state) => state.unreadCount);
  const isLoadingNotificationList = useNotificationStore((state) => state.isLoadingList);
  const isUpdatingNotification = useNotificationStore((state) => state.isSubmitting);
  const fetchNotificationUnreadCount = useNotificationStore((state) => state.fetchUnreadCount);
  const fetchNotificationRecent = useNotificationStore((state) => state.fetchRecent);
  const markNotificationRead = useNotificationStore((state) => state.markRead);
  const markAllNotificationsRead = useNotificationStore((state) => state.markAllRead);
  const resetNotifications = useNotificationStore((state) => state.reset);
  const wallet = useWalletStore((state) => state.wallet);
  const isLoadingWallet = useWalletStore((state) => state.isLoadingWallet);
  const fetchWallet = useWalletStore((state) => state.fetchWallet);
  const resetWallet = useWalletStore((state) => state.reset);

  const handleLogin = () => setIsLoginModalOpen(true);
  const handleLogout = () => signOut();

  const modeLabelByValue = useMemo(
    () =>
      ({
        FULL: t("god_eye.mode.full"),
        LITE: t("god_eye.mode.lite"),
        OFF: t("god_eye.mode.off"),
      }) as const,
    [t]
  );

  useEffect(() => {
    if (status === "authenticated") {
      void (async () => {
        await mergeOnLogin();
        await syncFromServer();
        await fetchNotificationUnreadCount();
        await fetchWallet();
      })();
      return;
    }

    if (status === "unauthenticated") {
      hydrateLocal();
      resetNotifications();
      resetWallet();
    }
  }, [
    fetchWallet,
    fetchNotificationUnreadCount,
    hydrateLocal,
    mergeOnLogin,
    resetNotifications,
    resetWallet,
    status,
    syncFromServer,
  ]);

  useEffect(() => {
    if (status !== "authenticated" || !isNotificationMenuOpen) {
      return;
    }
    void fetchNotificationRecent(8);
    void fetchNotificationUnreadCount();
  }, [
    fetchNotificationRecent,
    fetchNotificationUnreadCount,
    isNotificationMenuOpen,
    status,
  ]);

  const cycleGodEye = async () => {
    if (status === "authenticated") {
      await cycleModeRemote();
      return;
    }
    cycleModeLocal();
  };

  const handleNotificationClick = async (item: NotificationListItem) => {
    if (item.status === "UNREAD") {
      void markNotificationRead(item.notificationUid);
    }

    setIsNotificationMenuOpen(false);
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
    <>
      <header className="fixed top-0 left-0 right-0 z-50 flex h-16 items-center justify-between border-b border-white/5 bg-background/80 px-4 backdrop-blur-md md:px-6">
        <div className="flex items-center gap-6">
          <Link href={`/${locale}`} className="group flex cursor-pointer items-center gap-2">
            <div className="relative flex h-8 w-8 items-center justify-center overflow-hidden rounded-md bg-primary">
              <span className="font-mono text-lg font-bold text-background group-hover:animate-pulse">W</span>
              <div className="absolute inset-0 bg-accent opacity-0 mix-blend-difference transition-opacity duration-100 group-hover:opacity-50" />
            </div>
            <span className="hidden font-mono text-lg font-bold tracking-tight transition-colors group-hover:text-primary md:inline-block">
              WTF-Repo
            </span>
          </Link>

          <nav className="hidden items-center gap-1 md:flex">
            <Link
              href={`/${locale}`}
              className="px-4 py-2 font-mono text-xs tracking-widest text-zinc-400 transition-colors hover:text-primary"
            >
              {t("nav.arena")}
            </Link>
            <Link
              href={`/${locale}/archive`}
              className="px-4 py-2 font-mono text-xs tracking-widest text-zinc-400 transition-colors hover:text-primary"
            >
              {t("nav.archive")}
            </Link>
          </nav>
        </div>

        <div className="flex items-center gap-3 md:gap-4">
          <Button
            variant="ghost"
            size="icon"
            onClick={cycleGodEye}
            className="relative text-muted-foreground hover:bg-white/5 hover:text-primary"
            title={`${t("god_eye.label")}: ${modeLabelByValue[mode]}`}
          >
            <AnimatePresence mode="wait">
              {mode === "FULL" && (
                <motion.div key="full" initial={{ scale: 0 }} animate={{ scale: 1 }} exit={{ scale: 0 }}>
                  <Eye className="h-5 w-5 animate-pulse text-primary" />
                </motion.div>
              )}
              {mode === "LITE" && (
                <motion.div key="lite" initial={{ scale: 0 }} animate={{ scale: 1 }} exit={{ scale: 0 }}>
                  <Eye className="h-5 w-5 text-muted-foreground" />
                </motion.div>
              )}
              {mode === "OFF" && (
                <motion.div key="off" initial={{ scale: 0 }} animate={{ scale: 1 }} exit={{ scale: 0 }}>
                  <EyeOff className="h-5 w-5 text-muted-foreground/50" />
                </motion.div>
              )}
            </AnimatePresence>
          </Button>

          {status === "authenticated" && (
            <div className="hidden items-center gap-1.5 rounded-full border border-white/5 bg-zinc-900 px-3 py-1.5 md:flex">
              <span className="text-xs text-muted-foreground">{t("wallet.currency")}</span>
              <span className="font-mono font-bold text-accent">
                {isLoadingWallet
                  ? t("wallet.loading")
                  : typeof wallet?.balance === "number"
                    ? new Intl.NumberFormat(locale.startsWith("zh") ? "zh-CN" : "en-US").format(wallet.balance)
                    : "--"}
              </span>
            </div>
          )}

          {status === "authenticated" ? (
            <DropdownMenu open={isNotificationMenuOpen} onOpenChange={setIsNotificationMenuOpen}>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  className="relative text-muted-foreground hover:text-foreground"
                  title={t("notifications.trigger")}
                >
                  <Bell className="h-5 w-5" />
                  {unreadCount > 0 && (
                    <span className="absolute -right-1 -top-1 rounded-full border border-background bg-destructive px-1.5 font-mono text-[10px] leading-4 text-white">
                      {formatUnreadBadge(unreadCount)}
                    </span>
                  )}
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-80 border-zinc-800 bg-zinc-900 p-0 text-zinc-100">
                <div className="flex items-center justify-between border-b border-white/5 px-3 py-2">
                  <p className="text-sm font-semibold text-zinc-100">{t("notifications.title")}</p>
                  <button
                    type="button"
                    className="text-xs text-primary disabled:cursor-not-allowed disabled:text-zinc-500"
                    onClick={() => void markAllNotificationsRead()}
                    disabled={unreadCount === 0 || isUpdatingNotification}
                  >
                    {t("notifications.mark_all")}
                  </button>
                </div>

                <div className="max-h-80 overflow-y-auto p-2">
                  {isLoadingNotificationList ? (
                    <p className="rounded-md border border-dashed border-white/10 px-3 py-4 text-xs text-zinc-500">
                      {t("notifications.loading")}
                    </p>
                  ) : notificationItems.length === 0 ? (
                    <p className="rounded-md border border-dashed border-white/10 px-3 py-4 text-xs text-zinc-500">
                      {t("notifications.empty")}
                    </p>
                  ) : (
                    <ul className="space-y-2">
                      {notificationItems.map((item) => (
                        <li key={item.notificationUid}>
                          <button
                            type="button"
                            onClick={() => void handleNotificationClick(item)}
                            className="w-full rounded-md border border-white/10 bg-zinc-950/80 px-3 py-2 text-left transition-colors hover:border-primary/40"
                          >
                            <div className="flex items-start justify-between gap-2">
                              <p className="text-xs font-semibold leading-5 text-zinc-100">
                                {item.title}
                              </p>
                              {item.status === "UNREAD" && (
                                <span className="mt-1 h-1.5 w-1.5 shrink-0 rounded-full bg-primary" />
                              )}
                            </div>
                            {item.body && (
                              <p className="mt-1 line-clamp-2 text-xs leading-5 text-zinc-400">{item.body}</p>
                            )}
                            <p className="mt-1 text-[11px] text-zinc-500">
                              {new Intl.DateTimeFormat(locale.startsWith("zh") ? "zh-CN" : "en-US", {
                                month: "short",
                                day: "numeric",
                                hour: "2-digit",
                                minute: "2-digit",
                              }).format(new Date(item.createdAt))}
                            </p>
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
                <div className="border-t border-white/5 p-2">
                  <Link
                    href={`/${locale}/me/notifications`}
                    onClick={() => setIsNotificationMenuOpen(false)}
                    className="flex w-full items-center justify-between rounded-md border border-white/10 bg-zinc-950/70 px-3 py-2 text-xs text-zinc-300 transition-colors hover:border-primary/40 hover:text-primary"
                  >
                    <span>{t("notifications.view_all")}</span>
                    <ChevronRight className="h-3.5 w-3.5" />
                  </Link>
                </div>
              </DropdownMenuContent>
            </DropdownMenu>
          ) : (
            <Button
              variant="ghost"
              size="icon"
              className="relative text-muted-foreground hover:text-foreground"
              onClick={handleLogin}
              title={t("notifications.auth_required")}
            >
              <Bell className="h-5 w-5" />
            </Button>
          )}

          {status === "loading" ? (
            <div className="h-8 w-8 animate-pulse rounded-full bg-zinc-800" />
          ) : status === "authenticated" ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Avatar className="h-8 w-8 cursor-pointer border border-white/10 transition-colors hover:border-primary/50">
                  <AvatarImage src={session.user?.image || ""} />
                  <AvatarFallback className="bg-primary/20 font-mono text-xs text-primary">
                    {session.user?.name?.slice(0, 2)}
                  </AvatarFallback>
                </Avatar>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-56 border-zinc-800 bg-zinc-900 text-zinc-100">
                <DropdownMenuLabel className="font-mono">
                  <div className="text-xs text-muted-foreground">{t("menu.patient_id")}</div>
                  <div>{session.user?.name}</div>
                </DropdownMenuLabel>
                <DropdownMenuSeparator className="bg-white/5" />
                <DropdownMenuItem asChild className="cursor-pointer focus:bg-primary/20 focus:text-primary">
                  <Link href={`/${locale}/me`}>{t("menu.my_space")}</Link>
                </DropdownMenuItem>
                <DropdownMenuItem asChild className="cursor-pointer focus:bg-primary/20 focus:text-primary">
                  <Link href={`/${locale}/me/achievements`}>{t("menu.my_achievements")}</Link>
                </DropdownMenuItem>
                <DropdownMenuItem asChild className="cursor-pointer focus:bg-primary/20 focus:text-primary">
                  <Link href={`/${locale}/me/notifications`}>{t("menu.my_notifications")}</Link>
                </DropdownMenuItem>
                <DropdownMenuSeparator className="bg-white/5" />
                <DropdownMenuItem
                  className="cursor-pointer text-destructive focus:bg-destructive/20 focus:text-destructive"
                  onClick={handleLogout}
                >
                  {t("menu.logout")}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          ) : (
            <Button
              onClick={handleLogin}
              className="h-8 bg-primary font-mono text-xs text-primary-foreground hover:bg-primary/90"
            >
              {t("menu.login")}
            </Button>
          )}
        </div>
      </header>

      <LoginModal isOpen={isLoginModalOpen} onOpenChange={setIsLoginModalOpen} />
    </>
  );
}
