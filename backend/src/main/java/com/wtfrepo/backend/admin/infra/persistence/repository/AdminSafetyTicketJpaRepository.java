package com.wtfrepo.backend.admin.infra.persistence.repository;

import com.wtfrepo.backend.admin.domain.AdminSafetyTicketStatus;
import com.wtfrepo.backend.admin.infra.persistence.entity.AdminSafetyTicketJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminSafetyTicketJpaRepository
    extends JpaRepository<AdminSafetyTicketJpaEntity, String> {

  Page<AdminSafetyTicketJpaEntity> findAllByStatusOrderByCreatedAtDesc(
      AdminSafetyTicketStatus status, Pageable pageable);

  Page<AdminSafetyTicketJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
