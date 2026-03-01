import { redirect } from "next/navigation";

interface AchievementDetailPageProps {
  params: Promise<{ achievementCode: string; locale: string }>;
}

export default async function LegacyAchievementDetailPage({ params }: AchievementDetailPageProps) {
  const { achievementCode, locale } = await params;
  redirect(`/${locale}/me/achievements/${achievementCode}`);
}
