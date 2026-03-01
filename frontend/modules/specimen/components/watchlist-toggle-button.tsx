"use client";

import { useState } from "react";
import { Eye, EyeOff } from "lucide-react";
import { Button } from "@/shared/components/ui/button";
import { addToWatchlist, removeFromWatchlist } from "@/shared/api/specimen";
import { cn } from "@/lib/utils";
import { toast } from "sonner";
import { useTranslations } from "next-intl";

interface WatchlistToggleButtonProps {
  specimenId: string;
  source?: string;
  className?: string;
}

/**
 * Watchlist toggle used on specimen detail page.
 * Uses optimistic UI and falls back to backend result when request fails.
 */
export function WatchlistToggleButton({
  specimenId,
  source = "DETAIL",
  className,
}: WatchlistToggleButtonProps) {
  const t = useTranslations("specimen.watchlist");
  const [isWatched, setIsWatched] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const handleToggle = async () => {
    if (isLoading) {
      return;
    }

    const nextWatched = !isWatched;
    setIsWatched(nextWatched);
    setIsLoading(true);

    try {
      if (nextWatched) {
        await addToWatchlist(specimenId, source);
        toast.success(t("toast.added"));
      } else {
        await removeFromWatchlist(specimenId);
        toast.success(t("toast.removed"));
      }
    } catch (error) {
      setIsWatched(!nextWatched);
      console.error("Failed to update watchlist:", error);
      toast.error(t("toast.failed"));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Button
      variant="outline"
      onClick={handleToggle}
      disabled={isLoading}
      className={cn(className, isWatched && "text-primary border-primary/60")}
    >
      {isWatched ? <EyeOff className="w-3 h-3 mr-2" /> : <Eye className="w-3 h-3 mr-2" />}
      {isWatched ? t("remove") : t("add")}
    </Button>
  );
}
