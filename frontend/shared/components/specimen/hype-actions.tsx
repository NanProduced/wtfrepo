"use client";

import { useState } from "react";
import { Button } from "@/shared/components/ui/button";
import { submitHype } from "@/shared/api/specimen";
import { useSession } from "next-auth/react";
import { toast } from "sonner"; // Assuming sonner is used or can be added
import { Flame, Laugh, Ghost } from "lucide-react";
import { cn } from "@/lib/utils";
import { motion, AnimatePresence } from "framer-motion";
import { useTranslations } from "next-intl";

interface HypeActionsProps {
  specimenId: string;
  initialScore?: number;
  className?: string;
}

const HYPE_DIMENSIONS = [
  { key: "FUNNY", icon: Laugh, labelKey: "dimensions.funny", color: "text-yellow-400" },
  { key: "INSANE", icon: Ghost, labelKey: "dimensions.insane", color: "text-purple-400" },
  { key: "GENIUS", icon: Flame, labelKey: "dimensions.genius", color: "text-orange-400" },
];

/**
 * HypeActions
 * Emotional feedback buttons for a Specimen.
 * Supports rapid clicking with weighted decay (handled by backend).
 */
export function HypeActions({ specimenId, initialScore = 0, className }: HypeActionsProps) {
  const t = useTranslations("specimen.hype");
  const { status } = useSession();
  const [score, setScore] = useState(initialScore);
  const [isHypeing, setIsHypeing] = useState<string | null>(null);

  const handleHype = async (dimension: string) => {
    if (status !== "authenticated") {
      toast.error(t("auth_required_title"), {
        description: t("auth_required_description"),
      });
      return;
    }

    setIsHypeing(dimension);

    // Optimistic UI (Simple increment for visual feedback)
    setScore((prev) => prev + 0.1);

    try {
      const result = await submitHype(specimenId, dimension);
      // Synchronize with real backend value if provided
      if (result.hypeScore) {
        setScore(result.hypeScore);
      }
    } catch (error: unknown) {
      console.error("Hype failed:", error);
      const maybeStatus =
        typeof error === "object" && error !== null && "status" in error
          ? (error as { status?: number }).status
          : undefined;
      // Revert optimism if it's a hard error (not just rate limit)
      if (maybeStatus !== 429) {
        setScore((prev) => Math.max(0, prev - 0.1));
      }
    } finally {
      setTimeout(() => setIsHypeing(null), 300);
    }
  };

  return (
    <div className={cn("flex flex-col gap-4", className)}>
      <div className="flex items-center justify-between">
        <span className="font-mono text-[10px] text-zinc-500 tracking-widest">
          {t("title")}
        </span>
        <div className="flex items-center gap-1">
          <Flame className="w-3 h-3 text-primary animate-pulse" />
          <span className="font-mono text-sm font-bold text-primary">
            {score.toFixed(1)}
          </span>
        </div>
      </div>

      <div className="flex gap-2">
        {HYPE_DIMENSIONS.map((dim) => {
          const Icon = dim.icon;
          const isActive = isHypeing === dim.key;

          return (
            <Button
              key={dim.key}
              variant="outline"
              size="sm"
              onClick={() => handleHype(dim.key)}
              disabled={isHypeing !== null && isHypeing !== dim.key}
              className={cn(
                "flex-1 h-10 font-mono text-[10px] gap-2 border-white/5 bg-white/5 hover:bg-white/10 relative overflow-hidden",
                isActive && "border-primary/50 bg-primary/5"
              )}
            >
              <Icon className={cn("w-3.5 h-3.5", dim.color)} />
              {t(dim.labelKey)}

              {/* Rapid Click Feedback Effect */}
              <AnimatePresence>
                {isActive && (
                  <motion.span
                    initial={{ y: 0, opacity: 1 }}
                    animate={{ y: -20, opacity: 0 }}
                    exit={{ opacity: 0 }}
                    className="absolute inset-0 flex items-center justify-center font-bold text-primary pointer-events-none"
                  >
                    {t("overlay_gain")}
                  </motion.span>
                )}
              </AnimatePresence>
            </Button>
          );
        })}
      </div>
    </div>
  );
}
