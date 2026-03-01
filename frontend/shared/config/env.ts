/**
 * Environment Variables Configuration
 */

export const env = {
  API_URL: process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1",
  WS_URL: process.env.NEXT_PUBLIC_WS_URL || "/api/stream",
  IS_DEV: process.env.NODE_ENV === "development",
} as const;
