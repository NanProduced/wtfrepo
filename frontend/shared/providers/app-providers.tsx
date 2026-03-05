// Placeholder for a layout that wraps the app with necessary providers
// Note: This file should replace/extend app/layout.tsx
// But I will create a separate provider file to keep layout clean.

import { SessionProvider } from "./session";

import { NextIntlClientProvider, type AbstractIntlMessages } from "next-intl";
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

function normalizeMessageKeys(
  input: AbstractIntlMessages | undefined
): AbstractIntlMessages | undefined {
  const normalizeNode = (value: unknown): unknown => {
    if (Array.isArray(value)) {
      return value.map((item) => normalizeNode(item));
    }

    if (!value || typeof value !== "object") {
      return value;
    }

    const source = value as Record<string, unknown>;
    const normalized: Record<string, unknown> = {};
    const dottedEntries: Array<[string, unknown]> = [];

    for (const [key, nestedValue] of Object.entries(source)) {
      const normalizedValue = normalizeNode(nestedValue);

      if (key.includes(".")) {
        dottedEntries.push([key, normalizedValue]);
        continue;
      }

      normalized[key] = normalizedValue;
    }

    for (const [flatKey, nestedValue] of dottedEntries) {
      setNestedValue(normalized, flatKey.split("."), nestedValue);
    }

    return normalized;
  };

  if (!input) {
    return input;
  }

  return normalizeNode(input) as AbstractIntlMessages;
}

export interface AppProvidersProps {
  children: React.ReactNode;
  session?: Session | null;
  messages?: AbstractIntlMessages;
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
