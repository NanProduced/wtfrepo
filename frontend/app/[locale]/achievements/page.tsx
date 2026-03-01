import { redirect } from "next/navigation";

interface LegacyAchievementsPageProps {
  params: Promise<{ locale: string }>;
}

export default async function LegacyAchievementsPage({ params }: LegacyAchievementsPageProps) {
  const { locale } = await params;
  redirect(`/${locale}/me/achievements`);
}
