"use client";

import { useEffect } from "react";
import { Coins, Gift } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/shared/components/ui/button";
import { useWalletStore } from "@/shared/store/wallet";

interface WalletQuickPanelProps {
  locale: string;
  initialBalance?: number | null;
}

export function WalletQuickPanel({ locale, initialBalance = null }: WalletQuickPanelProps) {
  const t = useTranslations("me.page.wallet_block");
  const wallet = useWalletStore((state) => state.wallet);
  const isLoadingWallet = useWalletStore((state) => state.isLoadingWallet);
  const isSubmitting = useWalletStore((state) => state.isSubmitting);
  const fetchWallet = useWalletStore((state) => state.fetchWallet);
  const claimDaily = useWalletStore((state) => state.claimDaily);

  useEffect(() => {
    void fetchWallet();
  }, [fetchWallet]);

  const formattedBalance =
    typeof wallet?.balance === "number"
      ? new Intl.NumberFormat(locale.startsWith("zh") ? "zh-CN" : "en-US").format(wallet.balance)
      : typeof initialBalance === "number"
        ? new Intl.NumberFormat(locale.startsWith("zh") ? "zh-CN" : "en-US").format(initialBalance)
        : "-";

  const dailyClaimed = wallet?.dailyClaimed ?? false;
  const dailyAmount = wallet?.dailyAmount ?? 0;

  const handleClaimDaily = async () => {
    const result = await claimDaily();
    if (!result) {
      toast.error(t("toast.daily_failed"));
      return;
    }

    if (result.alreadyClaimed) {
      toast.message(t("toast.daily_already_claimed"));
      return;
    }

    toast.success(t("toast.daily_success", { amount: result.amount }));
  };

  return (
    <div className="mt-4 flex flex-wrap items-center gap-3 rounded-lg border border-white/10 bg-zinc-950/70 px-3 py-2 text-sm text-zinc-300">
      <span className="inline-flex items-center gap-2">
        <Coins className="h-4 w-4 text-amber-300" />
        {t("balance")}
        <span className="font-mono text-zinc-100">
          {isLoadingWallet ? "..." : formattedBalance}
        </span>
      </span>

      <div className="h-4 w-px bg-white/10" />

      <span className="text-xs text-zinc-500">
        {dailyClaimed
          ? t("daily_claimed")
          : t("daily_available", { amount: dailyAmount })}
      </span>

      <Button
        type="button"
        size="sm"
        variant="outline"
        className="h-7 border-white/15 bg-zinc-900 text-xs text-zinc-200 hover:bg-zinc-800"
        disabled={dailyClaimed || isSubmitting}
        onClick={() => void handleClaimDaily()}
      >
        <Gift className="mr-1.5 h-3.5 w-3.5" />
        {isSubmitting ? t("daily_claiming") : t("daily_claim")}
      </Button>
    </div>
  );
}

