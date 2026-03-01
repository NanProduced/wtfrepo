"use client";

import { create } from "zustand";
import {
  getNarratorPreference,
  mergeNarratorPreferenceOnLogin,
  patchNarratorPreference,
} from "@/shared/api/narrator";
import {
  NarratorMode,
  NarratorPreference,
  NarratorPreferencePatch,
  NarratorTone,
} from "@/shared/types/narrator";

const LOCAL_STORAGE_KEY = "wtf-repo.narrator.preference";

const DEFAULT_PREFERENCE: NarratorPreference = {
  mode: "FULL",
  modeIsExplicit: false,
  tonePreference: "SAFE",
  eyeFollowEnabled: true,
  tickerEnabled: true,
  reducedMotionApplied: false,
  updatedAt: null,
};

function safeParseLocalPreference() {
  if (typeof window === "undefined") {
    return null;
  }

  const raw = window.localStorage.getItem(LOCAL_STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as Partial<NarratorPreference>;
    return { ...DEFAULT_PREFERENCE, ...parsed };
  } catch {
    return null;
  }
}

function persistLocalPreference(preference: NarratorPreference) {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(preference));
}

export interface NarratorPreferenceState {
  preference: NarratorPreference;
  isLoading: boolean;
  hydrated: boolean;
  lastError: string | null;
  hydrateLocal: () => void;
  syncFromServer: () => Promise<void>;
  mergeOnLogin: () => Promise<void>;
  updateLocal: (patch: NarratorPreferencePatch) => void;
  updateRemote: (patch: NarratorPreferencePatch) => Promise<void>;
  cycleModeLocal: () => NarratorMode;
  cycleModeRemote: () => Promise<NarratorMode>;
  setToneLocal: (tone: NarratorTone) => void;
  setToneRemote: (tone: NarratorTone) => Promise<void>;
}

function mergePreference(base: NarratorPreference, patch: NarratorPreferencePatch) {
  return {
    ...base,
    ...patch,
    modeIsExplicit: patch.mode ? true : base.modeIsExplicit,
  };
}

function nextMode(current: NarratorMode): NarratorMode {
  if (current === "FULL") return "LITE";
  if (current === "LITE") return "OFF";
  return "FULL";
}

export const useNarratorPreferenceStore = create<NarratorPreferenceState>((set, get) => ({
  preference: DEFAULT_PREFERENCE,
  isLoading: false,
  hydrated: false,
  lastError: null,

  hydrateLocal: () => {
    const localPreference = safeParseLocalPreference();
    set((state) => ({
      ...state,
      preference: localPreference ?? state.preference,
      hydrated: true,
    }));
  },

  syncFromServer: async () => {
    set((state) => ({ ...state, isLoading: true, lastError: null }));
    try {
      const preference = await getNarratorPreference();
      persistLocalPreference(preference);
      set((state) => ({
        ...state,
        preference,
        isLoading: false,
        hydrated: true,
      }));
    } catch (error) {
      const fallbackPreference = safeParseLocalPreference() ?? DEFAULT_PREFERENCE;
      set((state) => ({
        ...state,
        preference: fallbackPreference,
        isLoading: false,
        hydrated: true,
        lastError:
          typeof error === "object" && error !== null && "message" in error
            ? String((error as { message: string }).message)
            : "Failed to load narrator preference.",
      }));
    }
  },

  mergeOnLogin: async () => {
    const local = safeParseLocalPreference();
    if (!local) {
      return;
    }

    try {
      const merged = await mergeNarratorPreferenceOnLogin({
        localMode: local.mode,
        localTonePreference: local.tonePreference,
        localEyeFollowEnabled: local.eyeFollowEnabled,
        localTickerEnabled: local.tickerEnabled,
      });

      const nextPreference: NarratorPreference = {
        ...merged,
        updatedAt: new Date().toISOString(),
      };
      persistLocalPreference(nextPreference);
      set((state) => ({ ...state, preference: nextPreference, hydrated: true }));
    } catch {
      // Ignore merge failures to keep UI responsive.
    }
  },

  updateLocal: (patch) => {
    const nextPreference = mergePreference(get().preference, patch);
    persistLocalPreference(nextPreference);
    set((state) => ({ ...state, preference: nextPreference, hydrated: true }));
  },

  updateRemote: async (patch) => {
    const previous = get().preference;
    const optimistic = mergePreference(previous, patch);
    set((state) => ({
      ...state,
      preference: optimistic,
      isLoading: true,
      lastError: null,
    }));

    try {
      const saved = await patchNarratorPreference(patch);
      persistLocalPreference(saved);
      set((state) => ({
        ...state,
        preference: saved,
        isLoading: false,
      }));
    } catch (error) {
      persistLocalPreference(previous);
      set((state) => ({
        ...state,
        preference: previous,
        isLoading: false,
        lastError:
          typeof error === "object" && error !== null && "message" in error
            ? String((error as { message: string }).message)
            : "Failed to update narrator preference.",
      }));
    }
  },

  cycleModeLocal: () => {
    const current = get().preference.mode;
    const mode = nextMode(current);
    get().updateLocal({ mode });
    return mode;
  },

  cycleModeRemote: async () => {
    const current = get().preference.mode;
    const mode = nextMode(current);
    await get().updateRemote({ mode });
    return mode;
  },

  setToneLocal: (tone) => {
    get().updateLocal({ tonePreference: tone });
  },

  setToneRemote: async (tone) => {
    await get().updateRemote({ tonePreference: tone });
  },
}));
