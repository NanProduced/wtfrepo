package com.wtfrepo.backend.comments.application;

import com.wtfrepo.backend.auth.infra.persistence.entity.AuthUserJpaEntity;
import com.wtfrepo.backend.auth.infra.persistence.repository.AuthUserJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.wtfrepo.backend.comments.application.model.CommentAdminModels;
import com.wtfrepo.backend.comments.application.support.CommentMentionParser;
import com.wtfrepo.backend.comments.application.support.CommentsConstants;
import com.wtfrepo.backend.comments.application.support.CommentsExceptions;
import com.wtfrepo.backend.comments.domain.CommentAuthorRepoRoleSnapshot;
import com.wtfrepo.backend.comments.domain.CommentModerationAction;
import com.wtfrepo.backend.comments.domain.CommentModerationActorType;
import com.wtfrepo.backend.comments.domain.CommentModerationRiskLevel;
import com.wtfrepo.backend.comments.domain.CommentReportReasonCode;
import com.wtfrepo.backend.comments.domain.CommentStatus;
import com.wtfrepo.backend.comments.infra.persistence.entity.CommentJpaEntity;
import com.wtfrepo.backend.comments.infra.persistence.entity.CommentModerationLogJpaEntity;
import com.wtfrepo.backend.comments.infra.persistence.entity.CommentReportJpaEntity;
import com.wtfrepo.backend.comments.infra.persistence.entity.CommentRequestIdempotencyJpaEntity;
import com.wtfrepo.backend.comments.infra.persistence.entity.CommentResonanceJpaEntity;
import com.wtfrepo.backend.comments.infra.persistence.repository.CommentJpaRepository;
import com.wtfrepo.backend.comments.infra.persistence.repository.CommentModerationLogJpaRepository;
import com.wtfrepo.backend.comments.infra.persistence.repository.CommentReportJpaRepository;
import com.wtfrepo.backend.comments.infra.persistence.repository.CommentRequestIdempotencyJpaRepository;
import com.wtfrepo.backend.comments.infra.persistence.repository.CommentResonanceJpaRepository;
import com.wtfrepo.backend.economy.application.EconomyWalletService;
import com.wtfrepo.backend.economy.infra.persistence.entity.EconomyLedgerJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.repository.EconomyLedgerJpaRepository;
import com.wtfrepo.backend.shared.json.JsonUtils;
import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import com.wtfrepo.backend.specimen.domain.SpecimenStatus;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenJpaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** M05 comments application service aligned with contract v0.1 public scope. */
@Service
public class CommentService {

  private static final Logger log = LoggerFactory.getLogger(CommentService.class);

  private static final Set<CommentStatus> LIST_VISIBLE_STATUSES =
      Set.of(CommentStatus.ACTIVE, CommentStatus.DELETED);
  private static final Set<CommentStatus> MY_COMMENTS_FILTERABLE_STATUSES =
      Set.of(
          CommentStatus.ACTIVE,
          CommentStatus.PENDING_REVIEW,
          CommentStatus.BLOCKED,
          CommentStatus.DELETED);
  private static final String MY_COMMENTS_STATUS_ALL = "ALL";
  private static final String EMPTY_JSON_ARRAY = "[]";
  private static final String DEFAULT_LIST_SORT = "hot";
  private static final String SORT_HOT = "hot";
  private static final String SORT_NEW = "new";
  private static final int DEFAULT_ADMIN_PAGE_SIZE = 20;
  private static final int MAX_ADMIN_PAGE_SIZE = 100;
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  private final CommentJpaRepository commentRepository;
  private final CommentRequestIdempotencyJpaRepository idempotencyRepository;
  private final CommentResonanceJpaRepository resonanceRepository;
  private final CommentReportJpaRepository reportRepository;
  private final CommentModerationLogJpaRepository moderationLogRepository;
  private final AuthUserJpaRepository authUserRepository;
  private final SpecimenJpaRepository specimenRepository;
  private final CommentAuthorRoleResolver commentAuthorRoleResolver;
  private final CommentMentionParser commentMentionParser;
  private final EconomyWalletService economyWalletService;
  private final EconomyLedgerJpaRepository economyLedgerRepository;
  private final OutboxEventStore outboxEventStore;
  private final CommentsPolicyProperties commentsPolicyProperties;
  private final JsonUtils jsonUtils;

  public CommentService(
      CommentJpaRepository commentRepository,
      CommentRequestIdempotencyJpaRepository idempotencyRepository,
      CommentResonanceJpaRepository resonanceRepository,
      CommentReportJpaRepository reportRepository,
      CommentModerationLogJpaRepository moderationLogRepository,
      AuthUserJpaRepository authUserRepository,
      SpecimenJpaRepository specimenRepository,
      CommentAuthorRoleResolver commentAuthorRoleResolver,
      CommentMentionParser commentMentionParser,
      EconomyWalletService economyWalletService,
      EconomyLedgerJpaRepository economyLedgerRepository,
      OutboxEventStore outboxEventStore,
      CommentsPolicyProperties commentsPolicyProperties,
      JsonUtils jsonUtils) {
    this.commentRepository = commentRepository;
    this.idempotencyRepository = idempotencyRepository;
    this.resonanceRepository = resonanceRepository;
    this.reportRepository = reportRepository;
    this.moderationLogRepository = moderationLogRepository;
    this.authUserRepository = authUserRepository;
    this.specimenRepository = specimenRepository;
    this.commentAuthorRoleResolver = commentAuthorRoleResolver;
    this.commentMentionParser = commentMentionParser;
    this.economyWalletService = economyWalletService;
    this.economyLedgerRepository = economyLedgerRepository;
    this.outboxEventStore = outboxEventStore;
    this.commentsPolicyProperties = commentsPolicyProperties;
    this.jsonUtils = jsonUtils;
  }

