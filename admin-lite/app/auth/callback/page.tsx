"use client";

import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { motion } from "motion/react";
import { CheckCircle2, XCircle, Loader2 } from "lucide-react";
import { ADMIN_OAUTH_STATE_STORAGE_KEY } from "@/lib/admin-oauth";

export default function AuthCallbackPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [status, setStatus] = useState<"loading" | "success" | "error">("loading");
  const [message, setMessage] = useState("");

  useEffect(() => {
    async function handleCallback() {
      try {
        const code = searchParams.get("code");
        const error = searchParams.get("error");
        const returnedState = searchParams.get("state");

        if (error) {
          setStatus("error");
          setMessage(
            error === "access_denied"
              ? "授权被拒绝 / Authorization denied"
              : `授权失败 / Authorization failed: ${error}`,
          );
          setTimeout(() => router.push("/login"), 3000);
          return;
        }

        if (!code) {
          setStatus("error");
          setMessage("缺少授权码 / Missing authorization code");
          setTimeout(() => router.push("/login"), 3000);
          return;
        }

        const expectedState = sessionStorage.getItem(ADMIN_OAUTH_STATE_STORAGE_KEY);
        if (!returnedState || !expectedState || returnedState !== expectedState) {
          setStatus("error");
          setMessage("授权状态校验失败 / Invalid OAuth state");
          setTimeout(() => router.push("/login"), 3000);
          return;
        }
        sessionStorage.removeItem(ADMIN_OAUTH_STATE_STORAGE_KEY);

        // 用授权码换取管理员 token
        const redirectUri = `${window.location.origin}/auth/callback`;
        const response = await fetch("/api/admin/session/exchange", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            mode: "oauth",
            code,
            state: returnedState,
            redirectUri,
          }),
        });

        if (!response.ok) {
          const data = await response.json().catch(() => ({}));
          throw new Error(data.message || "Token exchange failed");
        }

        setStatus("success");
        setMessage("登录成功，正在跳转... / Sign in successful, redirecting...");
        setTimeout(() => router.replace("/dashboard"), 1500);
      } catch (error) {
        setStatus("error");
        setMessage(
          error instanceof Error
            ? error.message
            : "登录失败，请重试 / Sign in failed, please try again",
        );
        setTimeout(() => router.push("/login"), 3000);
      }
    }

    handleCallback();
  }, [searchParams, router]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-indigo-50 via-white to-purple-50 px-4">
      <motion.div
        initial={{ opacity: 0, scale: 0.9 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.3 }}
        className="w-full max-w-md rounded-2xl border border-indigo-100 bg-white/80 p-8 text-center shadow-xl backdrop-blur-xl"
      >
        {status === "loading" && (
          <>
            <motion.div
              animate={{ rotate: 360 }}
              transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
              className="mx-auto mb-4 size-16"
            >
              <Loader2 className="size-16 text-indigo-600" />
            </motion.div>
            <h2 className="text-xl font-semibold text-indigo-950">
              正在处理授权... / Processing authorization...
            </h2>
          </>
        )}

        {status === "success" && (
          <>
            <motion.div
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              transition={{ type: "spring", stiffness: 200, damping: 15 }}
              className="mx-auto mb-4"
            >
              <CheckCircle2 className="size-16 text-emerald-600" />
            </motion.div>
            <h2 className="text-xl font-semibold text-emerald-700">{message}</h2>
          </>
        )}

        {status === "error" && (
          <>
            <motion.div
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              transition={{ type: "spring", stiffness: 200, damping: 15 }}
              className="mx-auto mb-4"
            >
              <XCircle className="size-16 text-rose-600" />
            </motion.div>
            <h2 className="text-xl font-semibold text-rose-700">{message}</h2>
            <p className="mt-2 text-sm text-slate-600">
              即将返回登录页... / Redirecting to login...
            </p>
          </>
        )}
      </motion.div>
    </div>
  );
}
