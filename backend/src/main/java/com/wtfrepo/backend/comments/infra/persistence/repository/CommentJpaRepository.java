package com.wtfrepo.backend.comments.infra.persistence.repository;

import com.wtfrepo.backend.comments.domain.CommentStatus;
import com.wtfrepo.backend.comments.infra.persistence.entity.CommentJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentJpaRepository extends JpaRepository<CommentJpaEntity, String> {

  List<CommentJpaEntity> findBySpecimenIdAndStatusInOrderByCreatedAtDesc(
      String specimenId, Collection<CommentStatus> statuses, Pageable pageable);

  List<CommentJpaEntity> findBySpecimenIdAndStatusInOrderByHotScoreDescCreatedAtDesc(
      String specimenId, Collection<CommentStatus> statuses, Pageable pageable);

  List<CommentJpaEntity> findByAuthorUserIdAndStatusInOrderByCreatedAtDesc(
      String authorUserId, Collection<CommentStatus> statuses, Pageable pageable);

  List<CommentJpaEntity> findByAuthorUserIdOrderByCreatedAtDesc(String authorUserId, Pageable pageable);

  Optional<CommentJpaEntity> findFirstBySpecimenIdAndStatusOrderByHotScoreDescCreatedAtDesc(
      String specimenId, CommentStatus status);

  Page<CommentJpaEntity> findAllByStatusOrderByCreatedAtDesc(
      CommentStatus status, Pageable pageable);
}
