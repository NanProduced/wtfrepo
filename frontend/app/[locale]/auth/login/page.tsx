"use client";

import { signIn } from "next-auth/react";
import { useParams, useSearchParams } from "next/navigation";
import { Button } from "@/shared/components/ui/button";
import { Github, Chrome, KeyRound } from "lucide-react";
import { motion } from "framer-motion";
import { useTranslations } from "next-intl";

export default function LoginPage() {
  const params = useParams<{ locale?: string }>();
  const locale = typeof params?.locale === "string" ? params.locale : "en";
  const t = useTranslations("auth.page");
  const searchParams = useSearchParams();
  const callbackUrl = searchParams.get("callbackUrl") || `/${locale}`;
  const error = searchParams.get("error");

  const handleLogin = (provider: string) => {
    signIn(provider, { callbackUrl });
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 relative overflow-hidden">
      {/* Background Ambience */}
      <div className="absolute inset-0 bg-grid-white/[0.02] bg-[size:32px_32px]" />
      <div className="absolute inset-0 bg-gradient-to-b from-background via-transparent to-background" />

      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        className="w-full max-w-md bg-zinc-900/50 backdrop-blur-xl border border-white/10 p-8 rounded-2xl shadow-2xl relative z-10"
      >
        <div className="text-center mb-8">
          <h1 className="font-mono text-3xl font-bold text-primary mb-2 tracking-tighter">
            {t("title")}
          </h1>
          <p className="text-zinc-400 text-sm font-mono">
            {t("subtitle")}
          </p>
        </div>

        {error && (
          <div className="mb-6 p-3 bg-destructive/10 border border-destructive/20 rounded text-destructive text-xs font-mono text-center">
            {t("error_prefix")}: {error}
          </div>
        )}

        <div className="space-y-3">
          <Button
            onClick={() => handleLogin("github")}
            className="w-full h-12 bg-[#24292e] hover:bg-[#2f363d] text-white border border-white/5 font-mono"
          >
            <Github className="w-5 h-5 mr-3" />
            {t("github_login")}
          </Button>

          <Button
            onClick={() => handleLogin("google")}
            className="w-full h-12 bg-white text-black hover:bg-zinc-200 border border-transparent font-mono"
          >
            <Chrome className="w-5 h-5 mr-3" />
            {t("google_login")}
          </Button>

          <div className="relative my-6">
            <div className="absolute inset-0 flex items-center">
              <span className="w-full border-t border-white/10" />
            </div>
            <div className="relative flex justify-center text-xs">
              <span className="bg-zinc-900 px-2 text-zinc-500 font-mono">
                {t("development_only")}
              </span>
            </div>
          </div>

          <form
            action={async (formData) => {
              await signIn("credentials", {
                username: formData.get("username"),
                callbackUrl,
              });
            }}
            className="flex gap-2"
          >
            <input
              name="username"
              placeholder={t("mock_placeholder")}
              className="flex-1 bg-black/20 border border-white/10 rounded px-3 py-2 text-sm font-mono focus:outline-none focus:border-primary/50"
            />
            <Button type="submit" variant="outline" size="icon">
              <KeyRound className="w-4 h-4" />
            </Button>
          </form>
        </div>

        <div className="mt-8 text-center">
          <p className="text-[10px] text-zinc-600 font-mono">
            {t("footer_line1")}
            <br />
            {t("footer_line2")}
          </p>
        </div>
      </motion.div>
    </div>
  );
}
