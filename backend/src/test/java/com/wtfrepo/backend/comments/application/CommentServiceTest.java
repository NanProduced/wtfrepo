package com.wtfrepo.backend.comments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtfrepo.backend.auth.infra.persistence.entity.AuthUserJpaEntity;
import com.wtfrepo.backend.auth.infra.persistence.repository.AuthUserJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtfrepo.backend.comments.application.support.CommentMentionParser;
import com.wtfrepo.backend.comments.domain.CommentAuthorRepoRoleSnapshot;
import com.wtfrepo.backend.comments.domain.CommentModerationRiskLevel;
import com.wtfrepo.backend.comments.domain.CommentReportReasonCode;
import com.wtfrepo.backend.comments.domain.CommentStatus;
import com.wtfrepo.backend.comments.infra.persistence.entity.CommentJpaEntity;
import com.wtfrepo.backend.comments.infra.persistence.entity.CommentReportJpaEntity;
import com.wtfrepo.backend.comments.infra.persistence.entity.CommentRequestIdempotencyJpaEntity;
import com.wtfrepo.backend.comments.infra.persistence.repository.CommentJpaRepository;
import com.wtfrepo.backend.comments.infra.persistence.repository.CommentModerationLogJpaRepository;
import com.wtfrepo.backend.comments.infra.persistence.repository.CommentReportJpaRepository;
import com.wtfrepo.backend.comments.infra.persistence.repository.CommentRequestIdempotencyJpaRepository;
import com.wtfrepo.backend.comments.infra.persistence.repository.CommentResonanceJpaRepository;
import com.wtfrepo.backend.economy.application.EconomyWalletService;
import com.wtfrepo.backend.economy.infra.persistence.repository.EconomyLedgerJpaRepository;
import com.wtfrepo.backend.shared.json.JsonUtils;
import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import com.wtfrepo.backend.shared.web.ApiException;
import com.wtfrepo.backend.shared.web.ErrorCode;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenJpaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

  @Mock private CommentJpaRepository commentRepository;
  @Mock private CommentRequestIdempotencyJpaRepository idempotencyRepository;
  @Mock private CommentResonanceJpaRepository resonanceRepository;
  @Mock private CommentReportJpaRepository reportRepository;
  @Mock private CommentModerationLogJpaRepository moderationLogRepository;
  @Mock private AuthUserJpaRepository authUserRepository;
  @Mock private SpecimenJpaRepository specimenRepository;
  @Mock private CommentAuthorRoleResolver commentAuthorRoleResolver;
  @Mock private EconomyWalletService economyWalletService;
  @Mock private EconomyLedgerJpaRepository economyLedgerRepository;
  @Mock private OutboxEventStore outboxEventStore;

  private CommentService commentService;
  private JsonUtils jsonUtils;

  @BeforeEach
  void setUp() {
    CommentsPolicyProperties policyProperties = new CommentsPolicyProperties();
    jsonUtils = new JsonUtils(new ObjectMapper());
    commentService =
        new CommentService(
            commentRepository,
            idempotencyRepository,
            resonanceRepository,
            reportRepository,
            moderationLogRepository,
            authUserRepository,
            specimenRepository,
            commentAuthorRoleResolver,
            new CommentMentionParser(),
            economyWalletService,
            economyLedgerRepository,
            outboxEventStore,
            policyProperties,
            jsonUtils);
  }

  @Test
  void publish_shouldDeductEconomyAndAppendOutboxEventsWhenSpecimenActive() {
    when(idempotencyRepository.findByUserIdAndClientRequestId("usr_1", "req_1"))
        .thenReturn(Optional.empty());
    when(specimenRepository.findById("spm_1")).thenReturn(Optional.of(activeSpecimen("spm_1")));
    when(
            economyWalletService.deductBug(
                eq("usr_1"),
                eq(200),
                eq("COMMENT"),
                anyString(),
                eq("COMMENT"),
                eq("comment_req_1")))
        .thenReturn(new EconomyWalletService.LedgerWriteResult("ledger_1", 800L));
    when(commentRepository.save(any(CommentJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(idempotencyRepository.save(any(CommentRequestIdempotencyJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CommentService.PublishResult result =
        commentService.publish(
            "usr_1", new CommentService.PublishCommand("spm_1", "hello comment", null, "req_1"));

    assertThat(result.commentId()).startsWith("cmt_");
    assertThat(result.specimenId()).isEqualTo("spm_1");
    assertThat(result.status()).isEqualTo("ACTIVE");
    assertThat(result.bugCost()).isEqualTo(200);
    assertThat(result.balanceAfter()).isEqualTo(800L);

    verify(economyWalletService)
        .deductBug("usr_1", 200, "COMMENT", result.commentId(), "COMMENT", "comment_req_1");
    verify(outboxEventStore, times(3)).append(any(OutboxEventCommand.class));

    ArgumentCaptor<OutboxEventCommand> captor = ArgumentCaptor.forClass(OutboxEventCommand.class);
    verify(outboxEventStore, times(3)).append(captor.capture());
    List<String> eventTypes = captor.getAllValues().stream().map(OutboxEventCommand::eventType).toList();
    assertThat(eventTypes)
        .contains("CommentPublishedEvent", "CommentCountChangedEvent", "TopRoastUpdatedEvent");
  }

  @Test
  void publish_shouldResolveMentionsAndAppendMentionEvent() {
    when(idempotencyRepository.findByUserIdAndClientRequestId("usr_1", "req_mention_1"))
        .thenReturn(Optional.empty());
    when(specimenRepository.findById("spm_1")).thenReturn(Optional.of(activeSpecimen("spm_1")));
    when(authUserRepository.findByUsername("alice"))
        .thenReturn(Optional.of(AuthUserJpaEntity.create("usr_2", "alice", 0L)));
    when(authUserRepository.findByUsername("bob"))
        .thenReturn(Optional.of(AuthUserJpaEntity.create("usr_3", "bob", 0L)));
    when(
            economyWalletService.deductBug(
                eq("usr_1"),
                eq(200),
                eq("COMMENT"),
                anyString(),
                eq("COMMENT"),
                eq("comment_req_mention_1")))
        .thenReturn(new EconomyWalletService.LedgerWriteResult("ledger_mention_1", 700L));
    when(commentRepository.save(any(CommentJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(idempotencyRepository.save(any(CommentRequestIdempotencyJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CommentService.PublishResult result =
        commentService.publish(
            "usr_1",
            new CommentService.PublishCommand(
                "spm_1", "hello @alice and @bob and @alice again", null, "req_mention_1"));

    assertThat(result.status()).isEqualTo("ACTIVE");

    ArgumentCaptor<CommentJpaEntity> entityCaptor = ArgumentCaptor.forClass(CommentJpaEntity.class);
    verify(commentRepository).save(entityCaptor.capture());
    assertThat(entityCaptor.getValue().getMentionedUserIds()).isEqualTo("[\"usr_2\",\"usr_3\"]");

    verify(outboxEventStore, times(4)).append(any(OutboxEventCommand.class));
    ArgumentCaptor<OutboxEventCommand> eventCaptor = ArgumentCaptor.forClass(OutboxEventCommand.class);
    verify(outboxEventStore, times(4)).append(eventCaptor.capture());
    List<String> eventTypes = eventCaptor.getAllValues().stream().map(OutboxEventCommand::eventType).toList();
    assertThat(eventTypes)
        .contains(
            "CommentPublishedEvent",
            "CommentCountChangedEvent",
            "CommentMentionEvent",
            "TopRoastUpdatedEvent");
  }

  @Test
  void publish_shouldRejectWhenMentionCountExceedsPolicy() {
    when(idempotencyRepository.findByUserIdAndClientRequestId("usr_1", "req_toomany_1"))
        .thenReturn(Optional.empty());
    when(specimenRepository.findById("spm_1")).thenReturn(Optional.of(activeSpecimen("spm_1")));

    assertThatThrownBy(
            () ->
                commentService.publish(
                    "usr_1",
                    new CommentService.PublishCommand(
                        "spm_1",
                        "@u1 @u2 @u3 @u4 @u5 @u6 too many",
                        null,
                        "req_toomany_1")))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiException = (ApiException) ex;
              assertThat(apiException.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_MENTIONS);
            });

    verify(economyWalletService, never())
        .deductBug(anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());
    verify(commentRepository, never()).save(any(CommentJpaEntity.class));
    verify(outboxEventStore, never()).append(any(OutboxEventCommand.class));
  }

  @Test
  void publish_shouldRejectWhenMentionUserNotFound() {
    when(idempotencyRepository.findByUserIdAndClientRequestId("usr_1", "req_invalidmention_1"))
        .thenReturn(Optional.empty());
    when(specimenRepository.findById("spm_1")).thenReturn(Optional.of(activeSpecimen("spm_1")));
    when(authUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                commentService.publish(
                    "usr_1",
                    new CommentService.PublishCommand(
                        "spm_1", "hello @ghost", null, "req_invalidmention_1")))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiException = (ApiException) ex;
              assertThat(apiException.getErrorCode()).isEqualTo(ErrorCode.INVALID_MENTION_USER);
            });

    verify(economyWalletService, never())
        .deductBug(anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());
    verify(commentRepository, never()).save(any(CommentJpaEntity.class));
    verify(outboxEventStore, never()).append(any(OutboxEventCommand.class));
  }

  @Test
  void publish_shouldRejectWhenMentioningSelf() {
    when(idempotencyRepository.findByUserIdAndClientRequestId("usr_1", "req_selfmention_1"))
        .thenReturn(Optional.empty());
    when(specimenRepository.findById("spm_1")).thenReturn(Optional.of(activeSpecimen("spm_1")));
    when(authUserRepository.findByUsername("selfUser"))
        .thenReturn(Optional.of(AuthUserJpaEntity.create("usr_1", "selfUser", 0L)));

    assertThatThrownBy(
            () ->
                commentService.publish(
                    "usr_1",
                    new CommentService.PublishCommand(
                        "spm_1", "hello @selfUser", null, "req_selfmention_1")))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiException = (ApiException) ex;
              assertThat(apiException.getErrorCode()).isEqualTo(ErrorCode.MENTION_SELF_NOT_ALLOWED);
            });

    verify(economyWalletService, never())
        .deductBug(anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());
    verify(commentRepository, never()).save(any(CommentJpaEntity.class));
    verify(outboxEventStore, never()).append(any(OutboxEventCommand.class));
  }

  @Test
  void publish_shouldReturnReplayResultWhenIdempotencyExists() {
    CommentRequestIdempotencyJpaEntity existingRequest =
        CommentRequestIdempotencyJpaEntity.create("idem_1", "usr_1", "req_1", "cmt_1");
    when(idempotencyRepository.findByUserIdAndClientRequestId("usr_1", "req_1"))
        .thenReturn(Optional.of(existingRequest));

    CommentJpaEntity existingComment =
        CommentJpaEntity.create(
            "cmt_1",
            "spm_1",
            "usr_1",
            null,
            null,
            null,
            "existing",
            "existing",
            "[]",
            "[]",
            CommentAuthorRepoRoleSnapshot.NONE,
            CommentModerationRiskLevel.PASS,
            null,
            CommentStatus.ACTIVE,
            "ledger_1");
    when(commentRepository.findById("cmt_1")).thenReturn(Optional.of(existingComment));
    when(economyWalletService.currentBalance("usr_1")).thenReturn(1200L);

    CommentService.PublishResult replayResult =
        commentService.publish(
            "usr_1", new CommentService.PublishCommand("spm_1", "ignored", null, "req_1"));

    assertThat(replayResult.commentId()).isEqualTo("cmt_1");
    assertThat(replayResult.balanceAfter()).isEqualTo(1200L);
    verify(economyWalletService, never())
        .deductBug(anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());
    verify(outboxEventStore, never()).append(any(OutboxEventCommand.class));
  }

  @Test
  void publish_shouldRejectWhenSpecimenIsOfflined() {
    when(idempotencyRepository.findByUserIdAndClientRequestId("usr_1", "req_1"))
        .thenReturn(Optional.empty());
    when(specimenRepository.findById("spm_1")).thenReturn(Optional.of(offlinedSpecimen("spm_1")));

    assertThatThrownBy(
            () ->
                commentService.publish(
                    "usr_1",
                    new CommentService.PublishCommand("spm_1", "hello", null, "req_1")))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiException = (ApiException) ex;
              assertThat(apiException.getErrorCode()).isEqualTo(ErrorCode.SPECIMEN_LOCKED);
            });

    verify(economyWalletService, never())
        .deductBug(anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());
    verify(commentRepository, never()).save(any(CommentJpaEntity.class));
  }

  @Test
  void list_shouldUseDeletedPlaceholderForSoftDeletedComments() {
    CommentJpaEntity activeComment =
        CommentJpaEntity.create(
            "cmt_active_1",
            "spm_1",
            "usr_1",
            null,
            null,
            null,
            "normal",
            "normal",
            "[]",
            "[]",
            CommentAuthorRepoRoleSnapshot.NONE,
            CommentModerationRiskLevel.PASS,
            null,
            CommentStatus.ACTIVE,
            "ledger_1");
    CommentJpaEntity deletedComment =
        CommentJpaEntity.create(
            "cmt_deleted_1",
            "spm_1",
            "usr_2",
            null,
            null,
            null,
            "deleted-source",
            "deleted-source",
            "[]",
            "[]",
            CommentAuthorRepoRoleSnapshot.NONE,
            CommentModerationRiskLevel.PASS,
            null,
            CommentStatus.DELETED,
            "ledger_2");

    when(commentRepository.findBySpecimenIdAndStatusInOrderByHotScoreDescCreatedAtDesc(
            eq("spm_1"), any(), any(Pageable.class)))
        .thenReturn(List.of(activeComment, deletedComment));

    CommentService.ListResult result =
        commentService.list(new CommentService.ListQuery("spm_1", "hot", null, 20));

    assertThat(result.items()).hasSize(2);
    assertThat(result.items().get(0).contentPreview()).isEqualTo("normal");
    assertThat(result.items().get(1).authorUserId()).isNull();
    assertThat(result.items().get(1).contentPreview()).isEqualTo("此病历已封存");
    assertThat(result.items().get(1).resonanceCount()).isZero();
  }

  @Test
  void resonate_shouldIncreaseCountAndAppendResonanceEventOnFirstAction() {
    CommentJpaEntity comment =
        CommentJpaEntity.create(
            "cmt_1",
            "spm_1",
            "author_1",
            null,
            null,
            null,
            "content",
            "content",
            "[]",
            "[]",
            CommentAuthorRepoRoleSnapshot.NONE,
            CommentModerationRiskLevel.PASS,
            null,
            CommentStatus.ACTIVE,
            "ledger_1");
    when(commentRepository.findById("cmt_1")).thenReturn(Optional.of(comment));
    when(specimenRepository.findById("spm_1")).thenReturn(Optional.of(activeSpecimen("spm_1")));
    when(resonanceRepository.existsByCommentIdAndUserId("cmt_1", "usr_2")).thenReturn(false);
    when(commentRepository.save(any(CommentJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CommentService.ResonanceResult result =
        commentService.resonate(
            "usr_2", new CommentService.ResonanceCommand("cmt_1", "res_req_1"));

    assertThat(result.resonated()).isTrue();
    assertThat(result.resonanceCount()).isEqualTo(1);
    verify(resonanceRepository).save(any());
    verify(outboxEventStore, times(2)).append(any(OutboxEventCommand.class));

    ArgumentCaptor<OutboxEventCommand> captor = ArgumentCaptor.forClass(OutboxEventCommand.class);
    verify(outboxEventStore, times(2)).append(captor.capture());
    List<String> eventTypes = captor.getAllValues().stream().map(OutboxEventCommand::eventType).toList();
    assertThat(eventTypes).contains("CommentResonanceEvent", "TopRoastUpdatedEvent");
  }

  @Test
  void resonate_shouldReturnIdempotentResultWhenResonanceAlreadyExists() {
    CommentJpaEntity comment =
        CommentJpaEntity.create(
            "cmt_1",
            "spm_1",
            "author_1",
            null,
            null,
            null,
            "content",
            "content",
            "[]",
            "[]",
            CommentAuthorRepoRoleSnapshot.NONE,
            CommentModerationRiskLevel.PASS,
            null,
            CommentStatus.ACTIVE,
            "ledger_1");
    when(commentRepository.findById("cmt_1")).thenReturn(Optional.of(comment));
    when(specimenRepository.findById("spm_1")).thenReturn(Optional.of(activeSpecimen("spm_1")));
    when(resonanceRepository.existsByCommentIdAndUserId("cmt_1", "usr_2")).thenReturn(true);

    CommentService.ResonanceResult result =
        commentService.resonate(
            "usr_2", new CommentService.ResonanceCommand("cmt_1", "res_req_2"));

    assertThat(result.resonated()).isFalse();
    assertThat(result.resonanceCount()).isZero();
    verify(resonanceRepository, never()).save(any());
    verify(outboxEventStore, never()).append(any(OutboxEventCommand.class));
  }

  @Test
  void context_shouldReturnGoneWhenCommentDeleted() {
    CommentJpaEntity deletedComment =
        CommentJpaEntity.create(
            "cmt_deleted_1",
            "spm_1",
            "usr_1",
            null,
            null,
            null,
            "deleted-source",
            "deleted-source",
            "[]",
            "[]",
            CommentAuthorRepoRoleSnapshot.NONE,
            CommentModerationRiskLevel.PASS,
            null,
            CommentStatus.DELETED,
            "ledger_1");
    when(commentRepository.findById("cmt_deleted_1")).thenReturn(Optional.of(deletedComment));

    assertThatThrownBy(() -> commentService.context("cmt_deleted_1"))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiException = (ApiException) ex;
              assertThat(apiException.getErrorCode()).isEqualTo(ErrorCode.CONTEXT_NOT_VISIBLE);
            });
  }

  @Test
  void myComments_shouldReturnPagedItemsForCurrentUser() {
    CommentJpaEntity comment1 =
        CommentJpaEntity.create(
            "cmt_me_1",
            "spm_1",
            "usr_me",
            null,
            null,
            null,
            "content1",
            "content1",
            "[]",
            "[]",
            CommentAuthorRepoRoleSnapshot.NONE,
            CommentModerationRiskLevel.PASS,
            null,
            CommentStatus.ACTIVE,
            "ledger_1");
    CommentJpaEntity comment2 =
        CommentJpaEntity.create(
            "cmt_me_2",
            "spm_2",
            "usr_me",
            null,
            null,
            null,
            "content2",
            "content2",
            "[]",
            "[]",
            CommentAuthorRepoRoleSnapshot.NONE,
            CommentModerationRiskLevel.PASS,
            null,
            CommentStatus.BLOCKED,
            "ledger_2");
    when(commentRepository.findByAuthorUserIdOrderByCreatedAtDesc(eq("usr_me"), any(Pageable.class)))
        .thenReturn(List.of(comment1, comment2));
    when(specimenRepository.findById("spm_1")).thenReturn(Optional.of(activeSpecimen("spm_1")));

    CommentService.MyCommentsResult result =
        commentService.myComments(
            "usr_me", new CommentService.MyCommentsQuery("0", 1, "ALL"));

    assertThat(result.items()).hasSize(1);
    assertThat(result.hasMore()).isTrue();
    assertThat(result.nextCursor()).isEqualTo("1");
    assertThat(result.items().get(0).commentId()).isEqualTo("cmt_me_1");
    assertThat(result.items().get(0).specimen().repoFullName()).isEqualTo("wtfrepo/demo");
    assertThat(result.items().get(0).locateUrl()).isEqualTo("/specimen/spm_1#cmt_me_1");
  }

  @Test
  void myComments_shouldRejectInvalidCursor() {
    assertThatThrownBy(
            () ->
                commentService.myComments(
                    "usr_me", new CommentService.MyCommentsQuery("bad-cursor", 20, "ALL")))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiException = (ApiException) ex;
              assertThat(apiException.getErrorCode()).isEqualTo(ErrorCode.INVALID_CURSOR);
            });

    verify(commentRepository, never()).findByAuthorUserIdOrderByCreatedAtDesc(anyString(), any());
  }

  @Test
  void delete_shouldSoftDeleteAndAppendEventsWhenAuthorOwnsComment() {
    CommentJpaEntity comment =
        CommentJpaEntity.create(
            "cmt_delete_1",
            "spm_1",
            "usr_author",
            null,
            null,
            null,
            "content",
            "content",
            "[]",
            "[]",
            CommentAuthorRepoRoleSnapshot.NONE,
            CommentModerationRiskLevel.PASS,
            null,
            CommentStatus.ACTIVE,
            "ledger_1");
    when(commentRepository.findById("cmt_delete_1")).thenReturn(Optional.of(comment));
    when(commentRepository.save(any(CommentJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CommentService.DeleteResult result =
        commentService.delete(
            "usr_author", new CommentService.DeleteCommand("cmt_delete_1", false));

    assertThat(result.deleted()).isTrue();
    assertThat(result.refundDelta()).isZero();
    assertThat(comment.getStatus()).isEqualTo(CommentStatus.DELETED);
    verify(outboxEventStore, times(3)).append(any(OutboxEventCommand.class));
  }

  @Test
  void delete_shouldRejectWhenOperatorHasNoPermission() {
    CommentJpaEntity comment =
        CommentJpaEntity.create(
            "cmt_delete_2",
            "spm_1",
            "usr_author",
            null,
            null,
            null,
            "content",
            "content",
            "[]",
            "[]",
            CommentAuthorRepoRoleSnapshot.NONE,
            CommentModerationRiskLevel.PASS,
            null,
            CommentStatus.ACTIVE,
            "ledger_1");
    when(commentRepository.findById("cmt_delete_2")).thenReturn(Optional.of(comment));

    assertThatThrownBy(
            () ->
                commentService.delete(
                    "usr_other", new CommentService.DeleteCommand("cmt_delete_2", false)))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiException = (ApiException) ex;
              assertThat(apiException.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
            });
  }

  @Test
  void topRoast_shouldReturnSummaryWhenThresholdReached() {
    CommentJpaEntity topComment =
        CommentJpaEntity.create(
            "cmt_top_1",
            "spm_1",
            "usr_top",
            null,
            null,
            null,
            "hot content",
            "hot content",
            "[]",
            "[]",
            CommentAuthorRepoRoleSnapshot.NONE,
            CommentModerationRiskLevel.PASS,
            null,
            CommentStatus.ACTIVE,
            "ledger_1");
    for (int i = 0; i < 12; i++) {
      topComment.incrementResonance();
    }

    when(specimenRepository.findById("spm_1")).thenReturn(Optional.of(activeSpecimen("spm_1")));
    when(commentRepository.findFirstBySpecimenIdAndStatusOrderByHotScoreDescCreatedAtDesc(
            "spm_1", CommentStatus.ACTIVE))
        .thenReturn(Optional.of(topComment));

    CommentService.TopRoastResult result = commentService.topRoast("spm_1");

    assertThat(result.hasTopRoast()).isTrue();
    assertThat(result.commentId()).isEqualTo("cmt_top_1");
    assertThat(result.resonanceCount()).isEqualTo(12);
    assertThat(result.author()).isNotNull();
    assertThat(result.author().userId()).isEqualTo("usr_top");
  }

  @Test
  void topRoast_shouldReturnEmptyWhenCandidateBelowThreshold() {
    CommentJpaEntity candidate =
        CommentJpaEntity.create(
            "cmt_top_2",
            "spm_1",
            "usr_top",
            null,
            null,
            null,
            "cold content",
            "cold content",
            "[]",
            "[]",
            CommentAuthorRepoRoleSnapshot.NONE,
            CommentModerationRiskLevel.PASS,
            null,
            CommentStatus.ACTIVE,
            "ledger_1");
    for (int i = 0; i < 3; i++) {
      candidate.incrementResonance();
    }

    when(specimenRepository.findById("spm_1")).thenReturn(Optional.of(activeSpecimen("spm_1")));
    when(commentRepository.findFirstBySpecimenIdAndStatusOrderByHotScoreDescCreatedAtDesc(
            "spm_1", CommentStatus.ACTIVE))
        .thenReturn(Optional.of(candidate));

    CommentService.TopRoastResult result = commentService.topRoast("spm_1");

    assertThat(result.hasTopRoast()).isFalse();
    assertThat(result.commentId()).isNull();
  }

  @Test
  void report_shouldPersistReportAndAppendOutboxEvent() {
    CommentJpaEntity comment =
        CommentJpaEntity.create(
            "cmt_report_1",
            "spm_1",
            "usr_author",
            null,
            null,
            null,
            "content",
            "content",
            "[]",
            "[]",
            CommentAuthorRepoRoleSnapshot.NONE,
            CommentModerationRiskLevel.PASS,
            null,
            CommentStatus.ACTIVE,
            "ledger_1");
    when(commentRepository.findById("cmt_report_1")).thenReturn(Optional.of(comment));
    when(specimenRepository.findById("spm_1")).thenReturn(Optional.of(activeSpecimen("spm_1")));
    when(reportRepository.save(any(CommentReportJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CommentService.ReportResult result =
        commentService.report(
            "usr_reporter",
            new CommentService.ReportCommand(
                "cmt_report_1", CommentReportReasonCode.SPAM.name(), "spam", "rep_req_1"));

    assertThat(result.reported()).isTrue();
    assertThat(result.ticketId()).startsWith("tkt_cmtrpt_");
    verify(reportRepository, times(1)).save(any(CommentReportJpaEntity.class));
    verify(outboxEventStore, times(3)).append(any(OutboxEventCommand.class));

    ArgumentCaptor<OutboxEventCommand> outboxCaptor = ArgumentCaptor.forClass(OutboxEventCommand.class);
    verify(outboxEventStore, times(3)).append(outboxCaptor.capture());
    List<String> eventTypes =
        outboxCaptor.getAllValues().stream().map(OutboxEventCommand::eventType).toList();
    assertThat(eventTypes)
        .contains("CommentReportedEvent", "CommentCountChangedEvent", "CommentStatusChangedEvent");
  }

  @Test
  void report_shouldRejectDuplicateInSameWindow() {
    CommentJpaEntity comment =
        CommentJpaEntity.create(
            "cmt_report_2",
            "spm_1",
            "usr_author",
            null,
            null,
            null,
            "content",
            "content",
            "[]",
            "[]",
            CommentAuthorRepoRoleSnapshot.NONE,
            CommentModerationRiskLevel.PASS,
            null,
            CommentStatus.ACTIVE,
            "ledger_1");
    when(commentRepository.findById("cmt_report_2")).thenReturn(Optional.of(comment));
    when(specimenRepository.findById("spm_1")).thenReturn(Optional.of(activeSpecimen("spm_1")));
    when(reportRepository.save(any(CommentReportJpaEntity.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate report"));

    assertThatThrownBy(
            () ->
                commentService.report(
                    "usr_reporter",
                    new CommentService.ReportCommand(
                        "cmt_report_2", "SPAM", "spam", "rep_req_2")))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiException = (ApiException) ex;
              assertThat(apiException.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_REPORT);
            });

    verify(outboxEventStore, never()).append(any(OutboxEventCommand.class));
  }

  @Test
  void report_shouldRejectWhenReasonInvalid() {
    assertThatThrownBy(
            () ->
                commentService.report(
                    "usr_reporter",
                    new CommentService.ReportCommand(
                        "cmt_report_invalid", "UNKNOWN", "msg", "rep_req_invalid")))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiException = (ApiException) ex;
              assertThat(apiException.getErrorCode()).isEqualTo(ErrorCode.INVALID_REASON);
            });

    verify(commentRepository, never()).findById(anyString());
    verify(reportRepository, never()).save(any(CommentReportJpaEntity.class));
  }

  private SpecimenJpaEntity activeSpecimen(String specimenId) {
    SpecimenJpaEntity specimen =
        SpecimenJpaEntity.createDraft(specimenId, "wtfrepo/demo", "https://github.com/wtfrepo/demo");
    specimen.submit("submit for review");
    specimen.approve("admin");
    return specimen;
  }

  private SpecimenJpaEntity offlinedSpecimen(String specimenId) {
    SpecimenJpaEntity specimen = activeSpecimen(specimenId);
    specimen.deactivate("admin", "manual offline");
    return specimen;
  }
}
