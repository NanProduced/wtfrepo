/**
 * Specimen Module Types
 * Based on SPECIMEN_CONTRACT_v0.1.md
 */

export type PublicStatus = "ACTIVE" | "OFFLINED";

export interface GitHubLanguage {
  name: string;
  bytes?: number;
  percentage: number;
}

export interface GitHubMetaBase {
  repoHtmlUrl: string;
  ownerLogin: string;
  ownerAvatarUrl: string;
  languages: GitHubLanguage[];
  stargazersCount: number;
  pushedAt: string;
  topics: string[];
  metadataSyncedAt?: string;
}

export interface GitHubMetaFull extends GitHubMetaBase {
  repoId: number;
  owner: {
    login: string;
    id: string;
    avatarUrl: string;
    htmlUrl: string;
  };
  description: string;
  homepage?: string;
  defaultBranch: string;
  license?: {
    spdxId: string;
    name: string;
  };
  visibility: string;
  archived: boolean;
  fork: boolean;
  createdAt: string;
  updatedAt: string;
  forksCount: number;
  openIssuesCount: number;
}

export interface SpecimenMetrics {
  elo: number;
  hype: number;
  votes: number;
  delta24h?: number;
  comments?: number;
}

export interface SpecimenTag {
  dimensionKey: string;
  tagKey: string;
  name: string;
  uiMeta?: Record<string, unknown>;
}

export interface SpecimenBase {
  specimenId: string;
  repoFullName: string;
}

// Archive Item
export interface ArchiveSpecimen extends SpecimenBase {
  oneLiner: string;
  githubMeta: GitHubMetaBase;
  metrics: SpecimenMetrics;
  tags: SpecimenTag[];
}

export type ArchiveLeaderboardMetric = "ELO" | "HYPE";

export interface ArchiveLeaderboardItem extends SpecimenBase {
  rank: number;
  rankDelta: number | null;
  score: number;
  metrics: SpecimenMetrics;
}

export interface ArchiveLeaderboardResponse {
  metric: ArchiveLeaderboardMetric;
  items: ArchiveLeaderboardItem[];
  nextCursor?: string;
  hasMore: boolean;
  total: number;
}

export interface ArchiveMomentumMover extends SpecimenBase {
  rank: number;
  previousRank: number;
  rankDelta: number;
  metrics: SpecimenMetrics;
}

export interface ArchiveInsightsData {
  summary: {
    totalSpecimens: number;
    totalVotes: number;
    todayArenaBattles: number;
    averageElo: number;
    averageHype: number;
    tradingDay: string;
  };
  momentum: {
    rising: number;
    unchanged: number;
    falling: number;
  };
  topRising: ArchiveMomentumMover[];
  topFalling: ArchiveMomentumMover[];
}

// Drawer Data
export interface SpecimenDrawerData {
  specimen: SpecimenBase & { githubUrl: string };
  githubMeta: GitHubMetaBase & { description: string };
  metrics: Omit<SpecimenMetrics, "delta24h" | "comments">;
  readmeExcerpt?: {
    excerptType: "FUNNY" | "SUMMARY" | "HIGHLIGHT";
    text: string;
  };
  tags: SpecimenTag[];
  topRoast?: {
    preview_text?: string;
    [key: string]: unknown;
  }; // [TODO-M05]
  canOpenInternalDetail: boolean;
  githubJumpWarning: {
    title: string;
    body: string;
  };
}

// Full Detail Data
export interface SpecimenDetailData {
  specimen: SpecimenBase & { publicStatus: PublicStatus };
  githubMeta: GitHubMetaFull;
  metrics: SpecimenMetrics;
  readme: {
    snapshotId: string;
    excerpts: Array<{ excerptType: string; text: string }>;
  };
  officialCommentary: {
    oneLiner: {
      zh: string;
      en: string;
    };
    arenaReason: {
      zh: string;
      en: string;
    };
  };
  codeHighlights: Array<{
    title: string;
    codeLanguage: string;
    snippet: string;
    explainText: string;
    priority?: number;
  }>;
  repoIdentity: {
    owner: { githubLogin: string; githubUserId: string };
    contributors: Array<{ githubLogin: string; githubUserId: string }>;
  };
  tags: SpecimenTag[];
  seoMeta: {
    title: string;
    description: string;
  };
  eloHistory?: Array<{
    at: string;
    elo: number;
    hype?: number;
    votes?: number;
  }>; // [DEFERRED-M01]
  similarSpecimens?: Array<Record<string, unknown>>; // [DEFERRED-P1]
}

// Tag Configuration
export interface TagDimension {
  dimensionKey: string;
  nameZh: string;
  nameEn: string;
  selectMode: "single" | "multi";
  required: boolean;
  sortOrder: number;
  uiMeta: {
    icon?: string;
    description?: string;
    styleToken?: string;
  };
  tags: TagDefinition[];
}

export interface TagDefinition {
  tagKey: string;
  nameZh: string;
  nameEn: string;
  uiMeta: {
    color?: string;
    icon?: string;
    badgeStyle?: string;
    tooltip?: string;
  };
}

export interface TagConfigResponse {
  configVersion: string;
  dimensions: TagDimension[];
}

export interface WatchlistItem {
  specimenId: string;
  repoFullName: string;
  metrics: { elo: number; hype: number };
  addedAt: string;
}

export interface SpecimenRepoIdentity {
  githubLogin: string;
  githubUserId: string;
}

export interface SpecimenRepoIdentitiesResponse {
  specimenId: string;
  owner: SpecimenRepoIdentity;
  contributors: SpecimenRepoIdentity[];
  syncedAt: string;
}
