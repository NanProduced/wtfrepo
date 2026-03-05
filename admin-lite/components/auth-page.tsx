"use client";

import type React from "react";
import { ChevronLeftIcon, KeyRound, Loader2, ShieldCheck } from "lucide-react";
import { FloatingPaths } from "@/components/floating-paths";
import { Logo } from "@/components/logo";
import { Button } from "@/components/ui/button";

interface AuthPageProps {
  title: string;
  subtitle: string;
  primaryActionLabel: string;
  primaryActionLoadingLabel: string;
  onPrimaryAction: () => void;
  loading?: boolean;
  notice?: string | null;
  homeUrl: string;
  homeLabel: string;
  legalText: string;
  legalLinkText: string;
  legalLinkHref: string;
  privacyLinkText: string;
  privacyLinkHref: string;
  secondaryActionLabel?: string;
  onSecondaryAction?: () => void;
  localeSwitcher?: React.ReactNode;
  sideEyebrow?: string;
  sideTitle?: string;
  sideDescription?: string;
  sideQuote?: string;
  sideQuoteAuthor?: string;
}

export function AuthPage({
  title,
  subtitle,
  primaryActionLabel,
  primaryActionLoadingLabel,
  onPrimaryAction,
  loading = false,
  notice,
  homeUrl,
  homeLabel,
  legalText,
  legalLinkText,
  legalLinkHref,
  privacyLinkText,
  privacyLinkHref,
  secondaryActionLabel,
  onSecondaryAction,
  localeSwitcher,
  sideEyebrow,
  sideTitle = "WTF-Repo Lockroom",
  sideDescription,
  sideQuote,
  sideQuoteAuthor = "WTF-Repo",
}: AuthPageProps) {
  return (
    <main className="relative md:h-screen md:overflow-hidden lg:grid lg:grid-cols-2">
      <div className="relative hidden h-full flex-col border-r bg-secondary p-10 lg:flex dark:bg-secondary/20">
        <div className="absolute inset-0 bg-linear-to-b from-transparent via-transparent to-background" />
        <Logo className="relative z-10 mr-auto h-4.5" />

        <div className="relative z-10 mt-auto space-y-4">
          {sideEyebrow ? (
            <p className="inline-flex w-fit rounded-full border border-border/70 bg-card/70 px-3 py-1 text-xs text-muted-foreground">
              {sideEyebrow}
            </p>
          ) : null}
          <div>
            <h2 className="font-semibold text-3xl tracking-tight">{sideTitle}</h2>
            {sideDescription ? (
              <p className="mt-3 max-w-md text-sm text-muted-foreground">{sideDescription}</p>
            ) : null}
          </div>
          {sideQuote ? (
            <blockquote className="space-y-2 border-l border-border/70 pl-4">
              <p className="text-base leading-relaxed">&ldquo;{sideQuote}&rdquo;</p>
              <footer className="font-mono text-sm text-muted-foreground">~ {sideQuoteAuthor}</footer>
            </blockquote>
          ) : null}
        </div>

        <div className="absolute inset-0">
          <FloatingPaths position={1} />
          <FloatingPaths position={-1} />
        </div>
      </div>

      <div className="relative flex min-h-screen flex-col justify-center px-8">
        <div aria-hidden className="absolute inset-0 isolate -z-10 opacity-60 contain-strict">
          <div className="absolute top-0 right-0 h-320 w-140 -translate-y-87.5 rounded-full bg-[radial-gradient(68.54%_68.72%_at_55.02%_31.46%,--theme(--color-foreground/.06)_0,hsla(0,0%,55%,.02)_50%,--theme(--color-foreground/.01)_80%)]" />
          <div className="absolute top-0 right-0 h-320 w-60 rounded-full bg-[radial-gradient(50%_50%_at_50%_50%,--theme(--color-foreground/.04)_0,--theme(--color-foreground/.01)_80%,transparent_100%)] [translate:5%_-50%]" />
          <div className="absolute top-0 right-0 h-320 w-60 -translate-y-87.5 rounded-full bg-[radial-gradient(50%_50%_at_50%_50%,--theme(--color-foreground/.04)_0,--theme(--color-foreground/.01)_80%,transparent_100%)]" />
        </div>

        <div className="absolute top-5 right-5">{localeSwitcher}</div>

        <Button asChild className="absolute top-5 left-5" variant="ghost">
          <a href={homeUrl}>
            <ChevronLeftIcon data-icon="inline-start" />
            {homeLabel}
          </a>
        </Button>

        <div className="mx-auto space-y-4 sm:w-sm">
          <Logo className="h-4.5 lg:hidden" />
          <div className="flex flex-col space-y-1">
            <h1 className="font-bold text-2xl tracking-wide">{title}</h1>
            <p className="text-base text-muted-foreground">{subtitle}</p>
          </div>

          {notice ? (
            <div
              className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive"
              role="alert"
              aria-live="polite"
            >
              {notice}
            </div>
          ) : null}

          <div className="space-y-2">
            <Button className="w-full" onClick={onPrimaryAction} type="button" disabled={loading}>
              {loading ? (
                <>
                  <Loader2 className="mr-1 size-4 animate-spin" />
                  {primaryActionLoadingLabel}
                </>
              ) : (
                <>
                  <ShieldCheck data-icon="inline-start" />
                  {primaryActionLabel}
                </>
              )}
            </Button>

            {secondaryActionLabel && onSecondaryAction ? (
              <Button
                className="w-full"
                variant="outline"
                onClick={onSecondaryAction}
                type="button"
                disabled={loading}
              >
                <KeyRound data-icon="inline-start" />
                {secondaryActionLabel}
              </Button>
            ) : null}
          </div>

          <div className="flex w-full items-center justify-center">
            <div className="h-px w-full bg-border" />
            <span className="px-2 text-muted-foreground text-xs uppercase tracking-wide">OAuth</span>
            <div className="h-px w-full bg-border" />
          </div>

          <p className="text-start text-muted-foreground text-xs">
            {legalText}{" "}
            <a className="underline underline-offset-4 hover:text-primary" href={legalLinkHref}>
              {legalLinkText}
            </a>{" "}
            <a className="underline underline-offset-4 hover:text-primary" href={privacyLinkHref}>
              {privacyLinkText}
            </a>
            .
          </p>
        </div>
      </div>
    </main>
  );
}
