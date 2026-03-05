export type CommentSort = "hot" | "new";
export type CommentStatus = "ACTIVE" | "PENDING_REVIEW" | "BLOCKED" | "DELETED";
export type CommentReportReasonCode =
  | "POLITICAL"
  | "PORN"
  | "HATE"
  | "SPAM"
  | "COPYRIGHT"
  | "OTHER";

export interface CommentListItem {
  commentId: string;
  specimenId: string;
  authorUserId: string;
  contentPreview: string;
  resonanceCount: number;
  isChiefConclusion: boolean;
  status: CommentStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CommentListPage {
  items: CommentListItem[];
  nextCursor: string | null;
}

export interface CommentPublishPayload {
  specimenId: string;
  contentMd: string;
  replyToCommentId?: string;
  clientRequestId: string;
}

export interface CommentPublishResult {
  commentId: string;
  specimenId: string;
  status: CommentStatus;
  bugCost: number;
  balanceAfter: number;
  billingLedgerId: string;
  createdAt: string;
}

export interface CommentResonanceResult {
  commentId: string;
  resonanceCount: number;
  resonated: boolean;
}

export interface CommentReportPayload {
  reasonCode: CommentReportReasonCode;
  message?: string;
  clientRequestId: string;
}

export interface CommentReportResult {
  reported: boolean;
  ticketId: string;
}

export interface CommentContextResponse {
  commentId: string;
  author: {
    userId: string;
    username: string | null;
    avatarUrl: string | null;
  };
  contentPreview: string;
  status: CommentStatus;
}

export interface CommentDeleteResult {
  deleted: boolean;
  refundDelta: number;
}

export interface CommentTopRoastResponse {
  hasTopRoast: boolean;
  commentId: string | null;
  author: {
    userId: string;
    username: string | null;
    avatarUrl: string | null;
  } | null;
  contentPreview: string | null;
  resonanceCount: number | null;
}
