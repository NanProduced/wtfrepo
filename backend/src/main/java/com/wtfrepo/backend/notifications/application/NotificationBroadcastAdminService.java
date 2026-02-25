package com.wtfrepo.backend.notifications.application;

import com.wtfrepo.backend.notifications.domain.BroadcastStatus;
import com.wtfrepo.backend.notifications.infra.persistence.entity.SystemBroadcastJpaEntity;
import com.wtfrepo.backend.notifications.infra.persistence.repository.SystemBroadcastJpaRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Admin-side system broadcast workflows in M06. */
@Service
public class NotificationBroadcastAdminService {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final SystemBroadcastJpaRepository systemBroadcastRepository;

  public NotificationBroadcastAdminService(SystemBroadcastJpaRepository systemBroadcastRepository) {
    this.systemBroadcastRepository = systemBroadcastRepository;
  }

  @Transactional
  public BroadcastRecord create(
      String createdBy, String title, String body, String targetUrl) {
    String normalizedTitle = normalizeRequired(title);
    String normalizedBody = normalizeOptional(body);
    String normalizedTargetUrl = normalizeOptional(targetUrl);

    SystemBroadcastJpaEntity entity =
        SystemBroadcastJpaEntity.create(createdBy, normalizedTitle, normalizedBody, normalizedTargetUrl);
    entity.markCompleted(0);
    SystemBroadcastJpaEntity saved = systemBroadcastRepository.save(entity);
    return toRecord(saved);
  }

  @Transactional(readOnly = true)
  public BroadcastPage list(Integer page, Integer pageSize) {
    int resolvedPage = normalizePage(page);
    int resolvedPageSize = normalizePageSize(pageSize);

    Page<SystemBroadcastJpaEntity> result =
        systemBroadcastRepository.findAll(
            PageRequest.of(
                resolvedPage - 1,
                resolvedPageSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

    List<BroadcastRecord> items = result.getContent().stream().map(this::toRecord).toList();
    return new BroadcastPage(
        items,
        resolvedPage,
        resolvedPageSize,
        Math.toIntExact(result.getTotalElements()),
        result.getTotalPages());
  }

  private BroadcastRecord toRecord(SystemBroadcastJpaEntity entity) {
    if (entity == null) {
      return null;
    }
    BroadcastStatus status = entity.getStatus();
    return new BroadcastRecord(
        entity.getBroadcastUid(),
        entity.getTitle(),
        entity.getBody(),
        entity.getTargetUrl(),
        status != null ? status.name() : null,
        entity.getTotalRecipients(),
        entity.getDeliveredCount(),
        entity.getCreatedBy(),
        entity.getCreatedAt(),
        entity.getCompletedAt());
  }

  private int normalizePage(Integer page) {
    if (page == null || page < 1) {
      return 1;
    }
    return page;
  }

  private int normalizePageSize(Integer pageSize) {
    if (pageSize == null || pageSize < 1) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(pageSize, MAX_PAGE_SIZE);
  }

  private String normalizeRequired(String value) {
    return value == null ? null : value.trim();
  }

  private String normalizeOptional(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }

  public record BroadcastRecord(
      String broadcastUid,
      String title,
      String body,
      String targetUrl,
      String status,
      int totalRecipients,
      int deliveredCount,
      String createdBy,
      Instant createdAt,
      Instant completedAt) {}

  public record BroadcastPage(
      List<BroadcastRecord> items, int page, int pageSize, int total, int totalPages) {}
}
