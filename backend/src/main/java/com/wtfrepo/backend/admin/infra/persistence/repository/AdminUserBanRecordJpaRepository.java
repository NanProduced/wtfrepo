package com.wtfrepo.backend.admin.infra.persistence.repository;

import com.wtfrepo.backend.admin.infra.persistence.entity.AdminUserBanRecordJpaEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserBanRecordJpaRepository
    extends JpaRepository<AdminUserBanRecordJpaEntity, String> {

  Optional<AdminUserBanRecordJpaEntity> findByUserIdAndActiveTrue(String userId);

  Page<AdminUserBanRecordJpaEntity> findAllByActiveTrueOrderByBannedAtDesc(Pageable pageable);

  Page<AdminUserBanRecordJpaEntity> findAllByActiveFalseOrderByBannedAtDesc(Pageable pageable);

  Page<AdminUserBanRecordJpaEntity> findAllByOrderByBannedAtDesc(Pageable pageable);
}
