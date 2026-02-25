package com.wtfrepo.backend.achievements.infra.persistence.repository;

import com.wtfrepo.backend.achievements.infra.persistence.entity.AchievementInboxEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AchievementInboxEventJpaRepository
    extends JpaRepository<AchievementInboxEventJpaEntity, String> {}
