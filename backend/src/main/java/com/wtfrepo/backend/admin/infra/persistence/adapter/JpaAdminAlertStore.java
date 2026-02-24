package com.wtfrepo.backend.admin.infra.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.wtfrepo.backend.admin.application.AdminAlertStore;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAlertPage;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAlertQuery;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAlertRecord;
import com.wtfrepo.backend.admin.infra.persistence.entity.AdminAlertTaskJpaEntity;
import com.wtfrepo.backend.admin.infra.persistence.repository.AdminAlertTaskJpaRepository;
import com.wtfrepo.backend.shared.json.JsonUtils;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Repository
@ConditionalOnBean(AdminAlertTaskJpaRepository.class)
public class JpaAdminAlertStore implements AdminAlertStore {

  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  private final AdminAlertTaskJpaRepository repository;
  private final JsonUtils jsonUtils;

  public JpaAdminAlertStore(AdminAlertTaskJpaRepository repository, JsonUtils jsonUtils) {
    this.repository = repository;
    this.jsonUtils = jsonUtils;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AdminAlertRecord> findById(String alertId) {
    return repository.findById(alertId).map(this::toRecord);
  }

  @Override
  @Transactional(readOnly = true)
  public AdminAlertPage list(AdminAlertQuery query) {
    PageRequest pageRequest = PageRequest.of(query.page() - 1, query.pageSize());
    Page<AdminAlertTaskJpaEntity> page;
    if (query.acknowledged() != null) {
      page = repository.findAllByAcknowledgedOrderByCreatedAtDesc(query.acknowledged(), pageRequest);
    } else {
      page = repository.findAllByOrderByCreatedAtDesc(pageRequest);
    }
    List<AdminAlertRecord> items = page.getContent().stream().map(this::toRecord).toList();
    return new AdminAlertPage(
        items,
        query.page(),
        query.pageSize(),
        (int) page.getTotalElements(),
        page.getTotalPages());
  }

  @Override
  @Transactional
  public AdminAlertRecord acknowledge(String alertId, String acknowledgedBy) {
    AdminAlertTaskJpaEntity entity =
        repository
            .findById(alertId)
            .orElseThrow(() -> new IllegalStateException("Alert task not found: " + alertId));
    entity.acknowledge(acknowledgedBy);
    AdminAlertTaskJpaEntity saved = repository.save(entity);
    return toRecord(saved);
  }

  private AdminAlertRecord toRecord(AdminAlertTaskJpaEntity entity) {
    return new AdminAlertRecord(
        entity.getId(),
        entity.getAlertType(),
        entity.getSeverity(),
        entity.getTargetType(),
        entity.getTargetId(),
        entity.getMessage(),
        toList(entity.getEmailSentTo()),
        entity.getEmailSentAt(),
        entity.isAcknowledged(),
        entity.getAcknowledgedBy(),
        entity.getCreatedAt());
  }

  private List<String> toList(String value) {
    if (!StringUtils.hasText(value)) {
      return List.of();
    }
    try {
      return jsonUtils.mapper().readValue(value, STRING_LIST);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to deserialize alert email recipients", ex);
    }
  }
}
