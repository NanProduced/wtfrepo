import {
  WalletDailyClaimResult,
  WalletLedgerPage,
  WalletView,
} from "@/shared/types/wallet";

function toSearchParams(params: Record<string, string | number | null | undefined>) {
  const searchParams = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === null || typeof value === "undefined") {
      continue;
    }
    searchParams.set(key, String(value));
  }
  return searchParams.toString();
}

export async function getWallet() {
  const res = await fetch("/api/wallet", {
    cache: "no-store",
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<WalletView>;
}

export async function getWalletLedger(params: {
  cursor?: string;
  limit?: number;
  reason?: string;
} = {}) {
  const suffix = toSearchParams({
    cursor: params.cursor,
    limit: params.limit,
    reason: params.reason,
  });
  const res = await fetch(`/api/wallet/ledger${suffix ? `?${suffix}` : ""}`, {
    cache: "no-store",
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<WalletLedgerPage>;
}

export async function claimWalletDaily() {
  const res = await fetch("/api/wallet/daily", {
    method: "POST",
    headers: {
      "X-Idempotency-Key": `idem-${crypto.randomUUID()}`,
    },
  });
  if (!res.ok) throw await res.json();
  return res.json() as Promise<WalletDailyClaimResult>;
}

