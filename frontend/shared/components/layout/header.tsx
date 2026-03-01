"use client";

import { useSession, signOut } from "next-auth/react";
import { Eye, EyeOff, Bell } from "lucide-react";
import { Button } from "@/shared/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuLabel,
  DropdownMenuSeparator
} from "@/shared/components/ui/dropdown-menu";
import { Avatar, AvatarFallback, AvatarImage } from "@/shared/components/ui/avatar";
import Link from "next/link";
import { useEffect, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { LoginModal } from "@/shared/components/auth/login-modal";
import { useNarratorPreferenceStore } from "@/shared/store/narrator-preference";
import { useLocale, useTranslations } from "next-intl";

export function Header() {
  const { data: session, status } = useSession();
  const locale = useLocale();
  const t = useTranslations("header");
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);
  const mode = useNarratorPreferenceStore((state) => state.preference.mode);
  const hydrateLocal = useNarratorPreferenceStore((state) => state.hydrateLocal);
  const syncFromServer = useNarratorPreferenceStore((state) => state.syncFromServer);
  const mergeOnLogin = useNarratorPreferenceStore((state) => state.mergeOnLogin);
  const cycleModeLocal = useNarratorPreferenceStore((state) => state.cycleModeLocal);
  const cycleModeRemote = useNarratorPreferenceStore((state) => state.cycleModeRemote);

  const handleLogin = () => setIsLoginModalOpen(true);
  const handleLogout = () => signOut();
  const modeLabelByValue = {
    FULL: t("god_eye.mode.full"),
    LITE: t("god_eye.mode.lite"),
    OFF: t("god_eye.mode.off"),
  } as const;

  useEffect(() => {
    if (status === "authenticated") {
      void (async () => {
        await mergeOnLogin();
        await syncFromServer();
      })();
      return;
    }

    if (status === "unauthenticated") {
      hydrateLocal();
    }
  }, [hydrateLocal, mergeOnLogin, status, syncFromServer]);

  // Cycle God Eye modes
  const cycleGodEye = async () => {
    if (status === "authenticated") {
      await cycleModeRemote();
      return;
    }

    cycleModeLocal();
  };

  return (
    <>
      <header className="fixed top-0 left-0 right-0 h-16 z-50 bg-background/80 backdrop-blur-md border-b border-white/5 flex items-center justify-between px-4 md:px-6">
        {/* Logo Area */}
        <div className="flex items-center gap-6">
          <Link href={`/${locale}`} className="flex items-center gap-2 group cursor-pointer">
            <div className="w-8 h-8 bg-primary rounded-md flex items-center justify-center overflow-hidden relative">
               <span className="font-mono font-bold text-background text-lg group-hover:animate-pulse">W</span>
               {/* Glitch Overlay */}
               <div className="absolute inset-0 bg-accent mix-blend-difference opacity-0 group-hover:opacity-50 transition-opacity duration-100" />
            </div>
            <span className="font-mono font-bold text-lg tracking-tight hidden md:inline-block group-hover:text-primary transition-colors">
              WTF-Repo
            </span>
          </Link>

          {/* Navigation */}
          <nav className="hidden md:flex items-center gap-1">
            <Link
              href={`/${locale}`}
              className="px-4 py-2 font-mono text-xs text-zinc-400 hover:text-primary transition-colors tracking-widest"
            >
              {t("nav.arena")}
            </Link>
            <Link
              href={`/${locale}/archive`}
              className="px-4 py-2 font-mono text-xs text-zinc-400 hover:text-primary transition-colors tracking-widest"
            >
              {t("nav.archive")}
            </Link>
          </nav>
        </div>

        {/* Right Actions */}
        <div className="flex items-center gap-3 md:gap-4">
          {/* God Eye Toggle */}
          <Button
            variant="ghost"
            size="icon"
            onClick={cycleGodEye}
            className="relative text-muted-foreground hover:text-primary hover:bg-white/5"
            title={`${t("god_eye.label")}: ${modeLabelByValue[mode]}`}
          >
            <AnimatePresence mode="wait">
              {mode === 'FULL' && (
                <motion.div key="full" initial={{ scale: 0 }} animate={{ scale: 1 }} exit={{ scale: 0 }}>
                  <Eye className="w-5 h-5 text-primary animate-pulse" />
                </motion.div>
              )}
              {mode === 'LITE' && (
                <motion.div key="lite" initial={{ scale: 0 }} animate={{ scale: 1 }} exit={{ scale: 0 }}>
                  <Eye className="w-5 h-5 text-muted-foreground" />
                </motion.div>
              )}
              {mode === 'OFF' && (
                <motion.div key="off" initial={{ scale: 0 }} animate={{ scale: 1 }} exit={{ scale: 0 }}>
                  <EyeOff className="w-5 h-5 text-muted-foreground/50" />
                </motion.div>
              )}
            </AnimatePresence>
          </Button>

          {/* Bug Balance (Slot Machine Placeholder) */}
          {status === "authenticated" && (
            <div className="hidden md:flex items-center gap-1.5 px-3 py-1.5 bg-zinc-900 rounded-full border border-white/5">
              <span className="text-xs text-muted-foreground">{t("wallet.currency")}</span>
              <span className="font-mono font-bold text-accent">0042</span>
            </div>
          )}

          {/* Pager (Notification) */}
          <Button variant="ghost" size="icon" className="relative text-muted-foreground hover:text-foreground">
            <Bell className="w-5 h-5" />
            <span className="absolute top-2 right-2 w-2 h-2 bg-destructive rounded-full animate-ping" />
            <span className="absolute top-2 right-2 w-2 h-2 bg-destructive rounded-full" />
          </Button>

          {/* User Menu */}
          {status === "loading" ? (
            <div className="w-8 h-8 rounded-full bg-zinc-800 animate-pulse" />
          ) : status === "authenticated" ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Avatar className="w-8 h-8 cursor-pointer border border-white/10 hover:border-primary/50 transition-colors">
                  <AvatarImage src={session.user?.image || ""} />
                  <AvatarFallback className="bg-primary/20 text-primary font-mono text-xs">
                    {session.user?.name?.slice(0, 2)}
                  </AvatarFallback>
                </Avatar>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-56 bg-zinc-900 border-zinc-800 text-zinc-100">
                <DropdownMenuLabel className="font-mono">
                  <div className="text-xs text-muted-foreground">{t("menu.patient_id")}</div>
                  <div>{session.user?.name}</div>
                </DropdownMenuLabel>
                <DropdownMenuSeparator className="bg-white/5" />
                <DropdownMenuItem asChild className="focus:bg-primary/20 focus:text-primary cursor-pointer">
                  <Link href={`/${locale}/me`}>{t("menu.my_space")}</Link>
                </DropdownMenuItem>
                <DropdownMenuItem asChild className="focus:bg-primary/20 focus:text-primary cursor-pointer">
                  <Link href={`/${locale}/me/achievements`}>{t("menu.my_achievements")}</Link>
                </DropdownMenuItem>
                <DropdownMenuSeparator className="bg-white/5" />
                <DropdownMenuItem
                  className="text-destructive focus:bg-destructive/20 focus:text-destructive cursor-pointer"
                  onClick={handleLogout}
                >
                  {t("menu.logout")}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          ) : (
            <Button
              onClick={handleLogin}
              className="font-mono text-xs bg-primary hover:bg-primary/90 text-primary-foreground h-8"
            >
              {t("menu.login")}
            </Button>
          )}
        </div>
      </header>

      {/* Login Modal */}
      <LoginModal isOpen={isLoginModalOpen} onOpenChange={setIsLoginModalOpen} />
    </>
  );
}
