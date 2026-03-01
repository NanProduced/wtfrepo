"use client";

import { signIn } from "next-auth/react";
import { Button } from "@/shared/components/ui/button";
import { KeyRound } from "lucide-react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/shared/components/ui/dialog";
import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";

// Official Brand Icons (SVG)
const GitHubIcon = () => (
  <svg viewBox="0 0 24 24" className="w-5 h-5 mr-3 fill-current" aria-hidden="true">
    <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z" />
  </svg>
);

const GoogleIcon = () => (
  <svg viewBox="0 0 24 24" className="w-5 h-5 mr-3" aria-hidden="true">
    <path
      d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
      fill="#4285F4"
    />
    <path
      d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
      fill="#34A853"
    />
    <path
      d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
      fill="#FBBC05"
    />
    <path
      d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
      fill="#EA4335"
    />
  </svg>
);

interface LoginModalProps {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
}

export function LoginModal({ isOpen, onOpenChange }: LoginModalProps) {
  const [mockUser, setMockUser] = useState("");
  const locale = useLocale();
  const t = useTranslations("auth.modal");

  const handleLogin = (provider: string) => {
    signIn(provider, { callbackUrl: `/${locale}` });
  };

  const handleMockLogin = (e: React.FormEvent) => {
    e.preventDefault();
    signIn("credentials", { username: mockUser, callbackUrl: `/${locale}` });
  };

  return (
    <Dialog open={isOpen} onOpenChange={onOpenChange}>
      <DialogContent
        closeLabel={t("close")}
        className="sm:max-w-[425px] bg-zinc-950 border-zinc-800 text-zinc-100 p-0 overflow-hidden"
      >
        <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-transparent via-primary to-transparent opacity-50" />

        <div className="p-6">
          <DialogHeader className="mb-6">
            <DialogTitle className="text-2xl font-mono text-primary tracking-tighter">
              {t("title")}
            </DialogTitle>
            <DialogDescription className="text-zinc-400 font-mono text-xs">
              {t("subtitle")}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-3">
            {/* Official GitHub Button: Black background, white text */}
            <Button
              onClick={() => handleLogin("github")}
              className="w-full h-11 bg-[#24292e] hover:bg-[#2f363d] text-white border border-white/10 font-sans font-medium"
            >
              <GitHubIcon />
              {t("github_login")}
            </Button>

            {/* Official Google Button: White background, gray text, colored icon */}
            <Button
              onClick={() => handleLogin("google")}
              className="w-full h-11 bg-white text-zinc-600 hover:bg-zinc-100 hover:text-black border border-zinc-200 font-sans font-medium"
            >
              <GoogleIcon />
              {t("google_login")}
            </Button>
          </div>

          <div className="relative my-6">
            <div className="absolute inset-0 flex items-center">
              <span className="w-full border-t border-white/10" />
            </div>
            <div className="relative flex justify-center text-[10px] tracking-widest">
              <span className="bg-zinc-950 px-2 text-zinc-600 font-mono">
                {t("development_mode")}
              </span>
            </div>
          </div>

          {/* Mock Login Form */}
          <form onSubmit={handleMockLogin} className="flex gap-2">
            <input
              value={mockUser}
              onChange={(e) => setMockUser(e.target.value)}
              placeholder={t("mock_placeholder")}
              className="flex-1 bg-zinc-900/50 border border-white/10 rounded-md px-3 py-2 text-xs font-mono focus:outline-none focus:border-primary/50 text-zinc-300 placeholder:text-zinc-600"
            />
            <Button type="submit" variant="secondary" size="icon" className="shrink-0 bg-zinc-800 hover:bg-zinc-700 text-zinc-300">
              <KeyRound className="w-4 h-4" />
            </Button>
          </form>
        </div>

        <div className="bg-zinc-900/50 p-3 text-center border-t border-white/5">
          <p className="text-[10px] text-zinc-600 font-mono">
            {t("footer")}
          </p>
        </div>
      </DialogContent>
    </Dialog>
  );
}
