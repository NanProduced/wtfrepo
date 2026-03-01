/**
 * Error mapping for Backend Error Codes to User-friendly messages.
 * Based on local-docs/07-Backend-Consistency-Baseline.md
 */

interface ErrorViolation {
  field?: string;
  message: string;
}

interface FriendlyErrorPayload {
  code?: string;
  status?: string | number;
  message?: string;
  violations?: ErrorViolation[];
}

export const ERROR_MESSAGES: Record<string, string | ((violations?: ErrorViolation[]) => string)> = {
  // Common
  "VALIDATION_ERROR": (violations) => {
    if (violations && violations.length > 0) {
      return `Invalid input: ${violations[0].message}`;
    }
    return "Validation failed. Please check your input.";
  },
  "UNAUTHORIZED": "Session expired. Please sign in again.",
  "FORBIDDEN": "Access denied. You don't have permission for this sector.",
  "INTERNAL_SERVER_ERROR": "The Asylum's mainframe is glitching. Please try again later.",
  "RATE_LIMIT_EXCEEDED": "Too many requests. Calm down, patient.",

  // Auth Specific
  "USERNAME_ALREADY_TAKEN": "That handle is already registered in our database.",
  "INVALID_USERNAME_FORMAT": "Username must be 3-20 characters (alphanumeric).",
  "IDENTITY_PROOF_EXPIRED": "Your scan session expired. Please restart the admission process.",

  // Economy Specific
  "INSUFFICIENT_FUNDS": "Insufficient Bug balance. Go work in the laundry room.",
  "IDEMPOTENCY_CONFLICT": "Request already processed. Don't spam the terminal.",
};

export function getFriendlyErrorMessage(error: unknown): string {
  if (typeof error === "string") return error;

  const payload = (error ?? {}) as FriendlyErrorPayload;
  const code = String(payload.code ?? payload.status ?? "");
  const message = ERROR_MESSAGES[code];

  if (typeof message === "function") {
    return message(payload.violations);
  }

  if (message) return message;

  return payload.message || "An unknown anomaly occurred.";
}
