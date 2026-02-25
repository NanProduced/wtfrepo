package com.wtfrepo.backend.comments.application.support;

/** Centralized constants for M05 comments APIs and write path orchestration. */
public final class CommentsConstants {

  private CommentsConstants() {}

  public static final class Header {

    public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";

    private Header() {}
  }

  public static final class Message {

    public static final String AUTH_REQUIRED = "Authentication required";
    public static final String SPECIMEN_NOT_FOUND = "specimen_not_found";
    public static final String SPECIMEN_NOT_ACTIVE = "specimen_not_active";
    public static final String SPECIMEN_LOCKED = "specimen_locked";
    public static final String COMMENT_NOT_FOUND = "comment_not_found";
    public static final String FORBIDDEN = "forbidden";
    public static final String ALREADY_DELETED = "already_deleted";
    public static final String CONTEXT_NOT_VISIBLE = "context_not_visible";
    public static final String DUPLICATE_REPORT = "duplicate_report";
    public static final String INVALID_CONTENT = "invalid_content";
    public static final String CONTENT_TOO_LONG = "content_too_long";
    public static final String INVALID_SORT = "invalid_sort";
    public static final String INVALID_CURSOR = "invalid_cursor";
    public static final String INVALID_REASON = "invalid_reason";
    public static final String TOO_MANY_MENTIONS = "too_many_mentions";
    public static final String INVALID_MENTION_USER = "invalid_mention_user";
    public static final String MENTION_SELF_NOT_ALLOWED = "mention_self_not_allowed";
    public static final String IDEMPOTENCY_CONFLICT = "idempotency_conflict";
    public static final String INVALID_STATUS = "invalid_comment_status";

    private Message() {}
  }

  public static final class Economy {

    public static final String REF_TYPE_COMMENT = "COMMENT";
    public static final String REASON_COMMENT = "COMMENT";

    private Economy() {}
  }

  public static final class Outbox {

    public static final String AGGREGATE_TYPE_COMMENT = "COMMENT";
    public static final String AGGREGATE_TYPE_SPECIMEN = "COMMENT_SPECIMEN";

    public static final String EVENT_COMMENT_PUBLISHED = "CommentPublishedEvent";
    public static final String EVENT_COMMENT_COUNT_CHANGED = "CommentCountChangedEvent";
    public static final String EVENT_COMMENT_RESONANCE = "CommentResonanceEvent";
    public static final String EVENT_COMMENT_MENTION = "CommentMentionEvent";
    public static final String EVENT_COMMENT_REPORTED = "CommentReportedEvent";
    public static final String EVENT_COMMENT_STATUS_CHANGED = "CommentStatusChangedEvent";
    public static final String EVENT_TOP_ROAST_UPDATED = "TopRoastUpdatedEvent";

    public static final String TRIGGER_ACTION_PUBLISH = "PUBLISH";
    public static final String TRIGGER_ACTION_DELETE = "DELETE";
    public static final String TRIGGER_ACTION_BLOCK = "BLOCK";
    public static final String TRIGGER_ACTION_UNBLOCK = "UNBLOCK";
    public static final String TRIGGER_ACTION_APPROVE = "APPROVE";
    public static final String TRIGGER_ACTION_REPORT = "REPORT";

    public static final String ACTOR_TYPE_USER = "USER";
    public static final String ACTOR_TYPE_ADMIN = "ADMIN";

    private Outbox() {}
  }

  public static final class Id {

    public static final String COMMENT_PREFIX = "cmt_";
    public static final String REQUEST_IDEMPOTENCY_PREFIX = "cmtrid_";
    public static final String RESONANCE_PREFIX = "cmtrs_";
    public static final String REPORT_PREFIX = "cmtrpt_";
    public static final String ECONOMY_DEDUCT_IDEMPOTENCY_PREFIX = "comment_";

    private Id() {}
  }

  public static final class Claim {

    public static final String ROLES = "roles";

    private Claim() {}
  }

  public static final class Role {

    public static final String ADMIN = "ADMIN";
    public static final String MANAGER = "MANAGER";

    private Role() {}
  }
}
