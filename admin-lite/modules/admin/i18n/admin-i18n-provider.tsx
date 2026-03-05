"use client";

import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import {
  ADMIN_LOCALE_COOKIE,
  type AdminLocale,
  normalizeAdminLocale,
} from "@/modules/admin/i18n/locale";

interface AdminI18nContextValue {
  locale: AdminLocale;
  isZh: boolean;
  t: (zh: string, en: string) => string;
  setLocale: (nextLocale: AdminLocale) => void;
}

const AdminI18nContext = createContext<AdminI18nContextValue | null>(null);

export function AdminI18nProvider({
  initialLocale,
  children,
}: {
  initialLocale: AdminLocale;
  children: ReactNode;
}) {
  const router = useRouter();
  const [locale, setLocaleState] = useState<AdminLocale>(normalizeAdminLocale(initialLocale));

  const setLocale = useCallback(
    (nextLocale: AdminLocale) => {
      if (nextLocale === locale) {
        return;
      }
      setLocaleState(nextLocale);
      document.cookie = `${ADMIN_LOCALE_COOKIE}=${nextLocale}; Path=/; Max-Age=31536000; SameSite=Lax`;
      router.refresh();
    },
    [locale, router],
  );

  const value = useMemo<AdminI18nContextValue>(
    () => ({
      locale,
      isZh: locale === "zh",
      t: (zh, en) => (locale === "zh" ? zh : en),
      setLocale,
    }),
    [locale, setLocale],
  );

  return <AdminI18nContext.Provider value={value}>{children}</AdminI18nContext.Provider>;
}

export function useAdminI18n() {
  const value = useContext(AdminI18nContext);
  if (!value) {
    throw new Error("useAdminI18n must be used inside <AdminI18nProvider />.");
  }
  return value;
}
