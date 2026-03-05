"use client";

import { Button } from "@/components/ui/button";
import { useAdminI18n } from "@/modules/admin/i18n/admin-i18n-provider";

export function AdminLocaleSwitch() {
  const { locale, setLocale, t } = useAdminI18n();

  return (
    <div className="inline-flex items-center gap-1 rounded-md border border-border bg-background p-1">
      <Button
        size="xs"
        variant={locale === "zh" ? "default" : "ghost"}
        onClick={() => setLocale("zh")}
      >
        {t("中文", "Chinese")}
      </Button>
      <Button
        size="xs"
        variant={locale === "en" ? "default" : "ghost"}
        onClick={() => setLocale("en")}
      >
        {t("英文", "English")}
      </Button>
    </div>
  );
}
