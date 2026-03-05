import type { AdminLocale } from "@/modules/admin/i18n/locale";
import { toLocaleTag } from "@/modules/admin/i18n/locale";

export function formatTime(input: string | null | undefined, locale: AdminLocale = "zh"): string {
  if (!input) {
    return "-";
  }
  const date = new Date(input);
  if (Number.isNaN(date.getTime())) {
    return input;
  }
  return date.toLocaleString(toLocaleTag(locale), {
    hour12: false,
  });
}

export function shortText(value: string, max = 88) {
  if (value.length <= max) {
    return value;
  }
  return `${value.slice(0, max)}...`;
}
