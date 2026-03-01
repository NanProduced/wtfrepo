"use client";

import { useSession } from "next-auth/react";
import { useEffect } from "react";
import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";

interface AuthGateProps {
  children: React.ReactNode;
  fallback?: React.ReactNode; // Optional custom fallback
}

/**
 * Gate Component (Client Side)
 * Wraps content that requires authentication.
 *
 * Usage:
 * <AuthGate>
 *   <VoteButton />
 * </AuthGate>
 */
export function AuthGate({ children, fallback }: AuthGateProps) {
  const { data: session, status } = useSession();
  const pathname = usePathname();
  const t = useTranslations("auth.gate");

  // Check if we have a valid backend token
  const hasBackendToken = !!session?.backendAccessToken;

  useEffect(() => {
    if (status === "unauthenticated") {
      console.log(`[AuthGate] Unauthenticated access attempt at ${pathname}`);
      // In a real app, we might trigger a login modal here
    }
  }, [status, pathname]);

  if (status === "loading") {
    return (
      <div className="flex items-center justify-center p-8 animate-pulse font-mono text-zinc-500">
        {t("verifying")}
      </div>
    );
  }

  if (!session || !hasBackendToken) {
    return fallback || (
      <div className="p-6 border border-primary/20 rounded-xl bg-primary/5 text-center space-y-4">
        <p className="font-mono text-primary/80">
          {t("required_title")}
        </p>
        <div className="text-xs text-zinc-500 font-mono">
          {t("required_body")}
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
