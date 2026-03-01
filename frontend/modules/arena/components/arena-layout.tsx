"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { SpecimenCard } from "./specimen-card";
import { motion, AnimatePresence } from "framer-motion";
import { SpecimenDrawer } from "@/modules/specimen/components/specimen-drawer";
import { RotateCcw } from "lucide-react";
import { useNarratorStore } from "@/shared/store/narrator";
import { ArenaActionButton } from "./arena-action-button";
import { getArenaDuel, submitArenaVote } from "@/shared/api/arena";
import {
  ArenaDuelResponse,
  ArenaVoteChoice,
} from "@/shared/types/arena";
import { getFriendlyErrorMessage } from "@/shared/lib/error-map";
import { toast } from "sonner";
import {
  ARENA_VOTE_NARRATOR_KEYS,
  buildNarratorTrigger,
} from "@/shared/config/narrator-events";
import { useTranslations } from "next-intl";

function getVoteNarratorEvent(choice: ArenaVoteChoice, phase: "hover" | "click") {
  const triggerKeyByChoice = {
    LEFT: ARENA_VOTE_NARRATOR_KEYS.LEFT[phase],
    RIGHT: ARENA_VOTE_NARRATOR_KEYS.RIGHT[phase],
    BOTH_BAD: ARENA_VOTE_NARRATOR_KEYS.BOTH_BAD[phase],
  } as const;

  return triggerKeyByChoice[choice];
}

