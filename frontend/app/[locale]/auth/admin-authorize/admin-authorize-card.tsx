"use client";

import { useMemo, useState } from "react";
import { ShieldCheck, AlertTriangle, Loader2 } from "lucide-react";
import { Button } from "@/shared/components/ui/button";

interface AdminAuthorizeCardProps {
  locale: "en" | "zh";
  redirectUri: string;
  state: string;
  displayName: string;
}

function buildErrorRedirect(redirectUri: string, state: string, reason: string) {
  const url = new URL(redirectUri);
  url.searchParams.set("error", reason);
  url.searchParams.set("state", state);
  return url.toString();
}

export function AdminAuthorizeCard({
  locale,
  redirectUri,
  state,
  displayName,
}: AdminAuthorizeCardProps) {
  const [loading, setLoading] = useState<"approve" | "deny" | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const t = useMemo(
    () =>
      locale === "zh"
        ? {
            title: "管理员访问授权",
            subtitle: "请确认是否允许当前账号登录 admin-lite 管理台。",
            brand: "WTF-Repo Lockroom",
            greeting: "当前账号",
            scopeTitle: "将授予以下权限",
            scope1: "登录 admin-lite 管理平台",
            scope2: "访问管理员专属运营功能",
            deny: "拒绝",
            approve: "确认授权",
            approving: "授权中...",
            denying: "处理中...",
            sessionExpired: "登录状态已过期，请重新登录主站后再授权。",
            failed: "授权失败，请稍后重试。",
          }
        : {
            title: "Admin Access Authorization",
            subtitle: "Confirm whether this account can sign in to admin-lite.",
            brand: "WTF-Repo Lockroom",
            greeting: "Signed in as",
            scopeTitle: "Permissions to grant",
            scope1: "Sign in to admin-lite",
            scope2: "Access privileged admin operations",
            deny: "Deny",
            approve: "Authorize",
            approving: "Authorizing...",
            denying: "Processing...",
            sessionExpired: "Session expired. Please sign in again on the main site.",
            failed: "Authorization failed. Please retry.",
          },
    [locale],
  );

  async function handleApprove() {
    setLoading("approve");
    setMessage(null);
    try {
      const response = await fetch("/api/admin/oauth/authorize", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ redirectUri, state }),
      });
      const payload = (await response.json().catch(() => ({}))) as {
        message?: string;
        redirectTo?: string;
      };
      if (response.status === 401) {
        setMessage(t.sessionExpired);
        setLoading(null);
        return;
      }

      const redirectTo = payload.redirectTo;
      if (typeof redirectTo === "string" && redirectTo.length > 0) {
        window.location.replace(redirectTo);
        return;
      }

      if (!response.ok) {
        throw new Error(payload.message || t.failed);
      }

      throw new Error(t.failed);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : t.failed);
      setLoading(null);
    }
  }

  function handleDeny() {
    setLoading("deny");
    window.location.replace(buildErrorRedirect(redirectUri, state, "access_denied"));
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-gradient-to-b from-neutral-950 via-neutral-900 to-neutral-950 px-4 py-8">
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(16,185,129,0.12),transparent_45%)]" />
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_bottom,rgba(59,130,246,0.12),transparent_45%)]" />

      <section className="relative z-10 w-full max-w-lg rounded-2xl border border-white/10 bg-black/45 p-6 shadow-2xl backdrop-blur-xl sm:p-8">
        <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-emerald-300/30 bg-emerald-300/10 px-3 py-1 text-xs text-emerald-200">
          <ShieldCheck className="size-3.5" />
          <span>{t.brand}</span>
        </div>

        <h1 className="text-2xl font-semibold tracking-tight text-white">{t.title}</h1>
        <p className="mt-2 text-sm text-neutral-300">{t.subtitle}</p>

        <div className="mt-6 rounded-xl border border-white/10 bg-white/5 p-4">
          <p className="text-xs text-neutral-400">{t.greeting}</p>
          <p className="mt-1 text-sm font-medium text-white">{displayName}</p>
        </div>

        <div className="mt-5 rounded-xl border border-white/10 bg-white/5 p-4">
          <p className="text-xs text-neutral-400">{t.scopeTitle}</p>
          <ul className="mt-2 space-y-1 text-sm text-neutral-200">
            <li>• {t.scope1}</li>
            <li>• {t.scope2}</li>
          </ul>
        </div>

        {message ? (
          <div className="mt-5 flex items-start gap-2 rounded-xl border border-amber-400/30 bg-amber-400/10 px-3 py-2 text-sm text-amber-200">
            <AlertTriangle className="mt-0.5 size-4 shrink-0" />
            <span>{message}</span>
          </div>
        ) : null}

        <div className="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Button
            type="button"
            variant="outline"
            onClick={handleDeny}
            disabled={loading !== null}
            className="h-11 border-white/20 bg-transparent text-white hover:bg-white/10"
          >
            {loading === "deny" ? (
              <>
                <Loader2 className="mr-2 size-4 animate-spin" />
                {t.denying}
              </>
            ) : (
              t.deny
            )}
          </Button>
          <Button
            type="button"
            onClick={handleApprove}
            disabled={loading !== null}
            className="h-11 bg-emerald-500 text-black hover:bg-emerald-400"
          >
            {loading === "approve" ? (
              <>
                <Loader2 className="mr-2 size-4 animate-spin" />
                {t.approving}
              </>
            ) : (
              t.approve
            )}
          </Button>
        </div>
      </section>
    </div>
  );
}
