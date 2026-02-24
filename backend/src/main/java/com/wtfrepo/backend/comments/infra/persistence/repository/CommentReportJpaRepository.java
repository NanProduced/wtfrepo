package com.wtfrepo.backend.comments.infra.persistence.repository;

import com.wtfrepo.backend.comments.infra.persistence.entity.CommentReportJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentReportJpaRepository extends JpaRepository<CommentReportJpaEntity, String> {}