  /**
   * Publishes one comment with economy deduction and outbox append in the same transaction.
   *
   * <p>Flow contract notes:
   *
   * <ul>
   *   <li>Write gate: specimen must be {@code ACTIVE}; {@code OFFLINED} is locked as read-only.
   *   <li>Comment id is pre-generated and used as economy deduction {@code refId}.
   *   <li>Economy deduction and comment persistence share one local transaction boundary.
   * </ul>
   */
  @Transactional
  public PublishResult publish(String userId, PublishCommand command) {
    String normalizedUserId = requireText(userId, "userId");
    String normalizedSpecimenId = requireText(command.specimenId(), "specimenId");
    String normalizedClientRequestId = normalizeClientRequestId(command.clientRequestId());

    CommentRequestIdempotencyJpaEntity existingRequest =
        idempotencyRepository
            .findByUserIdAndClientRequestId(normalizedUserId, normalizedClientRequestId)
            .orElse(null);
    if (existingRequest != null && StringUtils.hasText(existingRequest.getCommentId())) {
      return loadReplayPublishResult(existingRequest.getCommentId(), normalizedUserId);
    }

    requireSpecimenWritable(normalizedSpecimenId);
    String contentMd = normalizeAndValidateContent(command.contentMd());
    MentionResolution mentionResolution = resolveMentions(contentMd, normalizedUserId);
    ReplyReference replyReference = resolveReplyReference(normalizedSpecimenId, command.replyToCommentId());
    String commentId = nextCommentId();

    ModerationDecision moderationDecision = evaluateModeration(contentMd);
    CommentStatus initialStatus = toInitialStatus(moderationDecision.riskLevel());

    EconomyWalletService.LedgerWriteResult ledgerWriteResult =
        economyWalletService.deductBug(
            normalizedUserId,
            commentsPolicyProperties.getCommentCostBug(),
            CommentsConstants.Economy.REASON_COMMENT,
            commentId,
            CommentsConstants.Economy.REF_TYPE_COMMENT,
            economyIdempotencyKey(normalizedClientRequestId));

    CommentAuthorRepoRoleSnapshot roleSnapshot =
        resolveAuthorRepoRoleSnapshot(normalizedSpecimenId, normalizedUserId);
    CommentJpaEntity commentEntity =
        CommentJpaEntity.create(
            commentId,
            normalizedSpecimenId,
            normalizedUserId,
            replyReference.replyToCommentId(),
            replyReference.replyToUserId(),
            replyReference.toUsernameSnapshot(),
            contentMd,
            buildContentPreview(contentMd),
            EMPTY_JSON_ARRAY,
            mentionResolution.mentionedUserIdsJson(),
            roleSnapshot,
            moderationDecision.riskLevel(),
            moderationDecision.reasonCode(),
            initialStatus,
            ledgerWriteResult.ledgerId());

    commentRepository.save(commentEntity);
    appendModerationLog(
        commentEntity,
        initialStatus == CommentStatus.ACTIVE
            ? CommentModerationAction.PASS
            : CommentModerationAction.REVIEW,
        CommentModerationActorType.SYSTEM,
        null,
        commentEntity.getModerationReasonCode());
    persistRequestIdempotency(normalizedUserId, normalizedClientRequestId, commentId);
    appendOutboxEventsOnPublish(commentEntity, mentionResolution.mentionedUserIds());

    return new PublishResult(
        commentEntity.getCommentId(),
        commentEntity.getSpecimenId(),
        commentEntity.getStatus().name(),
        commentEntity.getBillingLedgerId(),
        commentsPolicyProperties.getCommentCostBug(),
        ledgerWriteResult.balanceAfter(),
        commentEntity.getCreatedAt());
  }

  /**
   * Lists comments under one specimen id.
   *
   * <p>Cursor-based pagination is reserved for later iterations; this P1 skeleton returns first page
   * only and keeps cursor output nullable.
   */
  @Transactional(readOnly = true)
  public ListResult list(ListQuery query) {
    String specimenId = requireText(query.specimenId(), "specimenId");
    String normalizedSort = normalizeSort(query.sort());
    int limit = normalizeListLimit(query.limit());

    List<CommentJpaEntity> entities;
    if (SORT_NEW.equals(normalizedSort)) {
      entities =
          commentRepository.findBySpecimenIdAndStatusInOrderByCreatedAtDesc(
              specimenId, LIST_VISIBLE_STATUSES, PageRequest.of(0, limit));
    } else {
      entities =
          commentRepository.findBySpecimenIdAndStatusInOrderByHotScoreDescCreatedAtDesc(
              specimenId, LIST_VISIBLE_STATUSES, PageRequest.of(0, limit));
    }

    List<ListItem> items = entities.stream().map(this::toListItem).toList();
    return new ListResult(items, null);
  }

  /**
   * Lists comments authored by current user.
   *
   * <p>Cursor uses simple page-index encoding in MVP stage. Contract-level opaque cursor can replace
   * this once cross-service query requirements are frozen.
   */
  @Transactional(readOnly = true)
  public MyCommentsResult myComments(String userId, MyCommentsQuery query) {
    String normalizedUserId = requireText(userId, "userId");
    int limit = normalizeListLimit(query.limit());
    int pageIndex = normalizePageCursor(query.cursor());
    MyCommentsStatusScope statusScope = normalizeMyCommentsStatus(query.status());

    List<CommentJpaEntity> entities =
        loadMyCommentsEntities(normalizedUserId, statusScope, PageRequest.of(pageIndex, limit + 1));

    boolean hasMore = entities.size() > limit;
    List<CommentJpaEntity> pageWindow = hasMore ? entities.subList(0, limit) : entities;
    Map<String, String> specimenRepoNames = loadSpecimenRepoNames(pageWindow);

    List<MyCommentsItem> items =
        pageWindow.stream()
            .map(
                entity ->
                    new MyCommentsItem(
                        entity.getCommentId(),
                        new MyCommentsSpecimen(
                            entity.getSpecimenId(), specimenRepoNames.get(entity.getSpecimenId())),
                        entity.getContentPreview(),
                        entity.getStatus().name(),
                        entity.getResonanceCount(),
                        entity.getCreatedAt(),
                        buildLocateUrl(entity.getSpecimenId(), entity.getCommentId())))
            .toList();

    String nextCursor = hasMore ? String.valueOf(pageIndex + 1) : null;
    return new MyCommentsResult(items, nextCursor, hasMore);
  }

  /**
   * Adds one resonance to an active comment.
   *
   * <p>Idempotency semantics are enforced by unique `(comment_id, user_id)` relation. Repeated
   * resonance requests from the same user return the latest count with {@code resonated=false}.
   */
  @Transactional
  public ResonanceResult resonate(String userId, ResonanceCommand command) {
    String normalizedUserId = requireText(userId, "userId");
    String commentId = requireText(command.commentId(), "commentId");
    normalizeClientRequestId(command.clientRequestId());

    CommentJpaEntity commentEntity = requireResonatableComment(commentId);
    requireSpecimenWritable(commentEntity.getSpecimenId());

    if (resonanceRepository.existsByCommentIdAndUserId(commentId, normalizedUserId)) {
      return new ResonanceResult(commentId, commentEntity.getResonanceCount(), false);
    }

    boolean inserted = tryPersistResonance(commentEntity, normalizedUserId);
    if (!inserted) {
      CommentJpaEntity latest =
          commentRepository
              .findById(commentId)
              .orElseThrow(
                  () -> CommentsExceptions.commentNotFound(CommentsConstants.Message.COMMENT_NOT_FOUND));
      return new ResonanceResult(commentId, latest.getResonanceCount(), false);
    }

    commentEntity.incrementResonance();
    commentRepository.save(commentEntity);
    appendCommentResonanceEvent(commentEntity, normalizedUserId);
    appendTopRoastUpdatedEvent(commentEntity);
    return new ResonanceResult(commentId, commentEntity.getResonanceCount(), true);
  }

