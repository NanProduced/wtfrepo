export type ArenaVoteChoice = "LEFT" | "RIGHT" | "BOTH_BAD";

export interface ArenaSpecimen {
  specimenId: string;
  title: string;
  tagline: string;
  species: string;
  diagnosisTags: string[];
  elo: number;
  matchesPlayed: number;
  ipoStatus: string;
  thumbnailUrl: string | null;
}

export interface ArenaDuelMatchMeta {
  matchType: string;
  matchProfileVersion: string;
  isIpoMatch: boolean;
}

export interface ArenaDuelWallet {
  balance: number;
  voteCost: number;
}

export interface ArenaDuelResponse {
  battleId: string;
  left: ArenaSpecimen;
  right: ArenaSpecimen;
  matchMeta: ArenaDuelMatchMeta;
  shouldResetExcludeSet: boolean;
  wallet: ArenaDuelWallet | null;
}

export interface ArenaVotePayload {
  battleId: string;
  winner: ArenaVoteChoice;
  idempotencyKey?: string;
}

export interface ArenaVoteResponse {
  battleId: string;
  winner: ArenaVoteChoice;
  leftDelta: number;
  rightDelta: number;
  leftEloAfter: number;
  rightEloAfter: number;
  leftPhase: string;
  rightPhase: string;
  bugCost: number;
  walletBalanceAfter: number;
}
