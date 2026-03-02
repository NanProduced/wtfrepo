const LOCALE_PREFIX_PATTERN = /^\/(en|zh)(?=\/|$)/i;

const LEGACY_PATH_REWRITES = [
  { from: "/profile/achievements", to: "/me/achievements" },
  { from: "/profile", to: "/me" },
] as const;

function normalizeLeadingSlash(path: string) {
  if (!path) {
    return "/";
  }
  return path.startsWith("/") ? path : `/${path}`;
}

function splitPathAndSuffix(path: string) {
  const queryIndex = path.indexOf("?");
  const hashIndex = path.indexOf("#");
  const firstSpecialIndex =
    queryIndex === -1
      ? hashIndex
      : hashIndex === -1
        ? queryIndex
        : Math.min(queryIndex, hashIndex);

  if (firstSpecialIndex === -1) {
    return { pathname: path, suffix: "" };
  }

  return {
    pathname: path.slice(0, firstSpecialIndex) || "/",
    suffix: path.slice(firstSpecialIndex),
  };
}

function rewriteLegacyPath(pathname: string) {
  for (const rewrite of LEGACY_PATH_REWRITES) {
    if (pathname === rewrite.from || pathname.startsWith(`${rewrite.from}/`)) {
      const rest = pathname.slice(rewrite.from.length);
      return `${rewrite.to}${rest}`;
    }
  }
  return pathname;
}

export function isExternalNotificationUrl(url: string) {
  return /^https?:\/\//i.test(url);
}

export function normalizeNotificationPath(rawUrl: string) {
  if (isExternalNotificationUrl(rawUrl)) {
    return rawUrl;
  }

  const normalizedUrl = normalizeLeadingSlash(rawUrl);
  if (normalizedUrl.startsWith("/api/")) {
    return normalizedUrl;
  }

  const { pathname, suffix } = splitPathAndSuffix(normalizedUrl);
  const localeMatch = pathname.match(LOCALE_PREFIX_PATTERN);
  if (!localeMatch) {
    return `${rewriteLegacyPath(pathname)}${suffix}`;
  }

  const localePrefix = localeMatch[0];
  const remainder = pathname.slice(localePrefix.length) || "/";
  const mappedRemainder = rewriteLegacyPath(remainder);

  return `${localePrefix}${mappedRemainder === "/" ? "" : mappedRemainder}${suffix}`;
}

export function toLocaleNotificationPath(rawUrl: string, locale: string) {
  const normalized = normalizeNotificationPath(rawUrl);
  if (isExternalNotificationUrl(normalized) || normalized.startsWith("/api/")) {
    return normalized;
  }

  if (LOCALE_PREFIX_PATTERN.test(normalized)) {
    return normalized;
  }

  return `/${locale}${normalized}`;
}

export function deriveNotificationFallbackPath(rawUrl: string | null | undefined) {
  if (!rawUrl || isExternalNotificationUrl(rawUrl)) {
    return "/";
  }

  const normalized = normalizeNotificationPath(rawUrl);
  if (isExternalNotificationUrl(normalized)) {
    return "/";
  }

  return splitPathAndSuffix(normalized).pathname || "/";
}
