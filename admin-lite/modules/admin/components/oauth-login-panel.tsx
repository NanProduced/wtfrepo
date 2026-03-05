"use client";

import { useState } from "react";
import { AuthPage } from "@/components/auth-page";
import { AdminLocaleSwitch } from "@/modules/admin/components/admin-locale-switch";
import { useAdminI18n } from "@/modules/admin/i18n/admin-i18n-provider";
import { ADMIN_OAUTH_STATE_STORAGE_KEY, generateOAuthState } from "@/lib/admin-oauth";

const MAIN_SITE_URL = process.env.NEXT_PUBLIC_MAIN_SITE_URL || "http://localhost:3000";
const DEV_DIRECT_TOKEN_ENABLED =
  process.env.NODE_ENV !== "production" ||
  process.env.NEXT_PUBLIC_ADMIN_ALLOW_DIRECT_TOKEN === "true";

export function OAuthLoginPanel() {
  const { t } = useAdminI18n();
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  async function handleOAuthLogin() {
    setLoading(true);
    setNotice(null);

    try {
      const redirectUri = `${window.location.origin}/auth/callback`;
      const state = generateOAuthState();
      sessionStorage.setItem(ADMIN_OAUTH_STATE_STORAGE_KEY, state);

      const authUrl =
        `${MAIN_SITE_URL}/auth/admin-authorize` +
        `?redirect_uri=${encodeURIComponent(redirectUri)}` +
        `&state=${encodeURIComponent(state)}`;

      window.location.assign(authUrl);
    } catch {
      setNotice(t("跳转失败，请稍后重试。", "Redirect failed. Please try again."));
      setLoading(false);
    }
  }

  function handleDebugEntry() {
    window.location.assign("/login?mode=token");
  }

  return (
    <AuthPage
      title={t("管理员登录", "Admin Sign In")}
      subtitle={t(
        "使用主站账号授权后可进入 admin-lite 管理台。",
        "Authorize with your main site account to continue to admin-lite.",
      )}
      primaryActionLabel={t("使用主站账号登录", "Sign in with Main Site")}
      primaryActionLoadingLabel={t("跳转中...", "Redirecting...")}
      onPrimaryAction={handleOAuthLogin}
      loading={loading}
      notice={notice}
      homeUrl={MAIN_SITE_URL}
      homeLabel={t("返回主站", "Back to Main Site")}
      legalText={t("继续即表示你同意", "By continuing, you agree to our")}
      legalLinkText={t("服务条款", "Terms of Service")}
      legalLinkHref={`${MAIN_SITE_URL}/terms`}
      privacyLinkText={t("和隐私政策", "and Privacy Policy")}
      privacyLinkHref={`${MAIN_SITE_URL}/privacy`}
      secondaryActionLabel={DEV_DIRECT_TOKEN_ENABLED ? t("开发调试入口", "Debug Token Entry") : undefined}
      onSecondaryAction={DEV_DIRECT_TOKEN_ENABLED ? handleDebugEntry : undefined}
      localeSwitcher={<AdminLocaleSwitch />}
      sideEyebrow={t("管理入口", "Control Entry")}
      sideTitle="WTF-Repo Lockroom"
      sideDescription={t(
        "统一管理入口，支持审核、治理与运营操作。",
        "Unified admin access for moderation, curation, and operations.",
      )}
      sideQuote={t(
        "授权完成后，你可以直接进入管理台继续处理治理任务。",
        "After authorization, you can continue governance tasks in the admin dashboard.",
      )}
      sideQuoteAuthor="WTF-Repo"
    />
  );
}
