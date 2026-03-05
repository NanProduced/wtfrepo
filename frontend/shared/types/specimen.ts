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
  ownerLogin: string | null;
  ownerAvatarUrl: string | null;
  languages: GitHubLanguage[];
  stargazersCount: number;
  pushedAt: string | null;
  topics: string[];
  metadataSyncedAt?: string | null;
}

export interface GitHubMetaFull extends GitHubMetaBase {
  repoId: number | null;
  owner: {
    login: string | null;
    id: string | null;
    avatarUrl: string | null;
    htmlUrl: string | null;
  };
  description: string | null;
  homepage: string | null;
  defaultBranch: string | null;
  license?: {
    spdxId: string | null;
    name: string | null;
  } | null;
  visibility: string | null;
  archived: boolean;
  fork: boolean;
  createdAt: string | null;
  updatedAt: string | null;
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
  uiMeta?: SpecimenTagUiMeta;
}

export interface SpecimenTagUiMeta {
  color?: string;
  icon?: string;
  tooltip?: string;
  [key: string]: unknown;
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
  nextCursor: string | null;
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
  githubMeta: GitHubMetaBase & { description: string | null };
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
    excerpts: Array<{
      excerptType: string;
      text: string;
      translatedTextZh?: string;
      translationMeta?: {
        source?: "MANUAL" | "LLM" | string;
        translatedBy?: string;
        translatedAt?: string;
        [key: string]: unknown;
      };
    }>;
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
    owner: { githubLogin: string; githubUserId: string } | null;
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
  owner: SpecimenRepoIdentity | null;
  contributors: SpecimenRepoIdentity[];
  syncedAt: string;
}
