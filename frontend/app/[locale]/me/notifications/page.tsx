import Link from "next/link";
import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { auth } from "@/shared/config/auth";
import { NotificationsCenter } from "@/modules/notifications/components/notifications-center";

interface MyNotificationsPageProps {
  params: Promise<{ locale: string }>;
}

export async function generateMetadata({
  params,
}: MyNotificationsPageProps): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "notifications.page.meta" });

  return {
    title: t("title"),
    description: t("description"),
  };
}

export default async function MyNotificationsPage({
  params,
}: MyNotificationsPageProps) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "notifications.page" });
  const session = await auth();

  if (!session?.backendAccessToken) {
    const callbackUrl = encodeURIComponent(`/${locale}/me/notifications`);
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

      <NotificationsCenter />
    </div>
  );
}
