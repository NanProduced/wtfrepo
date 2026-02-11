package com.wtfrepo.backend.specimen.infra.persistence.entity;

import com.wtfrepo.backend.specimen.domain.SpecimenAdminOperation;
import com.wtfrepo.backend.specimen.domain.SpecimenStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;

/**
 * Stores admin submit/review idempotent responses to guarantee replay safety.
 */
@Getter
@Entity
@Table(
    name = "specimen_admin_action_idempotency",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_specimen_admin_action_idempotency_scope",
          columnNames = {"admin_user_id", "specimen_id", "operation", "idempotency_key"})
    })
public class SpecimenAdminActionIdempotencyJpaEntity {

  /**
   * Deterministic key derived from `(adminUserId, specimenId, operation, idempotencyKey)`.
   *
   * <p>Using a derived key avoids accidental ambiguity in persistence and keeps lookup O(1).
   */
  @Id
  @Column(name = "operation_key", nullable = false, length = 64)
  private String operationKey;

  @Column(name = "admin_user_id", nullable = false, length = 64)
  private String adminUserId;

  @Column(name = "specimen_id", nullable = false, length = 64)
  private String specimenId;

  @Enumerated(EnumType.STRING)
  @Column(name = "operation", nullable = false, length = 16)
  private SpecimenAdminOperation operation;

  @Column(name = "idempotency_key", nullable = false, length = 128)
  private String idempotencyKey;

  @Column(name = "request_fingerprint", nullable = false, length = 64)
  private String requestFingerprint;

  @Enumerated(EnumType.STRING)
  @Column(name = "response_status", nullable = false, length = 32)
  private SpecimenStatus responseStatus;

  @Column(name = "response_reviewed_by", length = 64)
  private String responseReviewedBy;

  @Column(name = "response_reviewed_at")
  private Instant responseReviewedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected SpecimenAdminActionIdempotencyJpaEntity() {}

  public static SpecimenAdminActionIdempotencyJpaEntity of(
      String operationKey,
      String adminUserId,
      String specimenId,
      SpecimenAdminOperation operation,
      String idempotencyKey,
      String requestFingerprint,
      SpecimenStatus responseStatus,
      String responseReviewedBy,
      Instant responseReviewedAt) {
    SpecimenAdminActionIdempotencyJpaEntity entity = new SpecimenAdminActionIdempotencyJpaEntity();
    entity.operationKey = operationKey;
    entity.adminUserId = adminUserId;
    entity.specimenId = specimenId;
    entity.operation = operation;
    entity.idempotencyKey = idempotencyKey;
    entity.requestFingerprint = requestFingerprint;
    entity.responseStatus = responseStatus;
    entity.responseReviewedBy = responseReviewedBy;
    entity.responseReviewedAt = responseReviewedAt;
    entity.createdAt = Instant.now();
    return entity;
  }
}
