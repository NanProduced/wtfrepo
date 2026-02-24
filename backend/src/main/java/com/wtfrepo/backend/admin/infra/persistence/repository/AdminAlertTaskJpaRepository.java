package com.wtfrepo.backend.admin.infra.persistence.repository;

import com.wtfrepo.backend.admin.infra.persistence.entity.AdminAlertTaskJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAlertTaskJpaRepository
    extends JpaRepository<AdminAlertTaskJpaEntity, String> {

  Page<AdminAlertTaskJpaEntity> findAllByAcknowledgedOrderByCreatedAtDesc(
      boolean acknowledged, Pageable pageable);

  Page<AdminAlertTaskJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
