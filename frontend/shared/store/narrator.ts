import { create } from "zustand";

type NarratorContextValue = string | number | boolean;
export type NarratorTriggerContext = Record<string, NarratorContextValue>;

export type NarratorPriority = "P0_CRITICAL" | "P1_ACTION" | "P2_AMBIENT";

export interface NarratorTriggerPayload {
  key: string;
  fallbackKeys?: string[];
  priority?: NarratorPriority;
  cooldownMs?: number;
  ttlMs?: number;
  displayMs?: number;
  context?: NarratorTriggerContext;
}

interface NarratorScheduledEvent {
  id: number;
  key: string;
  fallbackKeys: string[];
  priority: NarratorPriority;
  cooldownMs: number;
  ttlMs: number;
  displayMs: number;
  createdAt: number;
  expiresAt: number;
  context?: NarratorTriggerContext;
}

interface NarratorState {
  message: string | null;
  isVisible: boolean;
  activeEvent: string | null;
  activeFallbackEvents: string[];
  activePriority: NarratorPriority | null;
  activeContext: NarratorTriggerContext | null;
  activeDisplayMs: number;
  eventVersion: number;
  queueSize: number;
  trigger: (eventKey: string | NarratorTriggerPayload) => void;
  dismiss: () => void;
  clearQueue: () => void;
  queue: NarratorScheduledEvent[];
  activeEntry: NarratorScheduledEvent | null;
  cooldownUntilByKey: Record<string, number>;
}

const DEFAULT_PRIORITY: NarratorPriority = "P1_ACTION";
const DEFAULT_TTL_MS = 10_000;
const DEFAULT_DISPLAY_MS = 3_000;
const DEFAULT_COOLDOWN_MS_BY_PRIORITY: Record<NarratorPriority, number> = {
  P0_CRITICAL: 0,
  P1_ACTION: 200,
  P2_AMBIENT: 1_000,
};
const MAX_QUEUE_LENGTH = 8;
const MAX_AMBIENT_QUEUE_LENGTH = 3;

const PRIORITY_WEIGHT: Record<NarratorPriority, number> = {
  P0_CRITICAL: 0,
  P1_ACTION: 1,
  P2_AMBIENT: 2,
};

let narratorEventCounter = 0;

function inferPriorityFromKey(key: string): NarratorPriority {
  if (key.endsWith(".hover") || key === "idle") {
    return "P2_AMBIENT";
  }

  if (key.includes("error") || key.includes("blocked") || key.includes("fail")) {
    return "P0_CRITICAL";
  }

  return DEFAULT_PRIORITY;
}

function sortQueueByPriority(queue: NarratorScheduledEvent[]) {
  return [...queue].sort((left, right) => {
    const priorityDelta = PRIORITY_WEIGHT[left.priority] - PRIORITY_WEIGHT[right.priority];
    if (priorityDelta !== 0) {
      return priorityDelta;
    }

    return left.createdAt - right.createdAt;
  });
}

function pruneExpired(queue: NarratorScheduledEvent[], now: number) {
  return queue.filter((item) => item.expiresAt > now);
}

function removeLowestPriorityEvent(queue: NarratorScheduledEvent[]) {
  if (queue.length === 0) {
    return queue;
  }

  let removalIndex = 0;

  for (let index = 1; index < queue.length; index++) {
    const current = queue[index];
    const candidate = queue[removalIndex];
    const currentWeight = PRIORITY_WEIGHT[current.priority];
    const candidateWeight = PRIORITY_WEIGHT[candidate.priority];

    if (currentWeight > candidateWeight) {
      removalIndex = index;
      continue;
    }

    if (currentWeight === candidateWeight && current.createdAt < candidate.createdAt) {
      removalIndex = index;
    }
  }

  return queue.filter((_, index) => index !== removalIndex);
}

function normalizePayload(
  eventKey: string | NarratorTriggerPayload,
  now: number
): NarratorScheduledEvent {
  if (typeof eventKey === "string") {
    const priority = inferPriorityFromKey(eventKey);
    const ttlMs = DEFAULT_TTL_MS;
    const displayMs = DEFAULT_DISPLAY_MS;
    const cooldownMs = DEFAULT_COOLDOWN_MS_BY_PRIORITY[priority];

    return {
      id: ++narratorEventCounter,
      key: eventKey,
      fallbackKeys: [],
      priority,
      cooldownMs,
      ttlMs,
      displayMs,
      createdAt: now,
      expiresAt: now + ttlMs,
    };
  }

  const priority = eventKey.priority ?? inferPriorityFromKey(eventKey.key);
  const ttlMs = Math.max(1_000, eventKey.ttlMs ?? DEFAULT_TTL_MS);
  const displayMs = Math.max(1_000, eventKey.displayMs ?? DEFAULT_DISPLAY_MS);
  const cooldownMs = Math.max(
    0,
    eventKey.cooldownMs ?? DEFAULT_COOLDOWN_MS_BY_PRIORITY[priority]
  );

  return {
    id: ++narratorEventCounter,
    key: eventKey.key,
    fallbackKeys: eventKey.fallbackKeys ?? [],
    priority,
    cooldownMs,
    ttlMs,
    displayMs,
    context: eventKey.context,
    createdAt: now,
    expiresAt: now + ttlMs,
  };
}

