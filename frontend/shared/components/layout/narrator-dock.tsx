"use client";

import { useEffect, useRef, useState } from "react";
import { motion, useSpring, useMotionValue, AnimatePresence } from "framer-motion";
import { useNarratorStore } from "@/shared/store/narrator";
import { useTranslations } from "next-intl";
import { buildNarratorTrigger } from "@/shared/config/narrator-events";
import { useNarratorPreferenceStore } from "@/shared/store/narrator-preference";

const MAX_RECENT_MESSAGE_HISTORY = 3;

function pickRandomMessageIndex(
  total: number,
  recentIndices: number[]
) {
  if (total <= 1) {
    return 0;
  }

  const uniqueRecent = Array.from(new Set(recentIndices));
  const candidates: number[] = [];

  for (let index = 0; index < total; index++) {
    if (!uniqueRecent.includes(index)) {
      candidates.push(index);
    }
  }

  const pool = candidates.length > 0
    ? candidates
    : Array.from({ length: total }, (_, index) => index);

  return pool[Math.floor(Math.random() * pool.length)];
}

function applyNarratorContext(
  text: string,
  context: Record<string, string | number | boolean> | null
) {
  if (!context) {
    return text;
  }

  return text.replace(/\{([a-zA-Z0-9_]+)\}/g, (match, placeholder) => {
    const value = context[placeholder];
    if (value === undefined || value === null) {
      return match;
    }

    return String(value);
  });
}