  /** Returns lightweight context used by reply hover/peek interactions. */
  @Transactional(readOnly = true)
  public ContextResult context(String commentId) {
    String normalizedCommentId = requireText(commentId, "commentId");
    CommentJpaEntity commentEntity =
        commentRepository
            .findById(normalizedCommentId)
            .orElseThrow(
                () -> CommentsExceptions.commentNotFound(CommentsConstants.Message.COMMENT_NOT_FOUND));

    if (commentEntity.getStatus() == CommentStatus.DELETED) {
      throw CommentsExceptions.contextNotVisible(CommentsConstants.Message.CONTEXT_NOT_VISIBLE);
    }
    if (commentEntity.getStatus() != CommentStatus.ACTIVE) {
      throw CommentsExceptions.commentNotFound(CommentsConstants.Message.COMMENT_NOT_FOUND);
    }

    return new ContextResult(
        commentEntity.getCommentId(),
        new ContextAuthor(commentEntity.getAuthorUserId(), null, null),
        commentEntity.getContentPreview(),
        commentEntity.getStatus().name());
  }

  /**
   * Soft-deletes one comment.
   *
   * <p>Delete never refunds bug consumption. Author self-delete and admin delete share the same
   * persistence semantics.
   */
  @Transactional
  public DeleteResult delete(String userId, DeleteCommand command) {
    String normalizedUserId = requireText(userId, "userId");
    String commentId = requireText(command.commentId(), "commentId");

    CommentJpaEntity commentEntity =
        commentRepository
            .findById(commentId)
            .orElseThrow(
                () -> CommentsExceptions.commentNotFound(CommentsConstants.Message.COMMENT_NOT_FOUND));
    if (commentEntity.getStatus() == CommentStatus.DELETED) {
      throw CommentsExceptions.alreadyDeleted(CommentsConstants.Message.ALREADY_DELETED);
    }
    if (!command.adminOperator() && !Objects.equals(commentEntity.getAuthorUserId(), normalizedUserId)) {
      throw CommentsExceptions.forbidden(CommentsConstants.Message.FORBIDDEN);
    }
    performDelete(
        commentEntity,
        normalizedUserId,
        command.adminOperator()
            ? CommentModerationActorType.ADMIN
            : CommentModerationActorType.SYSTEM,
        command.adminOperator() ? normalizedUserId : null);
    return new DeleteResult(true, 0);
  }

  /**
   * Returns one top-roast summary for the given specimen.
   *
   * <p>Only ACTIVE comments are considered. If no candidate reaches the configured threshold,
   * response keeps `hasTopRoast=false`.
   */
  @Transactional(readOnly = true)
  public TopRoastResult topRoast(String specimenId) {
    String normalizedSpecimenId = requireText(specimenId, "specimenId");
    requireSpecimenExists(normalizedSpecimenId);

    CommentJpaEntity candidate =
        commentRepository
            .findFirstBySpecimenIdAndStatusOrderByHotScoreDescCreatedAtDesc(
                normalizedSpecimenId, CommentStatus.ACTIVE)
            .orElse(null);
    if (candidate == null
        || candidate.getResonanceCount() < commentsPolicyProperties.getChiefConclusionThreshold()) {
      return TopRoastResult.empty();
    }

    return new TopRoastResult(
        true,
        candidate.getCommentId(),
        new TopRoastAuthor(candidate.getAuthorUserId(), null, null),
        candidate.getContentPreview(),
        candidate.getResonanceCount());
  }

  /**
   * Writes one report record and emits `CommentReportedEvent` for M07 ticket intake.
   *
   * <p>M07 integration is still skeleton-level. `ticketId` currently uses deterministic placeholder
   * derived from local report id.
   */
  @Transactional
  public ReportResult report(String userId, ReportCommand command) {
    String normalizedUserId = requireText(userId, "userId");
    String commentId = requireText(command.commentId(), "commentId");
    CommentReportReasonCode reasonCode = normalizeReportReason(command.reasonCode());
    String message = normalizeReportMessage(command.message());
    normalizeClientRequestId(command.clientRequestId());

    CommentJpaEntity commentEntity = requireResonatableComment(commentId);
    requireSpecimenWritable(commentEntity.getSpecimenId());

    LocalDate dateBucket = LocalDate.now(ZoneOffset.UTC);
    String reportId = nextReportId();
    String ticketId = "tkt_" + reportId;
    CommentReportJpaEntity reportEntity;
    try {
      reportEntity =
          reportRepository.save(
              CommentReportJpaEntity.create(
                  reportId,
                  commentEntity.getCommentId(),
                  commentEntity.getSpecimenId(),
                  normalizedUserId,
                  reasonCode,
                  message,
                  dateBucket,
                  ticketId));
    } catch (DataIntegrityViolationException ex) {
      // Windowed uniqueness keeps report flow idempotent under rapid repeated submissions.
      throw CommentsExceptions.duplicateReport(CommentsConstants.Message.DUPLICATE_REPORT);
    }

    appendCommentReportedEvent(reportEntity);
    return new ReportResult(true, reportEntity.getTicketId());
  }

  /** Lists comments waiting for moderation review in admin queue. */
  @Transactional(readOnly = true)
  public CommentAdminModels.ModerationQueuePage listModerationQueue(Integer page, Integer pageSize) {
    int resolvedPage = normalizeAdminPage(page);
    int resolvedPageSize = normalizeAdminPageSize(pageSize);
    var fetched =
        commentRepository.findAllByStatusOrderByCreatedAtDesc(
            CommentStatus.PENDING_REVIEW, PageRequest.of(resolvedPage - 1, resolvedPageSize));
    List<CommentAdminModels.ModerationQueueItem> items =
        fetched.getContent().stream().map(this::toModerationQueueItem).toList();
    return new CommentAdminModels.ModerationQueuePage(
        items,
        resolvedPage,
        resolvedPageSize,
        (int) fetched.getTotalElements(),
        fetched.getTotalPages());
  }

