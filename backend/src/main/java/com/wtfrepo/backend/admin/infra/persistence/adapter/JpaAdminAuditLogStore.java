package com.wtfrepo.backend.admin.infra.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.wtfrepo.backend.admin.application.AdminAuditLogStore;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAuditLogEntry;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAuditLogPage;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAuditLogQuery;
import com.wtfrepo.backend.admin.infra.persistence.entity.AdminAuditLogJpaEntity;
import com.wtfrepo.backend.admin.infra.persistence.repository.AdminAuditLogJpaRepository;
import com.wtfrepo.backend.shared.json.JsonUtils;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Repository
@ConditionalOnBean(AdminAuditLogJpaRepository.class)
public class JpaAdminAuditLogStore implements AdminAuditLogStore {

  private final AdminAuditLogJpaRepository repository;
  private final JsonUtils jsonUtils;

  public JpaAdminAuditLogStore(AdminAuditLogJpaRepository repository, JsonUtils jsonUtils) {
    this.repository = repository;
    this.jsonUtils = jsonUtils;
  }

  @Override
  @Transactional
  public void append(AdminAuditLogEntry entry) {
    AdminAuditLogJpaEntity entity =
        AdminAuditLogJpaEntity.create(
            entry.id(),
            entry.operatorId(),
            entry.action(),
            entry.targetType(),
            entry.targetId(),
            toJson(entry.beforeSnapshot()),
            toJson(entry.afterSnapshot()),
            toJson(entry.metadata()),
            entry.requestId(),
            entry.ipAddress(),
            entry.userAgent(),
            entry.createdAt());
    repository.save(entity);
  }

  @Override
  @Transactional(readOnly = true)
  public AdminAuditLogPage list(AdminAuditLogQuery query) {
    PageRequest pageRequest = PageRequest.of(query.page() - 1, query.pageSize());
    String operatorId = normalize(query.operatorId());
    String action = normalize(query.action());
    String targetType = normalize(query.targetType());
    String targetId = normalize(query.targetId());
    Page<AdminAuditLogJpaEntity> page =
        repository.findByFilters(operatorId, action, targetType, targetId, pageRequest);

    List<AdminAuditLogEntry> items = page.getContent().stream().map(this::toEntry).toList();
    return new AdminAuditLogPage(
        items,
        query.page(),
        query.pageSize(),
        (int) page.getTotalElements(),
        page.getTotalPages());
  }

  private String normalize(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }

  private AdminAuditLogEntry toEntry(AdminAuditLogJpaEntity entity) {
    return new AdminAuditLogEntry(
        entity.getId(),
        entity.getOperatorId(),
        entity.getAction(),
        entity.getTargetType(),
        entity.getTargetId(),
        toObject(entity.getBeforeSnapshot()),
        toObject(entity.getAfterSnapshot()),
        toObject(entity.getMetadata()),
        entity.getRequestId(),
        entity.getIpAddress(),
        entity.getUserAgent(),
        entity.getCreatedAt());
  }

  private String toJson(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return jsonUtils.toJson(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize admin audit payload", ex);
    }
  }

  private Object toObject(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return jsonUtils.toObject(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to deserialize admin audit payload", ex);
    }
  }
}
