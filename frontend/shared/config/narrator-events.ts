import type { NarratorTriggerPayload } from "@/shared/store/narrator";

type NarratorTriggerOverrides = Omit<Partial<NarratorTriggerPayload>, "key">;
type NarratorTriggerPreset = Partial<Omit<NarratorTriggerPayload, "key">>;

export const ARENA_VOTE_NARRATOR_KEYS = {
  LEFT: {
    hover: "arena.vote.left.hover",
    click: "arena.vote.left.click",
  },
  RIGHT: {
    hover: "arena.vote.right.hover",
    click: "arena.vote.right.click",
  },
  BOTH_BAD: {
    hover: "arena.vote.both_bad.hover",
    click: "arena.vote.both_bad.click",
  },
} as const;

export const ARENA_CARD_INSPECT_NARRATOR_KEYS = {
  left: {
    hover: "arena.card.left.inspect.hover",
    click: "arena.card.left.inspect.click",
  },
  right: {
    hover: "arena.card.right.inspect.hover",
    click: "arena.card.right.inspect.click",
  },
} as const;

const NARRATOR_EVENT_PRESETS: Record<string, NarratorTriggerPreset> = {
  [ARENA_VOTE_NARRATOR_KEYS.LEFT.hover]: {
    fallbackKeys: ["hover_vote"],
    priority: "P2_AMBIENT",
    cooldownMs: 1_200,
    ttlMs: 4_000,
    displayMs: 2_500,
  },
  [ARENA_VOTE_NARRATOR_KEYS.LEFT.click]: {
    fallbackKeys: ["click_vote"],
    priority: "P1_ACTION",
    cooldownMs: 300,
    ttlMs: 8_000,
    displayMs: 2_800,
  },
  [ARENA_VOTE_NARRATOR_KEYS.RIGHT.hover]: {
    fallbackKeys: ["hover_vote"],
    priority: "P2_AMBIENT",
    cooldownMs: 1_200,
    ttlMs: 4_000,
    displayMs: 2_500,
  },
  [ARENA_VOTE_NARRATOR_KEYS.RIGHT.click]: {
    fallbackKeys: ["click_vote"],
    priority: "P1_ACTION",
    cooldownMs: 300,
    ttlMs: 8_000,
    displayMs: 2_800,
  },
  [ARENA_VOTE_NARRATOR_KEYS.BOTH_BAD.hover]: {
    fallbackKeys: ["hover_vote"],
    priority: "P2_AMBIENT",
    cooldownMs: 1_200,
    ttlMs: 4_000,
    displayMs: 2_500,
  },
  [ARENA_VOTE_NARRATOR_KEYS.BOTH_BAD.click]: {
    fallbackKeys: ["click_vote"],
    priority: "P1_ACTION",
    cooldownMs: 300,
    ttlMs: 8_000,
    displayMs: 2_800,
  },
  [ARENA_CARD_INSPECT_NARRATOR_KEYS.left.hover]: {
    fallbackKeys: ["hover_vote"],
    priority: "P2_AMBIENT",
    cooldownMs: 1_000,
    ttlMs: 4_000,
    displayMs: 2_400,
  },
  [ARENA_CARD_INSPECT_NARRATOR_KEYS.left.click]: {
    fallbackKeys: ["click_vote"],
    priority: "P1_ACTION",
    cooldownMs: 300,
    ttlMs: 8_000,
    displayMs: 2_800,
  },
  [ARENA_CARD_INSPECT_NARRATOR_KEYS.right.hover]: {
    fallbackKeys: ["hover_vote"],
    priority: "P2_AMBIENT",
    cooldownMs: 1_000,
    ttlMs: 4_000,
    displayMs: 2_400,
  },
  [ARENA_CARD_INSPECT_NARRATOR_KEYS.right.click]: {
    fallbackKeys: ["click_vote"],
    priority: "P1_ACTION",
    cooldownMs: 300,
    ttlMs: 8_000,
    displayMs: 2_800,
  },
  idle: {
    priority: "P2_AMBIENT",
    cooldownMs: 2_000,
    ttlMs: 8_000,
    displayMs: 2_500,
  },
};

export function buildNarratorTrigger(
  key: string,
  overrides: NarratorTriggerOverrides = {}
): NarratorTriggerPayload {
  const base = NARRATOR_EVENT_PRESETS[key] ?? {};

  return {
    key,
    ...base,
    ...overrides,
    fallbackKeys: overrides.fallbackKeys ?? base.fallbackKeys,
    context: overrides.context ?? base.context,
  };
}