  /** Admin block action. Handles REVIEW reject refund or ACTIVE block flows. */
  @Transactional
  public CommentAdminModels.ModerationActionResult blockComment(
      String adminUserId, String commentId, String idempotencyKey) {
    String normalizedAdminUserId = requireText(adminUserId, "adminUserId");
    String normalizedCommentId = requireText(commentId, "commentId");

    CommentJpaEntity commentEntity =
        commentRepository
            .findById(normalizedCommentId)
            .orElseThrow(
                () -> CommentsExceptions.commentNotFound(CommentsConstants.Message.COMMENT_NOT_FOUND));

    CommentStatus currentStatus = commentEntity.getStatus();
    if (currentStatus != CommentStatus.ACTIVE && currentStatus != CommentStatus.PENDING_REVIEW) {
      throw CommentsExceptions.invalidStatus(CommentsConstants.Message.INVALID_STATUS);
    }

    CommentAdminModels.ModerationSnapshot before = toModerationSnapshot(commentEntity);
    int refundAmount = 0;
    if (currentStatus == CommentStatus.PENDING_REVIEW) {
      commentEntity.updateStatus(CommentStatus.BLOCKED);
      commentRepository.save(commentEntity);
      appendModerationLog(
          commentEntity,
          CommentModerationAction.REJECT,
          CommentModerationActorType.ADMIN,
          normalizedAdminUserId,
          commentEntity.getModerationReasonCode());
      refundAmount = refundReviewBug(commentEntity, idempotencyKey);
      appendCommentStatusChangedEvent(
          commentEntity,
          currentStatus,
          CommentStatus.BLOCKED,
          CommentsConstants.Outbox.ACTOR_TYPE_ADMIN);
    } else {
      boolean wasChief = commentEntity.clearChiefConclusion();
      commentEntity.updateStatus(CommentStatus.BLOCKED);
      commentRepository.save(commentEntity);
      appendModerationLog(
          commentEntity,
          CommentModerationAction.BLOCK,
          CommentModerationActorType.ADMIN,
          normalizedAdminUserId,
          null);
      appendCommentCountChangedEvent(
          commentEntity, -1, CommentsConstants.Outbox.TRIGGER_ACTION_BLOCK);
      appendCommentStatusChangedEvent(
          commentEntity,
          currentStatus,
          CommentStatus.BLOCKED,
          CommentsConstants.Outbox.ACTOR_TYPE_ADMIN);
      if (wasChief) {
        appendTopRoastUpdatedEvent(commentEntity);
      }
    }

    CommentAdminModels.ModerationSnapshot after = toModerationSnapshot(commentEntity);
    return new CommentAdminModels.ModerationActionResult(before, after, refundAmount);
  }

  /** Admin unblock action. */
  @Transactional
  public CommentAdminModels.ModerationActionResult unblockComment(
      String adminUserId, String commentId) {
    String normalizedAdminUserId = requireText(adminUserId, "adminUserId");
    String normalizedCommentId = requireText(commentId, "commentId");

    CommentJpaEntity commentEntity =
        commentRepository
            .findById(normalizedCommentId)
            .orElseThrow(
                () -> CommentsExceptions.commentNotFound(CommentsConstants.Message.COMMENT_NOT_FOUND));

    CommentStatus currentStatus = commentEntity.getStatus();
    if (currentStatus != CommentStatus.BLOCKED) {
      throw CommentsExceptions.invalidStatus(CommentsConstants.Message.INVALID_STATUS);
    }

    CommentAdminModels.ModerationSnapshot before = toModerationSnapshot(commentEntity);
    commentEntity.updateStatus(CommentStatus.ACTIVE);
    commentRepository.save(commentEntity);
    appendModerationLog(
        commentEntity,
        CommentModerationAction.UNBLOCK,
        CommentModerationActorType.ADMIN,
        normalizedAdminUserId,
        null);
    appendCommentCountChangedEvent(
        commentEntity, 1, CommentsConstants.Outbox.TRIGGER_ACTION_UNBLOCK);
    appendCommentStatusChangedEvent(
        commentEntity,
        currentStatus,
        CommentStatus.ACTIVE,
        CommentsConstants.Outbox.ACTOR_TYPE_ADMIN);
    appendTopRoastUpdatedEvent(commentEntity);

    CommentAdminModels.ModerationSnapshot after = toModerationSnapshot(commentEntity);
    return new CommentAdminModels.ModerationActionResult(before, after, 0);
  }

  /** Admin approve action for pending review comments. */
  @Transactional
  public CommentAdminModels.ModerationActionResult approveComment(
      String adminUserId, String commentId) {
    String normalizedAdminUserId = requireText(adminUserId, "adminUserId");
    String normalizedCommentId = requireText(commentId, "commentId");

    CommentJpaEntity commentEntity =
        commentRepository
            .findById(normalizedCommentId)
            .orElseThrow(
                () -> CommentsExceptions.commentNotFound(CommentsConstants.Message.COMMENT_NOT_FOUND));

    CommentStatus currentStatus = commentEntity.getStatus();
    if (currentStatus != CommentStatus.PENDING_REVIEW) {
      throw CommentsExceptions.invalidStatus(CommentsConstants.Message.INVALID_STATUS);
    }

    CommentAdminModels.ModerationSnapshot before = toModerationSnapshot(commentEntity);
    commentEntity.updateStatus(CommentStatus.ACTIVE);
    commentRepository.save(commentEntity);
    appendModerationLog(
        commentEntity,
        CommentModerationAction.APPROVE,
        CommentModerationActorType.ADMIN,
        normalizedAdminUserId,
        null);
    appendCommentPublishedEvent(commentEntity);
    appendCommentCountChangedEvent(
        commentEntity, 1, CommentsConstants.Outbox.TRIGGER_ACTION_APPROVE);
    appendCommentStatusChangedEvent(
        commentEntity,
        currentStatus,
        CommentStatus.ACTIVE,
        CommentsConstants.Outbox.ACTOR_TYPE_ADMIN);
    List<String> mentionedUserIds = parseMentionedUserIds(commentEntity.getMentionedUserIds());
    if (!mentionedUserIds.isEmpty()) {
      appendCommentMentionEvent(commentEntity, mentionedUserIds);
    }
    appendTopRoastUpdatedEvent(commentEntity);

    CommentAdminModels.ModerationSnapshot after = toModerationSnapshot(commentEntity);
    return new CommentAdminModels.ModerationActionResult(before, after, 0);
  }

  /** Admin delete action for one comment. */
  @Transactional
  public CommentAdminModels.ModerationActionResult deleteByAdmin(
      String adminUserId, String commentId) {
    String normalizedAdminUserId = requireText(adminUserId, "adminUserId");
    String normalizedCommentId = requireText(commentId, "commentId");

    CommentJpaEntity commentEntity =
        commentRepository
            .findById(normalizedCommentId)
            .orElseThrow(
                () -> CommentsExceptions.commentNotFound(CommentsConstants.Message.COMMENT_NOT_FOUND));
    if (commentEntity.getStatus() == CommentStatus.DELETED) {
      throw CommentsExceptions.alreadyDeleted(CommentsConstants.Message.ALREADY_DELETED);
    }

    return performDelete(
        commentEntity,
        normalizedAdminUserId,
        CommentModerationActorType.ADMIN,
        normalizedAdminUserId);
  }

