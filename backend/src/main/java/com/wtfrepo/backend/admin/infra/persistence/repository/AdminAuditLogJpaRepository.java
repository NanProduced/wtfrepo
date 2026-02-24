package com.wtfrepo.backend.admin.infra.persistence.repository;

import com.wtfrepo.backend.admin.infra.persistence.entity.AdminAuditLogJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAuditLogJpaRepository extends JpaRepository<AdminAuditLogJpaEntity, String> {

  Page<AdminAuditLogJpaEntity> findAllByOperatorIdOrderByCreatedAtDesc(
      String operatorId, Pageable pageable);

  Page<AdminAuditLogJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

  @Query(
      """
      select log from AdminAuditLogJpaEntity log
        where (:operatorId is null or log.operatorId = :operatorId)
          and (:action is null or log.action = :action)
          and (:targetType is null or log.targetType = :targetType)
          and (:targetId is null or log.targetId = :targetId)
        order by log.createdAt desc
      """)
  Page<AdminAuditLogJpaEntity> findByFilters(
      @Param("operatorId") String operatorId,
      @Param("action") String action,
      @Param("targetType") String targetType,
      @Param("targetId") String targetId,
      Pageable pageable);
}
