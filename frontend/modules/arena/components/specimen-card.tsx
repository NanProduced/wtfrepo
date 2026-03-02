"use client";

import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import { useNarratorStore } from "@/shared/store/narrator";
import { ArenaSpecimen } from "@/shared/types/arena";
import {
  ARENA_CARD_INSPECT_NARRATOR_KEYS,
  buildNarratorTrigger,
} from "@/shared/config/narrator-events";

interface SpecimenCardProps {
  specimen: ArenaSpecimen;
  side: "left" | "right";
  onInspect: (id: string) => void;
  isHighlighted?: boolean;
}

export function SpecimenCard({ specimen, side, onInspect, isHighlighted = false }: SpecimenCardProps) {
  const isLeft = side === "left";
  const accentText = isLeft ? "text-primary" : "text-violet-500";
  const borderClass = isLeft ? "border-primary" : "border-violet-500";
  const [owner, name] = specimen.title.includes("/")
    ? specimen.title.split("/", 2)
    : ["", specimen.title];
  const diagnosisPreview =
    specimen.diagnosisTags.length > 0
      ? specimen.diagnosisTags.slice(0, 4).join(", ")
      : "No diagnosis tags yet.";

  const triggerNarrator = useNarratorStore((state) => state.trigger);
  const inspectTriggerKeys = isLeft
    ? ARENA_CARD_INSPECT_NARRATOR_KEYS.left
    : ARENA_CARD_INSPECT_NARRATOR_KEYS.right;

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay: isLeft ? 0 : 0.1 }}
      className="w-full max-w-xl mx-auto h-full flex flex-col"
    >
      {/* Retro Terminal Window */}
      <div className={cn(
        "relative flex flex-col h-full bg-card border-2 shadow-[4px_4px_0px_0px_rgba(0,0,0,0.5)] transition-all duration-200",
        borderClass,
        isHighlighted && (isLeft
          ? "shadow-[0_0_0_2px_var(--primary),8px_8px_0px_0px_rgba(0,0,0,0.5)]"
          : "shadow-[0_0_0_2px_#8b5cf6,8px_8px_0px_0px_rgba(0,0,0,0.5)]")
      )}>

        {/* Terminal Header */}
        <div className={cn(
          "h-8 flex items-center justify-between px-2 border-b-2",
          borderClass,
          isLeft ? "bg-primary/20" : "bg-violet-500/20"
        )}>
          <div className="flex gap-2">
            <div className={cn("w-3 h-3 border-2 bg-background", borderClass)} />
            <div className={cn("w-3 h-3 border-2 bg-background", borderClass)} />
            <div className={cn("w-3 h-3 border-2 bg-background", borderClass)} />
          </div>
          <div className="font-mono text-[10px] tracking-widest opacity-70">
            specimen_{isLeft ? "a" : "b"}.exe
          </div>
          <div className="flex items-center">
            <button
              onClick={(e) => {
                e.stopPropagation();
                triggerNarrator(
                  buildNarratorTrigger(inspectTriggerKeys.click, {
                    context: {
                      side,
                      repo: specimen.title,
                    },
                  })
                );
                onInspect(specimen.specimenId);
              }}
              onMouseEnter={() =>
                triggerNarrator(
                  buildNarratorTrigger(inspectTriggerKeys.hover, {
                    context: {
                      side,
                      repo: specimen.title,
                    },
                  })
                )
              }
              className="hover:bg-black/20 p-1 rounded-sm transition-colors"
              title="Inspect Specimen"
            >
              <div className="w-4 h-4 border border-current flex items-center justify-center text-[10px] font-bold">?</div>
            </button>
          </div>
        </div>

        {/* Content Body */}
        <div className="flex-1 p-6 flex flex-col gap-6 relative overflow-hidden">
           {/* CRT Scanline Overlay (Local) */}
           <div className="absolute inset-0 bg-[linear-gradient(rgba(18,16,16,0)_50%,rgba(0,0,0,0.1)_50%),linear-gradient(90deg,rgba(255,0,0,0.03),rgba(0,255,0,0.01),rgba(0,0,255,0.03))] z-0 bg-[length:100%_2px,3px_100%] pointer-events-none" />

           {/* Repo Header */}
           <div className="relative z-10">
             <div className="flex items-start justify-between">
                <div>
                   <h2 className="font-pixel text-lg md:text-xl leading-tight text-foreground">
                     {owner ? (
                       <>
                         {owner}/<br />
                         <span className={accentText}>{name}</span>
                       </>
                     ) : (
                       <span className={accentText}>{name}</span>
                     )}
                   </h2>
                </div>
                <div className={cn(
                  "flex flex-col items-end font-mono text-xs border-l-2 pl-3",
                  borderClass
                )}>
                   <span>Elo {specimen.elo}</span>
                   <span className="opacity-70">Matches {specimen.matchesPlayed}</span>
                </div>
             </div>
           </div>

           {/* Description Box */}
           <div className="relative z-10 border-l-4 border-muted pl-4 py-1">
             <p className="font-mono text-sm md:text-base text-muted-foreground leading-relaxed">
               {specimen.tagline}
             </p>
           </div>

           {/* Terminal Output */}
           <div className="relative z-10 flex-1 bg-black/50 border border-border p-3 font-mono text-xs text-green-500/90 overflow-hidden">
              <div className="absolute top-0 right-0 p-1 bg-border text-[8px] text-background font-bold px-2">Diagnosis notes</div>
              <div className="h-full overflow-y-auto pr-2 [scrollbar-width:thin] [scrollbar-color:rgba(217,70,239,0.5)_transparent]">
                <div className="opacity-80 leading-relaxed font-thin whitespace-pre-wrap pb-4">
                  <span className="text-pink-500 mr-2">$</span>profile summary
                  <br />
                  species: {specimen.species}
                  <br />
                  ipo status: {specimen.ipoStatus}
                  <br />
                  diagnosis tags: {diagnosisPreview}
                  <motion.span
                    animate={{ opacity: [0, 1, 0] }}
                    transition={{ duration: 0.8, repeat: Infinity }}
                    className="inline-block w-2 h-4 bg-green-500 ml-1 align-middle"
                  />
                </div>
              </div>
           </div>
        </div>
      </div>
    </motion.div>
  );
}