export function NarratorDock() {
  const {
    activeEvent,
    activeFallbackEvents,
    activeContext,
    activeDisplayMs,
    isVisible,
    activePriority,
    dismiss,
    eventVersion,
  } = useNarratorStore();
  const mode = useNarratorPreferenceStore((state) => state.preference.mode);
  const eyeFollowEnabled = useNarratorPreferenceStore(
    (state) => state.preference.eyeFollowEnabled
  );
  const hydrateLocalPreference = useNarratorPreferenceStore((state) => state.hydrateLocal);
  const preferenceHydrated = useNarratorPreferenceStore((state) => state.hydrated);
  const t = useTranslations("narrator");
  const [currentText, setCurrentText] = useState<string | null>(null);
  const recentMessageIndexByEventRef = useRef<Record<string, number[]>>({});

  // Eye Tracking Logic
  const mouseX = useMotionValue(0);
  const mouseY = useMotionValue(0);

  // Pixel Art Motion: Use stepped movement for "choppy" feel
  // Or just smooth spring for the pupil inside the pixel grid
  const pupilX = useSpring(mouseX, { stiffness: 100, damping: 15 });
  const pupilY = useSpring(mouseY, { stiffness: 100, damping: 15 });

  useEffect(() => {
    if (!preferenceHydrated) {
      hydrateLocalPreference();
    }
  }, [hydrateLocalPreference, preferenceHydrated]);

  useEffect(() => {
    let frameId = 0;

    frameId = window.requestAnimationFrame(() => {
      if (!isVisible || !activeEvent) {
        setCurrentText(null);
        return;
      }

      const candidateKeys = [activeEvent, ...activeFallbackEvents];

      for (const key of candidateKeys) {
        const messagePath = `events.${key}`;

        if (!t.has(messagePath)) {
          continue;
        }

        const messages = t.raw(messagePath);

        if (!Array.isArray(messages) || messages.length === 0) {
          continue;
        }

        const recentIndices = recentMessageIndexByEventRef.current[key] ?? [];
        const selectedIndex = pickRandomMessageIndex(messages.length, recentIndices);
        const updatedRecentIndices = [...recentIndices, selectedIndex].slice(
          -Math.min(MAX_RECENT_MESSAGE_HISTORY, messages.length)
        );

        recentMessageIndexByEventRef.current[key] = updatedRecentIndices;

        const selectedMessage = messages[selectedIndex];
        if (typeof selectedMessage === "string") {
          setCurrentText(applyNarratorContext(selectedMessage, activeContext));
          return;
        }
      }

      setCurrentText(null);
    });

    return () => {
      window.cancelAnimationFrame(frameId);
    };
  }, [isVisible, activeEvent, activeFallbackEvents, activeContext, t, eventVersion]);

  useEffect(() => {
    if (!isVisible || !currentText) {
      return;
    }

    const timer = setTimeout(() => dismiss(), activeDisplayMs);
    return () => clearTimeout(timer);
  }, [activeDisplayMs, isVisible, currentText, dismiss]);

  useEffect(() => {
    if (!eyeFollowEnabled) {
      mouseX.set(0);
      mouseY.set(0);
      return;
    }

    const handleMouseMove = (e: MouseEvent) => {
      const { innerWidth, innerHeight } = window;
      // Offset calibration for bottom-right position
      const x = (e.clientX - (innerWidth - 60)) / 10;
      const y = (e.clientY - (innerHeight - 60)) / 10;

      // Clamp values for pixel eye (range -8 to 8)
      const clampedX = Math.min(Math.max(x, -8), 8);
      const clampedY = Math.min(Math.max(y, -8), 8);

      mouseX.set(clampedX);
      mouseY.set(clampedY);
    };
    window.addEventListener("mousemove", handleMouseMove);
    return () => window.removeEventListener("mousemove", handleMouseMove);
  }, [eyeFollowEnabled, mouseX, mouseY]);

  if (mode === "OFF") {
    return null;
  }

  const allowCurrentPriority =
    mode === "FULL" || activePriority === null || activePriority !== "P2_AMBIENT";
  const shouldRenderBubble = isVisible && currentText && allowCurrentPriority;

  return (
    <div className="fixed bottom-12 right-6 z-50 flex flex-col items-end gap-3 pointer-events-none">

      {/* 8-BIT SPEECH BUBBLE */}
      <AnimatePresence>
        {shouldRenderBubble && (
          <motion.div
            initial={{ opacity: 0, scale: 0.8, y: 10 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.8, y: 10 }}
            className="pointer-events-auto relative bg-white text-black p-4 max-w-[240px] shadow-[4px_4px_0px_0px_rgba(0,0,0,1)] border-2 border-black image-rendering-pixelated"
            style={{ fontFamily: 'var(--font-pixel)' }}
          >
            <p className="text-[10px] leading-relaxed">{currentText}</p>

            {/* Pixel Tail */}
            <div className="absolute -bottom-2 right-4 w-4 h-2 bg-white border-l-2 border-r-2 border-b-2 border-black" />
            <div className="absolute -bottom-3 right-5 w-2 h-1 bg-black" />
          </motion.div>
        )}
      </AnimatePresence>

      {/* 8-BIT EYE CONTAINER */}
      <div
        className="relative w-16 h-16 bg-white border-4 border-black pointer-events-auto cursor-pointer hover:scale-105 transition-transform active:translate-y-1 shadow-[4px_4px_0px_0px_rgba(217,70,239,1)]"
        onClick={() => useNarratorStore.getState().trigger(buildNarratorTrigger("idle"))}
        style={{ imageRendering: 'pixelated' }}
      >
        {/* Eye Outline (Corners) */}
        <div className="absolute inset-0 border-2 border-transparent" />

        {/* Sclera (White Part) */}
        <div className="absolute inset-1 bg-white" />

        {/* Pupil (Moving Part) */}
        <div className="absolute inset-0 flex items-center justify-center">
          <motion.div
            style={{ x: pupilX, y: pupilY }}
            className="w-6 h-6 bg-primary border-2 border-black relative"
          >
            {/* Pupil Glint */}
            <div className="absolute top-1 left-1 w-2 h-2 bg-white" />
          </motion.div>
        </div>

        {/* Eyelid Blink Animation (Optional CSS) */}
        {/* <div className="absolute inset-0 bg-black animate-pixel-blink pointer-events-none" /> */}
      </div>
    </div>
  );
}
