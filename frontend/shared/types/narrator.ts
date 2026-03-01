export type NarratorMode = "FULL" | "LITE" | "OFF";
export type NarratorTone = "SAFE" | "EDGY";
export type TickerPriority = "P0_CRITICAL" | "P1_ACTION" | "P2_AMBIENT";

export interface NarratorPreference {
  mode: NarratorMode;
  modeIsExplicit: boolean;
  tonePreference: NarratorTone;
  eyeFollowEnabled: boolean;
  tickerEnabled: boolean;
  reducedMotionApplied: boolean;
  updatedAt: string | null;
}

export interface NarratorPreferencePatch {
  mode?: NarratorMode;
  tonePreference?: NarratorTone;
  eyeFollowEnabled?: boolean;
  tickerEnabled?: boolean;
}

export interface NarratorPreferenceMergePayload {
  localMode?: NarratorMode;
  localTonePreference?: NarratorTone;
  localEyeFollowEnabled?: boolean;
  localTickerEnabled?: boolean;
}

export interface NarratorPreferenceMergeResult
  extends Omit<NarratorPreference, "updatedAt"> {
  mergedFrom: string;
}

export interface TickerRecentItem {
  eventId: string;
  eventType: string;
  text: string;
  priority: TickerPriority | string;
  actionUrl: string | null;
  occurredAt: string | null;
  expiresAt: string | null;
}

export interface TickerRecentPage {
  items: TickerRecentItem[];
  nextCursor: string | null;
  hasMore: boolean;
}

export interface TickerItemClickPayload {
  eventId: string;
  eventType: string;
  actionUrl?: string | null;
}
