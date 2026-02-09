package com.wtfrepo.backend.auth.infra.persistence.repository;

import com.wtfrepo.backend.auth.infra.persistence.entity.AuthUserJpaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthUserJpaRepository extends JpaRepository<AuthUserJpaEntity, String> {

  boolean existsByUsername(String username);

  Optional<AuthUserJpaEntity> findByUsername(String username);
}
