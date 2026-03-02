export interface WalletView {
  userId: string;
  balance: number;
  totalEarned: number;
  totalSpent: number;
  dailyClaimed: boolean;
  dailyAmount: number;
  voteCost: number;
}

export interface WalletLedgerItem {
  ledgerId: string;
  delta: number;
  balanceAfter: number;
  reason: string;
  refId: string;
  refType: string;
  createdAt: string;
}

export interface WalletLedgerPage {
  items: WalletLedgerItem[];
  nextCursor: string | null;
  hasMore: boolean;
}

export interface WalletDailyClaimResult {
  claimed: boolean;
  amount: number;
  balanceAfter: number;
  alreadyClaimed: boolean;
}

