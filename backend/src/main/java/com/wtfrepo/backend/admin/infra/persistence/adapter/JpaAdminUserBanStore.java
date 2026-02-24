package com.wtfrepo.backend.admin.infra.persistence.adapter;

import com.wtfrepo.backend.admin.application.AdminUserBanStore;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminUserBanPage;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminUserBanQuery;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminUserBanRecord;
import com.wtfrepo.backend.admin.infra.persistence.entity.AdminUserBanRecordJpaEntity;
import com.wtfrepo.backend.admin.infra.persistence.repository.AdminUserBanRecordJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnBean(AdminUserBanRecordJpaRepository.class)
public class JpaAdminUserBanStore implements AdminUserBanStore {

  private final AdminUserBanRecordJpaRepository repository;

  public JpaAdminUserBanStore(AdminUserBanRecordJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AdminUserBanRecord> findActiveByUserId(String userId) {
    return repository.findByUserIdAndActiveTrue(userId).map(this::toRecord);
  }

  @Override
  @Transactional(readOnly = true)
  public AdminUserBanPage list(AdminUserBanQuery query) {
    PageRequest pageRequest = PageRequest.of(query.page() - 1, query.pageSize());
    Page<AdminUserBanRecordJpaEntity> page;
    if (query.active() != null && query.active()) {
      page = repository.findAllByActiveTrueOrderByBannedAtDesc(pageRequest);
    } else if (query.active() != null) {
      page = repository.findAllByActiveFalseOrderByBannedAtDesc(pageRequest);
    } else {
      page = repository.findAllByActiveTrueOrderByBannedAtDesc(pageRequest);
    }
    List<AdminUserBanRecord> items = page.getContent().stream().map(this::toRecord).toList();
    return new AdminUserBanPage(
        items,
        query.page(),
        query.pageSize(),
        (int) page.getTotalElements(),
        page.getTotalPages());
  }

  @Override
  @Transactional
  public AdminUserBanRecord save(AdminUserBanRecord record) {
    AdminUserBanRecordJpaEntity entity =
        AdminUserBanRecordJpaEntity.fromRecord(
            record.id(),
            record.userId(),
            record.banType(),
            record.reason(),
            record.bannedBy(),
            record.bannedAt(),
            record.expiresAt(),
            record.unbannedBy(),
            record.unbannedAt(),
            record.active());
    AdminUserBanRecordJpaEntity saved = repository.save(entity);
    return toRecord(saved);
  }

  private AdminUserBanRecord toRecord(AdminUserBanRecordJpaEntity entity) {
    return new AdminUserBanRecord(
        entity.getId(),
        entity.getUserId(),
        null,
        entity.getBanType(),
        entity.getReason(),
        entity.getBannedBy(),
        entity.getBannedAt(),
        entity.getExpiresAt(),
        entity.getUnbannedBy(),
        entity.getUnbannedAt(),
        entity.isActive());
  }
}
