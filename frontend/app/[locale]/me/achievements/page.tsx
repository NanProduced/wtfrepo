import type { Metadata } from "next";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { redirect } from "next/navigation";
import { AchievementsOverview } from "@/modules/achievements/components/achievements-overview";
import { auth } from "@/shared/config/auth";
import { getTranslations } from "next-intl/server";

interface MyAchievementsPageProps {
  params: Promise<{ locale: string }>;
}

export async function generateMetadata({ params }: MyAchievementsPageProps): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "achievements.page.meta" });

  return {
    title: t("title"),
    description: t("description"),
  };
}

export default async function MyAchievementsPage({ params }: MyAchievementsPageProps) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "achievements.page" });
  const session = await auth();

  if (!session?.backendAccessToken) {
    const callbackUrl = encodeURIComponent(`/${locale}/me/achievements`);
    redirect(`/${locale}/auth/login?callbackUrl=${callbackUrl}`);
  }

  return (
    <div className="container mx-auto min-h-screen max-w-7xl px-4 pb-28 pt-24">
      <Link
        href={`/${locale}/me`}
        className="mb-4 inline-flex items-center gap-2 rounded-lg border border-white/10 px-3 py-2 text-xs text-zinc-400 hover:border-primary/40 hover:text-primary"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        {t("back")}
      </Link>

      <header className="mb-6">
        <p className="text-xs text-zinc-500">{t("badge")}</p>
        <h1 className="mt-1 text-3xl font-semibold tracking-tight text-zinc-100">{t("title")}</h1>
        <p className="mt-2 text-sm text-zinc-400">
          {t("description")}
        </p>
      </header>
      <AchievementsOverview />
    </div>
  );
}