function canPreempt(incoming: NarratorScheduledEvent, active: NarratorScheduledEvent | null) {
  if (!active) {
    return true;
  }

  return PRIORITY_WEIGHT[incoming.priority] < PRIORITY_WEIGHT[active.priority];
}

function applyActiveEvent(
  state: NarratorState,
  activeEntry: NarratorScheduledEvent,
  queue: NarratorScheduledEvent[],
  cooldownUntilByKey: Record<string, number>
) {
  return {
    ...state,
    isVisible: true,
    activeEntry,
    activeEvent: activeEntry.key,
    activeFallbackEvents: activeEntry.fallbackKeys,
    activePriority: activeEntry.priority,
    activeContext: activeEntry.context ?? null,
    activeDisplayMs: activeEntry.displayMs,
    message: null,
    eventVersion: state.eventVersion + 1,
    queue,
    queueSize: queue.length,
    cooldownUntilByKey,
  };
}

export const useNarratorStore = create<NarratorState>((set) => ({
  message: null,
  isVisible: false,
  activeEvent: null,
  activeFallbackEvents: [],
  activePriority: null,
  activeContext: null,
  activeDisplayMs: DEFAULT_DISPLAY_MS,
  eventVersion: 0,
  queueSize: 0,
  queue: [],
  activeEntry: null,
  cooldownUntilByKey: {},

  trigger: (eventKey) => {
    set((state) => {
      const now = Date.now();
      const incoming = normalizePayload(eventKey, now);
      const existingCooldownUntil = state.cooldownUntilByKey[incoming.key] ?? 0;
      const prunedQueue = pruneExpired(state.queue, now);

      if (existingCooldownUntil > now) {
        return {
          ...state,
          queue: prunedQueue,
          queueSize: prunedQueue.length,
        };
      }

      const cooldownUntilByKey = {
        ...state.cooldownUntilByKey,
        [incoming.key]: now + incoming.cooldownMs,
      };

      const hasActive = state.isVisible && state.activeEntry !== null;

      if (!hasActive) {
        return applyActiveEvent(state, incoming, prunedQueue, cooldownUntilByKey);
      }

      if (canPreempt(incoming, state.activeEntry)) {
        const queueWithPreempted = state.activeEntry.expiresAt > now
          ? sortQueueByPriority([...prunedQueue, state.activeEntry])
          : prunedQueue;

        return applyActiveEvent(state, incoming, queueWithPreempted, cooldownUntilByKey);
      }

      const queueWithoutDuplicate = prunedQueue.filter((item) => item.key !== incoming.key);
      const ambientCount = queueWithoutDuplicate.filter(
        (item) => item.priority === "P2_AMBIENT"
      ).length;

      if (
        incoming.priority === "P2_AMBIENT" &&
        ambientCount >= MAX_AMBIENT_QUEUE_LENGTH
      ) {
        return {
          ...state,
          queue: queueWithoutDuplicate,
          queueSize: queueWithoutDuplicate.length,
          cooldownUntilByKey,
        };
      }

      let nextQueue = sortQueueByPriority([...queueWithoutDuplicate, incoming]);

      if (nextQueue.length > MAX_QUEUE_LENGTH) {
        if (incoming.priority === "P2_AMBIENT") {
          nextQueue = queueWithoutDuplicate;
        } else {
          nextQueue = removeLowestPriorityEvent(nextQueue);
        }
      }

      return {
        ...state,
        queue: nextQueue,
        queueSize: nextQueue.length,
        cooldownUntilByKey,
      };
    });
  },

  dismiss: () => {
    set((state) => {
      const now = Date.now();
      const prunedQueue = pruneExpired(state.queue, now);

      if (prunedQueue.length === 0) {
        return {
          ...state,
          isVisible: false,
          activeEntry: null,
          activeEvent: null,
          activeFallbackEvents: [],
          activePriority: null,
          activeContext: null,
          activeDisplayMs: DEFAULT_DISPLAY_MS,
          message: null,
          queue: [],
          queueSize: 0,
        };
      }

      const [nextActive, ...restQueue] = prunedQueue;

      return {
        ...state,
        isVisible: true,
        activeEntry: nextActive,
        activeEvent: nextActive.key,
        activeFallbackEvents: nextActive.fallbackKeys,
        activePriority: nextActive.priority,
        activeContext: nextActive.context ?? null,
        activeDisplayMs: nextActive.displayMs,
        message: null,
        eventVersion: state.eventVersion + 1,
        queue: restQueue,
        queueSize: restQueue.length,
      };
    });
  },

  clearQueue: () => {
    set((state) => ({
      ...state,
      queue: [],
      queueSize: 0,
    }));
  },
}));