export function ArenaLayout() {
  const t = useTranslations("arena.layout");
  const [isVoting, setIsVoting] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [duel, setDuel] = useState<ArenaDuelResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [selectedSpecimenId, setSelectedSpecimenId] = useState<string | null>(null);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [hoveredVote, setHoveredVote] = useState<ArenaVoteChoice | null>(null);

  const triggerNarrator = useNarratorStore((state) => state.trigger);

  const leftSpecimen = duel?.left;
  const rightSpecimen = duel?.right;

  const round = duel?.round ?? 1;
  const participantCount = duel?.participantCount ?? 0;

  const canVote = useMemo(() => {
    return !isVoting && !isLoading && !!duel;
  }, [duel, isLoading, isVoting]);

  const loadDuel = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);

    try {
      const payload = await getArenaDuel();
      setDuel(payload);
    } catch (error) {
      const friendlyMessage = getFriendlyErrorMessage(error);
      setErrorMessage(friendlyMessage);
      toast.error(t("toast.arena_unavailable"), {
        description: friendlyMessage,
      });
    } finally {
      setIsLoading(false);
    }
  }, [t]);

  useEffect(() => {
    void loadDuel();
  }, [loadDuel]);

  const handleVote = async (choice: ArenaVoteChoice) => {
    if (!duel || isVoting) {
      return;
    }

    setIsVoting(true);

    triggerNarrator(
      buildNarratorTrigger(getVoteNarratorEvent(choice, "click"), {
        context: {
          side: choice,
        },
      })
    );

    try {
      const result = await submitArenaVote({
        battleId: duel.battleId,
        choice,
      });

      if (result.newBugBalance !== null) {
        toast.success(t("toast.diagnosis_submitted"), {
          description: t("toast.balance", { amount: result.newBugBalance }),
        });
      }

      await loadDuel();
    } catch (error) {
      const friendlyMessage = getFriendlyErrorMessage(error);
      toast.error(t("toast.diagnosis_failed"), {
        description: friendlyMessage,
      });
    } finally {
      setIsVoting(false);
      setHoveredVote(null);
    }
  };

  const handleVoteHover = (choice: ArenaVoteChoice) => {
    if (isVoting) {
      return;
    }

    setHoveredVote(choice === "BOTH_BAD" ? null : choice);

    triggerNarrator(
      buildNarratorTrigger(getVoteNarratorEvent(choice, "hover"), {
        context: {
          side: choice,
        },
      })
    );
  };

  const handleInspect = (id: string) => {
    setSelectedSpecimenId(id);
    setIsDrawerOpen(true);
  };

  const handleRetry = () => {
    void loadDuel();
  };

  return (
    <div className="flex-1 flex flex-col md:flex-row items-center justify-center relative w-full h-full min-h-[calc(100vh-6rem)] gap-4 md:gap-0 p-4 md:p-8 overflow-hidden bg-background">

      {/* Background Grid */}
      <div className="absolute inset-0 z-0 opacity-10 pointer-events-none"
           style={{
             backgroundImage: 'linear-gradient(#333 1px, transparent 1px), linear-gradient(90deg, #333 1px, transparent 1px)',
             backgroundSize: '40px 40px'
           }}
      />

      {isLoading && (
        <div className="absolute inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm">
          <div className="flex items-center gap-3 rounded-lg border border-white/10 bg-black/40 px-4 py-3 font-mono text-xs tracking-widest text-zinc-300">
            <RotateCcw className="h-4 w-4 animate-spin text-primary" />
            {t("loading_duel")}
          </div>
        </div>
      )}

      {errorMessage && !isLoading && (
        <div className="absolute inset-0 z-50 flex items-center justify-center px-4">
          <div className="max-w-md rounded-lg border border-destructive/30 bg-black/70 p-5 text-center">
            <p className="font-mono text-xs tracking-widest text-destructive">{t("arena_offline")}</p>
            <p className="mt-3 text-sm text-zinc-300">{errorMessage}</p>
            <ArenaActionButton
              onClick={handleRetry}
              className="mt-4 h-9 px-4 font-mono text-xs tracking-widest"
            >
              {t("retry")}
            </ArenaActionButton>
          </div>
        </div>
      )}

      {/* VS Badge (Center) */}
      <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-30 pointer-events-none flex flex-col items-center gap-2">
        <motion.div
          animate={{ scale: [1, 1.05, 1] }}
          transition={{ duration: 0.8, repeat: Infinity, ease: "linear" }}
          className="w-20 h-20 md:w-28 md:h-28 bg-red-600 border-4 border-black shadow-[4px_4px_0px_0px_rgba(0,0,0,1)] flex items-center justify-center transform rotate-3"
        >
          <span className="font-pixel text-2xl md:text-4xl text-white drop-shadow-[2px_2px_0px_rgba(0,0,0,0.5)]">VS</span>
        </motion.div>
        <div className="hidden md:flex flex-col items-center gap-1 font-mono text-[10px] tracking-widest text-zinc-400 bg-black/40 border border-white/10 px-3 py-2 rounded-md">
          <span>{t("round", { round })}</span>
          <span className="text-zinc-500">{t("doctors_online", { count: participantCount })}</span>
        </div>
      </div>

      {/* Left Side (Specimen A) */}
      <div className="w-full md:w-1/2 h-full flex items-center justify-center p-2 md:p-8 md:pr-12 relative z-10">
        {leftSpecimen && (
          <AnimatePresence mode="wait">
            <SpecimenCard
              key={`a-${round}-${leftSpecimen.specimenId}`}
              specimen={leftSpecimen}
              side="left"
              onInspect={handleInspect}
              isHighlighted={hoveredVote === "LEFT"}
            />
          </AnimatePresence>
        )}
      </div>

      {/* Right Side (Specimen B) */}
      <div className="w-full md:w-1/2 h-full flex items-center justify-center p-2 md:p-8 md:pl-12 relative z-10">
        {rightSpecimen && (
          <AnimatePresence mode="wait">
            <SpecimenCard
              key={`b-${round}-${rightSpecimen.specimenId}`}
              specimen={rightSpecimen}
              side="right"
              onInspect={handleInspect}
              isHighlighted={hoveredVote === "RIGHT"}
            />
          </AnimatePresence>
        )}
      </div>

      {/* Unified Action Bar */}
      <div className="absolute bottom-12 md:bottom-16 left-1/2 -translate-x-1/2 z-40 w-[calc(100%-2rem)] md:w-auto">
        <div className="grid grid-cols-3 gap-2 md:gap-3 bg-black/70 backdrop-blur-md border border-white/10 rounded-xl p-2">
          <ArenaActionButton
            onClick={() => handleVote("LEFT")}
            onMouseEnter={() => handleVoteHover("LEFT")}
            onMouseLeave={() => setHoveredVote(null)}
            disabled={!canVote}
            className="h-11 md:h-12 px-4 md:px-6 font-pixel text-xs md:text-sm rounded-none border-2 border-primary bg-primary text-primary-foreground shadow-[3px_3px_0px_0px_rgba(0,0,0,1)] hover:translate-x-[1px] hover:translate-y-[1px] hover:shadow-none transition-all"
          >
            {isVoting ? <RotateCcw className="w-4 h-4 animate-spin" /> : t("action.diagnose_a")}
          </ArenaActionButton>

          <ArenaActionButton
            variant="outline"
            onClick={() => handleVote("BOTH_BAD")}
            onMouseEnter={() => handleVoteHover("BOTH_BAD")}
            onMouseLeave={() => setHoveredVote(null)}
            disabled={!canVote}
            className="h-11 md:h-12 px-4 md:px-6 font-pixel text-xs md:text-sm rounded-none border-2 border-zinc-600 bg-zinc-900/90 text-zinc-200 hover:bg-zinc-800 hover:text-white"
          >
            {isVoting ? <RotateCcw className="w-4 h-4 animate-spin" /> : t("action.both_bad")}
          </ArenaActionButton>

          <ArenaActionButton
            onClick={() => handleVote("RIGHT")}
            onMouseEnter={() => handleVoteHover("RIGHT")}
            onMouseLeave={() => setHoveredVote(null)}
            disabled={!canVote}
            className="h-11 md:h-12 px-4 md:px-6 font-pixel text-xs md:text-sm rounded-none border-2 border-violet-600 bg-violet-600 text-white shadow-[3px_3px_0px_0px_rgba(0,0,0,1)] hover:translate-x-[1px] hover:translate-y-[1px] hover:shadow-none transition-all"
          >
            {isVoting ? <RotateCcw className="w-4 h-4 animate-spin" /> : t("action.diagnose_b")}
          </ArenaActionButton>
        </div>
      </div>

      {/* Mobile Divider Space */}
      <div className="h-24 md:hidden w-full" />

      {/* Drawer */}
      <SpecimenDrawer
        specimenId={selectedSpecimenId}
        isOpen={isDrawerOpen}
        onClose={() => setIsDrawerOpen(false)}
      />
    </div>
  );
}
