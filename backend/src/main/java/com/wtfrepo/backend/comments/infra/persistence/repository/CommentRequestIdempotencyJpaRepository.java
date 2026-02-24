package com.wtfrepo.backend.comments.infra.persistence.repository;

import com.wtfrepo.backend.comments.infra.persistence.entity.CommentRequestIdempotencyJpaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRequestIdempotencyJpaRepository
    extends JpaRepository<CommentRequestIdempotencyJpaEntity, String> {

  Optional<CommentRequestIdempotencyJpaEntity> findByUserIdAndClientRequestId(
      String userId, String clientRequestId);
}

