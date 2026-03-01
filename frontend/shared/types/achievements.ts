export interface AchievementItem {
  achievementCode: string;
  displayName: string;
  description: string;
  iconUrl: string | null;
  tier: string;
  isSecret: boolean;
  isUnlocked: boolean;
  unlockedAt: string | null;
  rewardBug: number;
}

export interface AchievementsListResponse {
  items: AchievementItem[];
  nextCursor: string | null;
  hasMore: boolean;
}

export interface AchievementTierBreakdown {
  total: number;
  unlocked: number;
}

export interface LatestUnlock {
  achievementCode: string;
  displayName: string;
  unlockedAt: string | null;
}

export interface AchievementsSummaryResponse {
  totalAchievements: number;
  unlockedCount: number;
  unlockedPercentage: number;
  totalBugEarned: number;
  latestUnlock: LatestUnlock | null;
  tierBreakdown: Record<string, AchievementTierBreakdown>;
}

export interface AchievementDetailResponse {
  achievementCode: string;
  displayName: string;
  description: string;
  iconUrl: string | null;
  tier: string;
  rewardBug: number;
  totalUnlockedCount: number;
  totalUnlockedPercentage: number;
  isUnlocked: boolean;
  unlockedAt: string | null;
}
