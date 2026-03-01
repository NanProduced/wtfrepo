// Placeholder for a layout that wraps the app with necessary providers
// Note: This file should replace/extend app/layout.tsx
// But I will create a separate provider file to keep layout clean.

import { SessionProvider } from "./session";

import { NextIntlClientProvider } from "next-intl";
import type { Session } from "next-auth";
import { Toaster } from "sonner";

function setNestedValue(
  target: Record<string, unknown>,
  path: string[],
  value: unknown
) {
  let cursor = target;

  for (let index = 0; index < path.length - 1; index++) {
    const segment = path[index];
    const currentValue = cursor[segment];

    if (
      !currentValue ||
      typeof currentValue !== "object" ||
      Array.isArray(currentValue)
    ) {
      cursor[segment] = {};
    }

    cursor = cursor[segment] as Record<string, unknown>;
  }

  cursor[path[path.length - 1]] = value;
}

function normalizeMessageKeys(input: unknown): unknown {
  if (Array.isArray(input)) {
    return input.map((item) => normalizeMessageKeys(item));
  }

  if (!input || typeof input !== "object") {
    return input;
  }

  const source = input as Record<string, unknown>;
  const normalized: Record<string, unknown> = {};
  const dottedEntries: Array<[string, unknown]> = [];

  for (const [key, value] of Object.entries(source)) {
    const normalizedValue = normalizeMessageKeys(value);

    if (key.includes(".")) {
      dottedEntries.push([key, normalizedValue]);
      continue;
    }

    normalized[key] = normalizedValue;
  }

  for (const [flatKey, value] of dottedEntries) {
    setNestedValue(normalized, flatKey.split("."), value);
  }

  return normalized;
}

export interface AppProvidersProps {
  children: React.ReactNode;
  session?: Session | null;
  messages?: Record<string, unknown>;
  locale?: string;
}

export function AppProviders({
  children,
  session,
  messages,
  locale
}: AppProvidersProps) {
  const normalizedMessages = normalizeMessageKeys(messages);

  return (
    <NextIntlClientProvider locale={locale} messages={normalizedMessages}>
      <SessionProvider session={session}>
        {/* <ThemeProvider attribute="class" defaultTheme="dark" forcedTheme="dark"> */}
        {children}
        <Toaster theme="dark" position="bottom-right" closeButton />
        {/* </ThemeProvider> */}
      </SessionProvider>
    </NextIntlClientProvider>
  );
}
