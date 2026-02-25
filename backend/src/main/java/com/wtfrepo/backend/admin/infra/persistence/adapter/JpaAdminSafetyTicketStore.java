package com.wtfrepo.backend.admin.infra.persistence.adapter;

import com.wtfrepo.backend.admin.application.AdminSafetyTicketStore;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminSafetyTicketPage;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminSafetyTicketQuery;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminSafetyTicketRecord;
import com.wtfrepo.backend.admin.domain.AdminSafetyTicketSource;
import com.wtfrepo.backend.admin.domain.AdminSafetyTicketStatus;
import com.wtfrepo.backend.admin.infra.persistence.entity.AdminSafetyTicketJpaEntity;
import com.wtfrepo.backend.admin.infra.persistence.repository.AdminSafetyTicketJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Repository
@ConditionalOnBean(AdminSafetyTicketJpaRepository.class)
public class JpaAdminSafetyTicketStore implements AdminSafetyTicketStore {

  private final AdminSafetyTicketJpaRepository repository;

  public JpaAdminSafetyTicketStore(AdminSafetyTicketJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AdminSafetyTicketRecord> findById(String ticketId) {
    return repository.findById(ticketId).map(this::toRecord);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AdminSafetyTicketRecord> findLatestActiveByTarget(
      String targetType, String targetId) {
    List<AdminSafetyTicketStatus> activeStatuses =
        List.of(AdminSafetyTicketStatus.OPEN, AdminSafetyTicketStatus.IN_REVIEW);
    return repository
        .findFirstByTargetTypeAndTargetIdAndStatusInOrderByCreatedAtDesc(
            targetType, targetId, activeStatuses)
        .map(this::toRecord);
  }

  @Override
  @Transactional
  public AdminSafetyTicketRecord create(
      AdminSafetyTicketSource source,
      String reporterId,
      String targetType,
      String targetId,
      String reason,
      String ticketId) {
    AdminSafetyTicketJpaEntity entity =
        StringUtils.hasText(ticketId)
            ? AdminSafetyTicketJpaEntity.createWithId(
                ticketId, source, reporterId, targetType, targetId, reason)
            : AdminSafetyTicketJpaEntity.create(
                source, reporterId, targetType, targetId, reason);
    AdminSafetyTicketJpaEntity saved = repository.save(entity);
    return toRecord(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public AdminSafetyTicketPage list(AdminSafetyTicketQuery query) {
    PageRequest pageRequest = PageRequest.of(query.page() - 1, query.pageSize());
    Page<AdminSafetyTicketJpaEntity> page;
    if (query.status() != null) {
      page = repository.findAllByStatusOrderByCreatedAtDesc(query.status(), pageRequest);
    } else {
      page = repository.findAllByOrderByCreatedAtDesc(pageRequest);
    }
    List<AdminSafetyTicketRecord> items = page.getContent().stream().map(this::toRecord).toList();
    return new AdminSafetyTicketPage(
        items,
        query.page(),
        query.pageSize(),
        (int) page.getTotalElements(),
        page.getTotalPages());
  }

  @Override
  @Transactional
  public AdminSafetyTicketRecord updateStatus(
      String ticketId,
      AdminSafetyTicketStatus status,
      String resolution,
      String resolvedBy,
      Instant resolvedAt,
      Instant updatedAt) {
    AdminSafetyTicketJpaEntity entity =
        repository
            .findById(ticketId)
            .orElseThrow(() -> new IllegalStateException("Safety ticket not found: " + ticketId));
    entity.updateStatus(status, resolution, resolvedBy, resolvedAt, updatedAt);
    AdminSafetyTicketJpaEntity saved = repository.save(entity);
    return toRecord(saved);
  }

  private AdminSafetyTicketRecord toRecord(AdminSafetyTicketJpaEntity entity) {
    return new AdminSafetyTicketRecord(
        entity.getId(),
        entity.getSource(),
        entity.getStatus(),
        entity.getReporterId(),
        entity.getTargetType(),
        entity.getTargetId(),
        entity.getReason(),
        entity.getResolution(),
        entity.getResolvedBy(),
        entity.getResolvedAt(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
