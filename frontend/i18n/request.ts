import { getRequestConfig } from "next-intl/server";

const SUPPORTED_LOCALES = new Set(["en", "zh"]);

export default getRequestConfig(async ({ locale, requestLocale }) => {
  const resolvedRequestLocale = await requestLocale;
  const localeCandidate = locale ?? resolvedRequestLocale;
  const normalizedLocale = typeof localeCandidate === "string" ? localeCandidate.toLowerCase() : "";
  const languageCode = normalizedLocale.split("-")[0];
  const baseLocale = SUPPORTED_LOCALES.has(languageCode) ? languageCode : "en";

  return {
    locale: baseLocale,
    messages: (await import(`../messages/${baseLocale}.json`)).default,
  };
});