  private ListItem toListItem(CommentJpaEntity entity) {
    if (entity.getStatus() == CommentStatus.DELETED) {
      return new ListItem(
          entity.getCommentId(),
          entity.getSpecimenId(),
          null,
          commentsPolicyProperties.getDeletedPlaceholder(),
          0,
          false,
          entity.getStatus().name(),
          entity.getCreatedAt(),
          entity.getUpdatedAt());
    }

    return new ListItem(
        entity.getCommentId(),
        entity.getSpecimenId(),
        entity.getAuthorUserId(),
        entity.getContentPreview(),
        entity.getResonanceCount(),
        entity.isChiefConclusion(),
        entity.getStatus().name(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private CommentAdminModels.ModerationQueueItem toModerationQueueItem(CommentJpaEntity entity) {
    return new CommentAdminModels.ModerationQueueItem(
        entity.getCommentId(),
        entity.getSpecimenId(),
        entity.getAuthorUserId(),
        entity.getContentPreview(),
        entity.getStatus().name(),
        entity.getModerationRiskLevel() != null ? entity.getModerationRiskLevel().name() : null,
        entity.getModerationReasonCode(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private CommentAdminModels.ModerationSnapshot toModerationSnapshot(CommentJpaEntity entity) {
    return new CommentAdminModels.ModerationSnapshot(
        entity.getCommentId(),
        entity.getSpecimenId(),
        entity.getAuthorUserId(),
        entity.getStatus().name(),
        entity.isChiefConclusion(),
        entity.getResonanceCount());
  }

  private CommentAdminModels.ModerationActionResult performDelete(
      CommentJpaEntity commentEntity,
      String deletedBy,
      CommentModerationActorType actorType,
      String actorId) {
    CommentAdminModels.ModerationSnapshot before = toModerationSnapshot(commentEntity);
    CommentStatus oldStatus = commentEntity.getStatus();
    commentEntity.markDeleted(deletedBy);
    commentRepository.save(commentEntity);
    appendModerationLog(
        commentEntity, CommentModerationAction.DELETE, actorType, actorId, null);

    if (oldStatus == CommentStatus.ACTIVE) {
      appendCommentCountChangedEvent(
          commentEntity, -1, CommentsConstants.Outbox.TRIGGER_ACTION_DELETE);
    }
    appendCommentStatusChangedEvent(
        commentEntity,
        oldStatus,
        CommentStatus.DELETED,
        actorType == CommentModerationActorType.ADMIN
            ? CommentsConstants.Outbox.ACTOR_TYPE_ADMIN
            : CommentsConstants.Outbox.ACTOR_TYPE_USER);
    appendTopRoastUpdatedEvent(commentEntity);
    CommentAdminModels.ModerationSnapshot after = toModerationSnapshot(commentEntity);
    return new CommentAdminModels.ModerationActionResult(before, after, 0);
  }

  private int normalizePageCursor(String cursor) {
    if (!StringUtils.hasText(cursor)) {
      return 0;
    }
    try {
      int pageIndex = Integer.parseInt(cursor.trim());
      if (pageIndex < 0) {
        throw CommentsExceptions.invalidCursor(CommentsConstants.Message.INVALID_CURSOR);
      }
      return pageIndex;
    } catch (NumberFormatException ex) {
      throw CommentsExceptions.invalidCursor(CommentsConstants.Message.INVALID_CURSOR);
    }
  }

  private MyCommentsStatusScope normalizeMyCommentsStatus(String status) {
    if (!StringUtils.hasText(status)) {
      return MyCommentsStatusScope.all();
    }

    String normalized = status.trim().toUpperCase(Locale.ROOT);
    if (MY_COMMENTS_STATUS_ALL.equals(normalized)) {
      return MyCommentsStatusScope.all();
    }

    try {
      CommentStatus commentStatus = CommentStatus.valueOf(normalized);
      if (!MY_COMMENTS_FILTERABLE_STATUSES.contains(commentStatus)) {
        throw CommentsExceptions.invalidSort(CommentsConstants.Message.INVALID_SORT);
      }
      return MyCommentsStatusScope.single(commentStatus);
    } catch (IllegalArgumentException ex) {
      throw CommentsExceptions.invalidSort(CommentsConstants.Message.INVALID_SORT);
    }
  }

  private List<CommentJpaEntity> loadMyCommentsEntities(
      String userId, MyCommentsStatusScope statusScope, PageRequest pageRequest) {
    if (statusScope.allStatuses()) {
      return commentRepository.findByAuthorUserIdOrderByCreatedAtDesc(userId, pageRequest);
    }
    return commentRepository.findByAuthorUserIdAndStatusInOrderByCreatedAtDesc(
        userId, statusScope.statuses(), pageRequest);
  }

  private Map<String, String> loadSpecimenRepoNames(List<CommentJpaEntity> comments) {
    Map<String, String> repoNames = new HashMap<>();
    comments.stream()
        .map(CommentJpaEntity::getSpecimenId)
        .distinct()
        .forEach(
            specimenId ->
                repoNames.put(
                    specimenId,
                    specimenRepository.findById(specimenId).map(SpecimenJpaEntity::getRepoFullName).orElse(null)));
    return repoNames;
  }

  private String buildLocateUrl(String specimenId, String commentId) {
    return "/specimen/" + specimenId + "#" + commentId;
  }

  private void requireSpecimenWritable(String specimenId) {
    SpecimenJpaEntity specimen =
        specimenRepository
            .findById(specimenId)
            .orElseThrow(
                () -> CommentsExceptions.specimenNotFound(CommentsConstants.Message.SPECIMEN_NOT_FOUND));

    if (specimen.getStatus() == SpecimenStatus.OFFLINED) {
      throw CommentsExceptions.specimenLocked(CommentsConstants.Message.SPECIMEN_LOCKED);
    }
    if (specimen.getStatus() != SpecimenStatus.ACTIVE) {
      throw CommentsExceptions.specimenNotActive(CommentsConstants.Message.SPECIMEN_NOT_ACTIVE);
    }
  }

  private void requireSpecimenExists(String specimenId) {
    if (specimenRepository.findById(specimenId).isEmpty()) {
      throw CommentsExceptions.specimenNotFound(CommentsConstants.Message.SPECIMEN_NOT_FOUND);
    }
  }

  private ReplyReference resolveReplyReference(String specimenId, String replyToCommentId) {
    if (!StringUtils.hasText(replyToCommentId)) {
      return ReplyReference.empty();
    }

    CommentJpaEntity targetComment =
        commentRepository
            .findById(replyToCommentId.trim())
            .orElseThrow(
                () -> CommentsExceptions.commentNotFound(CommentsConstants.Message.COMMENT_NOT_FOUND));
    if (!Objects.equals(targetComment.getSpecimenId(), specimenId)) {
      throw CommentsExceptions.commentNotFound(CommentsConstants.Message.COMMENT_NOT_FOUND);
    }
    if (targetComment.getStatus() != CommentStatus.ACTIVE) {
      throw CommentsExceptions.commentNotFound(CommentsConstants.Message.COMMENT_NOT_FOUND);
    }
    return new ReplyReference(targetComment.getCommentId(), targetComment.getAuthorUserId(), null);
  }

  private CommentJpaEntity requireResonatableComment(String commentId) {
    CommentJpaEntity commentEntity =
        commentRepository
            .findById(commentId)
            .orElseThrow(
                () -> CommentsExceptions.commentNotFound(CommentsConstants.Message.COMMENT_NOT_FOUND));
    if (commentEntity.getStatus() != CommentStatus.ACTIVE) {
      throw CommentsExceptions.commentNotFound(CommentsConstants.Message.COMMENT_NOT_FOUND);
    }
    return commentEntity;
  }

  private boolean tryPersistResonance(CommentJpaEntity commentEntity, String userId) {
    try {
      resonanceRepository.save(
          CommentResonanceJpaEntity.create(
              nextResonanceId(), commentEntity.getCommentId(), commentEntity.getSpecimenId(), userId));
      return true;
    } catch (DataIntegrityViolationException ex) {
      // Unique constraint `(comment_id, user_id)` keeps this path idempotent under race.
      return false;
    }
  }

  private CommentReportReasonCode normalizeReportReason(String reasonCode) {
    CommentReportReasonCode normalized = CommentReportReasonCode.fromRaw(reasonCode);
    if (normalized == null) {
      throw CommentsExceptions.invalidReason(CommentsConstants.Message.INVALID_REASON);
    }
    return normalized;
  }

  private String normalizeReportMessage(String message) {
    if (!StringUtils.hasText(message)) {
      return null;
    }
    String normalized = message.trim();
    if (normalized.length() > commentsPolicyProperties.getReportMessageMaxLength()) {
      throw CommentsExceptions.contentTooLong(CommentsConstants.Message.CONTENT_TOO_LONG);
    }
    return normalized;
  }

  /**
   * Resolves @mention usernames in markdown to stable user ids.
   *
   * <p>This method is the single write-path source for `mentioned_user_ids` and mention outbox
   * payloads.
   */
  private MentionResolution resolveMentions(String contentMd, String authorUserId) {
    LinkedHashSet<String> usernames =
        new LinkedHashSet<>(commentMentionParser.extractMentionedUsernames(contentMd));
    if (usernames.isEmpty()) {
      return MentionResolution.empty();
    }
    if (usernames.size() > commentsPolicyProperties.getMentionMaxCount()) {
      throw CommentsExceptions.tooManyMentions(CommentsConstants.Message.TOO_MANY_MENTIONS);
    }

    List<String> mentionedUserIds =
        usernames.stream().map(username -> resolveMentionedUserId(username, authorUserId)).toList();
    return new MentionResolution(mentionedUserIds, toJsonArray(mentionedUserIds));
  }

  private CommentAuthorRepoRoleSnapshot resolveAuthorRepoRoleSnapshot(
      String specimenId, String userId) {
    try {
      CommentAuthorRepoRoleSnapshot resolved =
          commentAuthorRoleResolver.resolveRole(specimenId, userId);
      return resolved != null ? resolved : CommentAuthorRepoRoleSnapshot.NONE;
    } catch (RuntimeException ex) {
      log.warn(
          "comment_author_role_resolve_failed specimenId={} userId={}",
          specimenId,
          userId,
          ex);
      return CommentAuthorRepoRoleSnapshot.NONE;
    }
  }

  private String resolveMentionedUserId(String username, String authorUserId) {
    // TODO(M05/M07): add banned-user filtering after auth/profile domain exposes moderation state.
    String mentionedUserId =
        authUserRepository
            .findByUsername(username)
            .map(AuthUserJpaEntity::getUserId)
            .orElseThrow(
                () ->
                    CommentsExceptions.invalidMentionUser(
                        CommentsConstants.Message.INVALID_MENTION_USER));

    if (Objects.equals(authorUserId, mentionedUserId)) {
      throw CommentsExceptions.mentionSelfNotAllowed(
          CommentsConstants.Message.MENTION_SELF_NOT_ALLOWED);
    }
    return mentionedUserId;
  }

  private String toJsonArray(List<String> values) {
    if (values.isEmpty()) {
      return EMPTY_JSON_ARRAY;
    }
    return values.stream()
        .map(value -> "\"" + value + "\"")
        .collect(Collectors.joining(",", "[", "]"));
  }

  private String normalizeAndValidateContent(String contentMd) {
    if (!StringUtils.hasText(contentMd)) {
      throw CommentsExceptions.invalidContent(CommentsConstants.Message.INVALID_CONTENT);
    }
    String normalized = contentMd.trim();
    if (normalized.isEmpty()) {
      throw CommentsExceptions.invalidContent(CommentsConstants.Message.INVALID_CONTENT);
    }
    if (normalized.length() > commentsPolicyProperties.getContentMaxLength()) {
      throw CommentsExceptions.contentTooLong(CommentsConstants.Message.CONTENT_TOO_LONG);
    }
    return normalized;
  }

  private String normalizeSort(String sort) {
    if (!StringUtils.hasText(sort)) {
      return DEFAULT_LIST_SORT;
    }
    String normalized = sort.trim().toLowerCase();
    if (SORT_HOT.equals(normalized) || SORT_NEW.equals(normalized)) {
      return normalized;
    }
    throw CommentsExceptions.invalidSort(CommentsConstants.Message.INVALID_SORT);
  }

  private int normalizeListLimit(Integer limit) {
    if (limit == null || limit <= 0) {
      return commentsPolicyProperties.getListDefaultLimit();
    }
    return Math.min(limit, commentsPolicyProperties.getListMaxLimit());
  }

  private int normalizeAdminPage(Integer page) {
    if (page == null || page < 1) {
      return 1;
    }
    return page;
  }

  private int normalizeAdminPageSize(Integer pageSize) {
    if (pageSize == null || pageSize < 1) {
      return DEFAULT_ADMIN_PAGE_SIZE;
    }
    return Math.min(pageSize, MAX_ADMIN_PAGE_SIZE);
  }

  private String buildContentPreview(String contentMd) {
    String normalized = contentMd.replaceAll("\\s+", " ").trim();
    int previewLength = Math.min(200, normalized.length());
    return normalized.substring(0, previewLength);
  }

  private List<String> parseMentionedUserIds(String mentionedUserIds) {
    if (!StringUtils.hasText(mentionedUserIds)) {
      return List.of();
    }
    String normalized = mentionedUserIds.trim();
    if (normalized.isEmpty() || EMPTY_JSON_ARRAY.equals(normalized)) {
      return List.of();
    }
    try {
      return jsonUtils.mapper().readValue(normalized, STRING_LIST);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to parse mentioned user ids", ex);
    }
  }

  private void appendModerationLog(
      CommentJpaEntity commentEntity,
      CommentModerationAction action,
      CommentModerationActorType actorType,
      String actorId,
      String reasonCode) {
    moderationLogRepository.save(
        CommentModerationLogJpaEntity.create(
            commentEntity.getCommentId(),
            commentEntity.getSpecimenId(),
            commentEntity.getAuthorUserId(),
            action,
            reasonCode,
            actorType,
            actorId));
  }

  private int refundReviewBug(CommentJpaEntity commentEntity, String idempotencyKey) {
    if (!StringUtils.hasText(commentEntity.getBillingLedgerId())) {
      return 0;
    }
    EconomyLedgerJpaEntity ledger =
        economyLedgerRepository.findById(commentEntity.getBillingLedgerId()).orElse(null);
    if (ledger == null) {
      return 0;
    }
    long delta = ledger.getDelta();
    int refundAmount = (int) Math.abs(delta);
    if (refundAmount <= 0) {
      return 0;
    }
    economyWalletService.creditBug(
        commentEntity.getAuthorUserId(),
        refundAmount,
        "COMMENT_REVIEW_REFUND",
        commentEntity.getCommentId(),
        CommentsConstants.Economy.REF_TYPE_COMMENT,
        idempotencyKey);
    return refundAmount;
  }

  private ModerationDecision evaluateModeration(String contentMd) {
    // TODO(M05-comments): replace with real moderation adapter after contract freeze.
    return new ModerationDecision(CommentModerationRiskLevel.PASS, null);
  }

  private CommentStatus toInitialStatus(CommentModerationRiskLevel riskLevel) {
    if (riskLevel == CommentModerationRiskLevel.PASS) {
      return CommentStatus.ACTIVE;
    }
    return CommentStatus.PENDING_REVIEW;
  }

  private void persistRequestIdempotency(String userId, String clientRequestId, String commentId) {
    try {
      idempotencyRepository.save(
          CommentRequestIdempotencyJpaEntity.create(
              nextRequestIdempotencyId(), userId, clientRequestId, commentId));
    } catch (DataIntegrityViolationException ex) {
      CommentRequestIdempotencyJpaEntity existing =
          idempotencyRepository.findByUserIdAndClientRequestId(userId, clientRequestId).orElse(null);
      if (existing != null && Objects.equals(existing.getCommentId(), commentId)) {
        return;
      }
      throw CommentsExceptions.idempotencyConflict(CommentsConstants.Message.IDEMPOTENCY_CONFLICT);
    }
  }

  private PublishResult loadReplayPublishResult(String commentId, String userId) {
    CommentJpaEntity existingComment =
        commentRepository
            .findById(commentId)
            .orElseThrow(
                () -> CommentsExceptions.idempotencyConflict(CommentsConstants.Message.IDEMPOTENCY_CONFLICT));
    long balanceAfter = economyWalletService.currentBalance(userId);
    return new PublishResult(
        existingComment.getCommentId(),
        existingComment.getSpecimenId(),
        existingComment.getStatus().name(),
        existingComment.getBillingLedgerId(),
        commentsPolicyProperties.getCommentCostBug(),
        balanceAfter,
        existingComment.getCreatedAt());
  }

  private void appendOutboxEventsOnPublish(
      CommentJpaEntity commentEntity, List<String> mentionedUserIds) {
    if (commentEntity.getStatus() != CommentStatus.ACTIVE) {
      // Mention/publish notifications are emitted only when comment is externally visible.
      // Pending-review comments should emit equivalent events in future approve workflow.
      return;
    }

    appendCommentPublishedEvent(commentEntity);
    appendCommentCountChangedEvent(commentEntity, 1, CommentsConstants.Outbox.TRIGGER_ACTION_PUBLISH);
    if (!mentionedUserIds.isEmpty()) {
      appendCommentMentionEvent(commentEntity, mentionedUserIds);
    }
    appendTopRoastUpdatedEvent(commentEntity);
  }

  private void appendCommentPublishedEvent(CommentJpaEntity commentEntity) {
    CommentPublishedEventPayload payload =
        new CommentPublishedEventPayload(
            commentEntity.getCommentId(),
            commentEntity.getSpecimenId(),
            commentEntity.getAuthorUserId(),
            commentEntity.getReplyToUserId(),
            commentEntity.getStatus().name());
    outboxEventStore.append(
        new OutboxEventCommand(
            CommentsConstants.Outbox.AGGREGATE_TYPE_COMMENT,
            commentEntity.getCommentId(),
            CommentsConstants.Outbox.EVENT_COMMENT_PUBLISHED,
            "comments:published:" + commentEntity.getCommentId(),
            payload,
            Instant.now()));
  }

  private void appendCommentResonanceEvent(CommentJpaEntity commentEntity, String resonatorUserId) {
    CommentResonanceEventPayload payload =
        new CommentResonanceEventPayload(
            commentEntity.getCommentId(),
            commentEntity.getSpecimenId(),
            resonatorUserId,
            commentEntity.getAuthorUserId(),
            commentEntity.getResonanceCount());
    outboxEventStore.append(
        new OutboxEventCommand(
            CommentsConstants.Outbox.AGGREGATE_TYPE_COMMENT,
            commentEntity.getCommentId(),
            CommentsConstants.Outbox.EVENT_COMMENT_RESONANCE,
            "comments:resonance:" + commentEntity.getCommentId() + ":" + resonatorUserId,
            payload,
            Instant.now()));
  }

  private void appendCommentMentionEvent(CommentJpaEntity commentEntity, List<String> mentionedUserIds) {
    CommentMentionEventPayload payload =
        new CommentMentionEventPayload(
            commentEntity.getCommentId(),
            commentEntity.getSpecimenId(),
            commentEntity.getAuthorUserId(),
            mentionedUserIds);
    outboxEventStore.append(
        new OutboxEventCommand(
            CommentsConstants.Outbox.AGGREGATE_TYPE_COMMENT,
            commentEntity.getCommentId(),
            CommentsConstants.Outbox.EVENT_COMMENT_MENTION,
            "comments:mention:" + commentEntity.getCommentId(),
            payload,
            Instant.now()));
  }

  private void appendCommentReportedEvent(CommentReportJpaEntity reportEntity) {
    CommentReportedEventPayload payload =
        new CommentReportedEventPayload(
            reportEntity.getId(),
            reportEntity.getCommentId(),
            reportEntity.getSpecimenId(),
            reportEntity.getReporterUserId(),
            reportEntity.getReasonCode().name(),
            reportEntity.getMessage());
    outboxEventStore.append(
        new OutboxEventCommand(
            CommentsConstants.Outbox.AGGREGATE_TYPE_COMMENT,
            reportEntity.getCommentId(),
            CommentsConstants.Outbox.EVENT_COMMENT_REPORTED,
            "comments:reported:" + reportEntity.getId(),
            payload,
            Instant.now()));
  }

  private void appendCommentCountChangedEvent(
      CommentJpaEntity commentEntity, int delta, String triggerAction) {
    CommentCountChangedEventPayload payload =
        new CommentCountChangedEventPayload(
            commentEntity.getSpecimenId(), delta, commentEntity.getCommentId(), triggerAction);
    outboxEventStore.append(
        new OutboxEventCommand(
            CommentsConstants.Outbox.AGGREGATE_TYPE_SPECIMEN,
            commentEntity.getSpecimenId(),
            CommentsConstants.Outbox.EVENT_COMMENT_COUNT_CHANGED,
            "comments:count-changed:" + commentEntity.getCommentId(),
            payload,
            Instant.now()));
  }

  private void appendCommentStatusChangedEvent(
      CommentJpaEntity commentEntity,
      CommentStatus oldStatus,
      CommentStatus newStatus,
      String actorType) {
    CommentStatusChangedEventPayload payload =
        new CommentStatusChangedEventPayload(
            commentEntity.getCommentId(),
            commentEntity.getSpecimenId(),
            commentEntity.getAuthorUserId(),
            oldStatus.name(),
            newStatus.name(),
            actorType);
    outboxEventStore.append(
        new OutboxEventCommand(
            CommentsConstants.Outbox.AGGREGATE_TYPE_COMMENT,
            commentEntity.getCommentId(),
            CommentsConstants.Outbox.EVENT_COMMENT_STATUS_CHANGED,
            "comments:status-changed:" + commentEntity.getCommentId() + ":" + newStatus.name(),
            payload,
            Instant.now()));
  }

  private void appendTopRoastUpdatedEvent(CommentJpaEntity commentEntity) {
    // Placeholder producer in P1: selection/recompute strategy will be finalized in M05 P2.
    TopRoastUpdatedEventPayload payload =
        new TopRoastUpdatedEventPayload(
            commentEntity.getSpecimenId(),
            commentEntity.getCommentId(),
            commentEntity.getResonanceCount(),
            commentEntity.isChiefConclusion());
    outboxEventStore.append(
        new OutboxEventCommand(
            CommentsConstants.Outbox.AGGREGATE_TYPE_SPECIMEN,
            commentEntity.getSpecimenId(),
            CommentsConstants.Outbox.EVENT_TOP_ROAST_UPDATED,
            "comments:top-roast:" + commentEntity.getSpecimenId(),
            payload,
            Instant.now()));
  }

  private String economyIdempotencyKey(String clientRequestId) {
    return CommentsConstants.Id.ECONOMY_DEDUCT_IDEMPOTENCY_PREFIX + clientRequestId;
  }

  private String nextCommentId() {
    return CommentsConstants.Id.COMMENT_PREFIX + UUID.randomUUID().toString().replace("-", "");
  }

  private String nextRequestIdempotencyId() {
    return CommentsConstants.Id.REQUEST_IDEMPOTENCY_PREFIX
        + UUID.randomUUID().toString().replace("-", "");
  }

  private String nextResonanceId() {
    return CommentsConstants.Id.RESONANCE_PREFIX + UUID.randomUUID().toString().replace("-", "");
  }

  private String nextReportId() {
    return CommentsConstants.Id.REPORT_PREFIX + UUID.randomUUID().toString().replace("-", "");
  }

  private String normalizeClientRequestId(String clientRequestId) {
    String normalized = requireText(clientRequestId, "clientRequestId");
    if (normalized.length() > 128) {
      throw CommentsExceptions.idempotencyConflict(CommentsConstants.Message.IDEMPOTENCY_CONFLICT);
    }
    return normalized;
  }

  private String requireText(String value, String fieldName) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value.trim();
  }

  public record PublishCommand(
      String specimenId, String contentMd, String replyToCommentId, String clientRequestId) {}

  public record PublishResult(
      String commentId,
      String specimenId,
      String status,
      String billingLedgerId,
      int bugCost,
      long balanceAfter,
      Instant createdAt) {}

  public record ListQuery(String specimenId, String sort, String cursor, Integer limit) {}

  public record ListResult(List<ListItem> items, String nextCursor) {}

  public record MyCommentsQuery(String cursor, Integer limit, String status) {}

  public record MyCommentsResult(List<MyCommentsItem> items, String nextCursor, boolean hasMore) {}

  public record MyCommentsItem(
      String commentId,
      MyCommentsSpecimen specimen,
      String contentPreview,
      String status,
      int resonanceCount,
      Instant createdAt,
      String locateUrl) {}

  public record MyCommentsSpecimen(String specimenId, String repoFullName) {}

  public record ListItem(
      String commentId,
      String specimenId,
      String authorUserId,
      String contentPreview,
      int resonanceCount,
      boolean isChiefConclusion,
      String status,
      Instant createdAt,
      Instant updatedAt) {}

  public record ResonanceCommand(String commentId, String clientRequestId) {}

  public record ResonanceResult(String commentId, int resonanceCount, boolean resonated) {}

  public record ContextResult(String commentId, ContextAuthor author, String contentPreview, String status) {}

  public record ContextAuthor(String userId, String username, String avatarUrl) {}

  public record DeleteCommand(String commentId, boolean adminOperator) {}

  public record DeleteResult(boolean deleted, int refundDelta) {}

  public record TopRoastResult(
      boolean hasTopRoast,
      String commentId,
      TopRoastAuthor author,
      String contentPreview,
      Integer resonanceCount) {

    private static TopRoastResult empty() {
      return new TopRoastResult(false, null, null, null, null);
    }
  }

  public record TopRoastAuthor(String userId, String username, String avatarUrl) {}

  public record ReportCommand(
      String commentId, String reasonCode, String message, String clientRequestId) {}

  public record ReportResult(boolean reported, String ticketId) {}

  private record ReplyReference(
      String replyToCommentId, String replyToUserId, String toUsernameSnapshot) {

    private static ReplyReference empty() {
      return new ReplyReference(null, null, null);
    }
  }

  private record MyCommentsStatusScope(boolean allStatuses, Set<CommentStatus> statuses) {

    private static MyCommentsStatusScope all() {
      return new MyCommentsStatusScope(true, Set.of());
    }

    private static MyCommentsStatusScope single(CommentStatus status) {
      return new MyCommentsStatusScope(false, Set.of(status));
    }
  }

  private record ModerationDecision(CommentModerationRiskLevel riskLevel, String reasonCode) {}

  private record MentionResolution(List<String> mentionedUserIds, String mentionedUserIdsJson) {

    private static MentionResolution empty() {
      return new MentionResolution(List.of(), EMPTY_JSON_ARRAY);
    }
  }

  private record CommentPublishedEventPayload(
      String commentId,
      String specimenId,
      String authorUserId,
      String replyToUserId,
      String status) {}

  private record CommentMentionEventPayload(
      String commentId, String specimenId, String authorUserId, List<String> mentionedUserIds) {}

  private record CommentResonanceEventPayload(
      String commentId,
      String specimenId,
      String resonatorUserId,
      String commentAuthorUserId,
      int resonanceCount) {}

  private record CommentCountChangedEventPayload(
      String specimenId, int delta, String commentId, String triggerAction) {}

  private record CommentReportedEventPayload(
      String reportId,
      String commentId,
      String specimenId,
      String reporterUserId,
      String reasonCode,
      String message) {}

  private record CommentStatusChangedEventPayload(
      String commentId,
      String specimenId,
      String authorUserId,
      String oldStatus,
      String newStatus,
      String actorType) {}

  private record TopRoastUpdatedEventPayload(
      String specimenId, String commentId, int resonanceCount, boolean hasTopRoast) {}
}
