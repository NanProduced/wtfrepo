package com.wtfrepo.backend.notifications.infra.persistence.repository;

import com.wtfrepo.backend.notifications.infra.persistence.entity.UserBroadcastCheckpointJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBroadcastCheckpointJpaRepository
    extends JpaRepository<UserBroadcastCheckpointJpaEntity, String> {}
