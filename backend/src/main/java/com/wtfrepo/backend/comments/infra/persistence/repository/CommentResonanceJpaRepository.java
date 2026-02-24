package com.wtfrepo.backend.comments.infra.persistence.repository;

import com.wtfrepo.backend.comments.infra.persistence.entity.CommentResonanceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentResonanceJpaRepository extends JpaRepository<CommentResonanceJpaEntity, String> {

  boolean existsByCommentIdAndUserId(String commentId, String userId);
}

