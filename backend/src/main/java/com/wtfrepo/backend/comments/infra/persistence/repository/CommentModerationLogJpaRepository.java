package com.wtfrepo.backend.comments.infra.persistence.repository;

import com.wtfrepo.backend.comments.infra.persistence.entity.CommentModerationLogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for comment moderation log entries. */
public interface CommentModerationLogJpaRepository
    extends JpaRepository<CommentModerationLogJpaEntity, String> {}
