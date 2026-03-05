export interface PageMeta {
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
}

export interface AdminProfile {
  userId: string;
  username: string;
  roles: string[];
}

export interface SessionStatus {
  authenticated: boolean;
  profile?: AdminProfile;
}

export interface SafetyTicket {
  id: string;
  source: string | null;
  status: string | null;
  reporterId: string | null;
  targetType: string;
  targetId: string;
  reason: string;
  resolution: string | null;
  resolvedBy: string | null;
  resolvedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdminAlert {
  id: string;
  alertType: string | null;
  severity: string | null;
  targetType: string | null;
  targetId: string | null;
  message: string;
  emailSentTo: string[];
  emailSentAt: string | null;
  acknowledged: boolean;
  acknowledgedBy: string | null;
  createdAt: string;
}

export interface ModerationQueueItem {
  commentId: string;
  specimenId: string;
  authorUserId: string;
  contentPreview: string;
  status: string;
  moderationRiskLevel: string | null;
  moderationReasonCode: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AuditLogItem {
  id: string;
  operatorId: string;
  action: string;
  targetType: string;
  targetId: string;
  beforeSnapshot: unknown;
  afterSnapshot: unknown;
  metadata: unknown;
  requestId: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: string;
}

export interface MatchQualityReport {
  activeSpecimenCount: number;
  expectedPairCount: number;
  availablePairCount: number;
  pairCoverageComplete: boolean;
  pairCoverageRatio: number;
  resetExcludeThreshold: number;
  configuredProfileVersion: string;
  profileVersionAligned: boolean;
}

export interface BannedUser {
  id: string;
  userId: string;
  username: string;
  banType: string | null;
  reason: string;
  bannedBy: string;
  bannedAt: string;
  expiresAt: string | null;
  unbannedBy: string | null;
  unbannedAt: string | null;
  active: boolean;
}

export interface BroadcastItem {
  broadcastUid: string;
  title: string;
  body: string | null;
  targetUrl: string | null;
  status: string;
  totalRecipients: number;
  deliveredCount: number;
  createdBy: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface LanguageRatioItem {
  name: string;
  percentage: number | null;
}

export interface LanguageCandidateItem {
  name: string;
  bytes: number;
  percentage: number;
}

export interface ReadmeCandidateItem {
  candidateId: string;
  candidateType: string;
  heading: string | null;
  text: string;
  score: number | null;
  codeLanguage: string | null;
}

export interface RepoIdentityCandidateItem {
  githubLogin: string;
  githubUserId: string;
  githubAvatarUrl: string | null;
  githubHtmlUrl: string | null;
  contributions: number | null;
}

export interface ImportSpecimenResult {
  specimenId: string;
  status: string;
  fetchedMeta: {
    repoName: string;
    owner: string;
    languages: LanguageRatioItem[];
    readmeFetched: boolean;
  };
  readmeCandidates: ReadmeCandidateItem[];
  codeCandidates: ReadmeCandidateItem[];
  repoIdentityCandidates: {
    owner: RepoIdentityCandidateItem | null;
    contributors: RepoIdentityCandidateItem[];
  };
  languageCandidates: LanguageCandidateItem[];
}
