package com.wtfrepo.backend.achievements.infra.persistence.repository;

import com.wtfrepo.backend.achievements.infra.persistence.entity.UserAchievementJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAchievementJpaRepository extends JpaRepository<UserAchievementJpaEntity, String> {

  boolean existsByUserIdAndAchievementCode(String userId, String achievementCode);

  long countByUserId(String userId);

  List<UserAchievementJpaEntity> findByUserId(String userId);

  Optional<UserAchievementJpaEntity> findByUserIdAndAchievementCode(String userId, String achievementCode);

  long countByAchievementCode(String achievementCode);

  @Query("select count(distinct ua.userId) from UserAchievementJpaEntity ua")
  long countDistinctUserId();

  @Query(
      """
      select ua
        from UserAchievementJpaEntity ua
       where ua.userId = :userId
         and (
             :cursorUnlockedAt is null
             or ua.unlockedAt < :cursorUnlockedAt
             or (ua.unlockedAt = :cursorUnlockedAt and ua.id < :cursorId)
         )
       order by ua.unlockedAt desc, ua.id desc
      """)
  List<UserAchievementJpaEntity> findPageAfterCursor(
      @Param("userId") String userId,
      @Param("cursorUnlockedAt") Instant cursorUnlockedAt,
      @Param("cursorId") String cursorId,
      Pageable pageable);
}
