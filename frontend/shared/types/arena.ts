import { GitHubLanguage, SpecimenMetrics, SpecimenTag } from "@/shared/types/specimen";

export type ArenaVoteChoice = "LEFT" | "RIGHT" | "BOTH_BAD";

export interface ArenaSpecimenGitHubMeta {
  repoHtmlUrl: string;
  ownerLogin: string;
  ownerAvatarUrl: string;
  languages: GitHubLanguage[];
  stargazersCount: number;
  pushedAt: string;
}

export interface ArenaSpecimen {
  specimenId: string;
  repoFullName: string;
  oneLiner: string;
  readmePreview: string;
  githubMeta: ArenaSpecimenGitHubMeta;
  metrics: Pick<SpecimenMetrics, "elo" | "hype" | "votes">;
  tags: SpecimenTag[];
}

export interface ArenaDuelResponse {
  battleId: string;
  left: ArenaSpecimen;
  right: ArenaSpecimen;
  userBugBalance: number | null;
  participantCount?: number;
  round?: number;
}

export interface ArenaVotePayload {
  battleId: string;
  choice: ArenaVoteChoice;
  clientTs?: string;
  idempotencyKey?: string;
}

export interface ArenaVoteResponse {
  battleId: string;
  choice: ArenaVoteChoice;
  leftDelta: number;
  rightDelta: number;
  newBugBalance: number | null;
}
