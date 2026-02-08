/**
 * Application Constants
 */

export const APP_NAME = "WTF-Repo" as const;
export const APP_DESCRIPTION = "Cyber Asylum for GitHub Repositories" as const;

export const CURRENCY_NAME = "Bug" as const;
export const CURRENCY_SYMBOL = "🐛" as const;

export const USER_HANDLE_PREFIX = "patient_" as const;

export const MODULES = {
  ARENA: "arena",
  AUTH: "auth",
  ECONOMY: "economy",
  SPECIMEN: "specimen",
  COMMENTS: "comments",
  NOTIFICATIONS: "notifications",
  ADMIN: "admin",
  ACHIEVEMENTS: "achievements",
  NARRATOR: "narrator",
} as const;

export const ROUTES = {
  HOME: "/",
  ARENA: "/",
  ARCHIVE: "/archive",
  SPECIMEN: (id: string) => `/specimen/${id}`,
  PROFILE: "/profile",
  ADMIN_LITE: "/admin-lite",
} as const;
