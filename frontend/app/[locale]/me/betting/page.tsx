import Link from "next/link";
import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { auth } from "@/shared/config/auth";
import { BettingOverviewPanel } from "@/modules/me/components/betting-overview-panel";

interface MyBettingPageProps {
  params: Promise<{ locale: string }>;
}

export async function generateMetadata({ params }: MyBettingPageProps): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "me.betting.meta" });

  return {
    title: t("title"),
    description: t("description"),
  };
}

export default async function MyBettingPage({ params }: MyBettingPageProps) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "me.betting" });
  const session = await auth();

  if (!session?.backendAccessToken) {
    const callbackUrl = encodeURIComponent(`/${locale}/me/betting`);
    redirect(`/${locale}/auth/login?callbackUrl=${callbackUrl}`);
  }

  return (
    <div className="container mx-auto min-h-screen max-w-5xl px-4 pb-28 pt-24">
      <Link
        href={`/${locale}/me`}
        className="mb-4 inline-flex items-center gap-1 text-xs text-zinc-400 transition-colors hover:text-primary"
      >
        <ChevronLeft className="h-3.5 w-3.5" />
        {t("back")}
      </Link>

      <header className="mb-6 rounded-2xl border border-white/10 bg-zinc-900/70 p-6">
        <p className="text-xs text-zinc-500">{t("badge")}</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight text-zinc-100">{t("title")}</h1>
        <p className="mt-2 text-sm text-zinc-400">{t("description")}</p>
      </header>

      <BettingOverviewPanel locale={locale} />
    </div>
  );
}
