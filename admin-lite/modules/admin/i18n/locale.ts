export const ADMIN_LOCALE_COOKIE = "wtf_admin_lite_locale";

export const ADMIN_LOCALES = ["zh", "en"] as const;

export type AdminLocale = (typeof ADMIN_LOCALES)[number];

export function normalizeAdminLocale(raw: string | null | undefined): AdminLocale {
  return raw === "en" ? "en" : "zh";
}

export function toLocaleTag(locale: AdminLocale): string {
  return locale === "zh" ? "zh-CN" : "en-US";
}
