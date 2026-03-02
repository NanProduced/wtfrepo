export type BetDirection = "UP" | "FLAT" | "DOWN";

export type BetOrderStatus = "PENDING" | "WON" | "LOST" | "CANCELLED";

export type BetPoolStatus = "OPEN" | "CLOSED" | "SETTLED";
export type SettlementOutcome = BetDirection | "FORCE_SETTLED" | "UNKNOWN";
export type BetBlockReasonCode =
  | "AUTH_REQUIRED"
  | "VOTE_REQUIRED"
  | "IPO_LOCKED"
  | "POOL_NOT_AVAILABLE"
  | "POOL_NOT_OPEN"
  | "CUTOFF_PASSED";

export interface BetPlacePayload {
  specimenId: string;
  direction: BetDirection;
  amount: number;
}

export interface BetPlaceResult {
  orderId: string;
  specimenId: string;
  direction: BetDirection;
  amount: number;
  oddsAtPlace: number;
  settleDate: string;
  status: BetOrderStatus;
  walletBalanceAfter: number;
}

export interface BetActiveOrder {
  orderId: string;
  specimenId: string;
  specimenTitle: string;
  direction: BetDirection;
  amount: number;
  oddsAtPlace: number;
  currentOdds: number;
  status: BetOrderStatus;
}

export interface BetActiveView {
  date: string;
  orders: BetActiveOrder[];
  totalStaked: number;
}

export interface BetHistoryOrder {
  orderId: string;
  specimenId: string;
  specimenTitle: string;
  direction: BetDirection;
  amount: number;
  oddsAtPlace: number;
  status: BetOrderStatus;
  payout: number | null;
  moonDoomBonus: number;
  settleDate: string;
  settledAt: string | null;
}

export interface BetHistoryPage {
  orders: BetHistoryOrder[];
  nextCursor: string | null;
  hasMore: boolean;
}

export interface SettlementTodayOrder {
  orderId: string;
  direction: BetDirection;
  amount: number;
  status: BetOrderStatus;
  payout: number;
  moonDoomBonus: number;
}

export interface SettlementTodayItem {
  specimenId: string;
  specimenTitle: string;
  eloOpen: number;
  eloClose: number;
  deltaR: number;
  outcome: SettlementOutcome;
  isMoonDoom: boolean;
  myOrders: SettlementTodayOrder[];
}

export interface SettlementTodayPage {
  date: string;
  settlements: SettlementTodayItem[];
  nextCursor: string | null;
  hasMore: boolean;
}

export interface BetSummaryView {
  specimenId: string;
  date: string;
  poolUp: number;
  poolFlat: number;
  poolDown: number;
  houseUp: number;
  houseFlat: number;
  houseDown: number;
  oddsUp: number | null;
  oddsFlat: number | null;
  oddsDown: number | null;
  rakeRate: number;
  betCutoffAt: string | null;
  poolStatus: BetPoolStatus | null;
  totalBettors: number;
  houseActive: boolean;
  ipoStatus: string;
  currentElo: number;
  eloOpenToday: number;
  deltaRSoFar: number;
  correctionToday: number;
  moonDoomThreshold: number;
  canBet: boolean;
  betBlockReasonCode: BetBlockReasonCode | null;
  hasVotedForSpecimenToday: boolean;
}
