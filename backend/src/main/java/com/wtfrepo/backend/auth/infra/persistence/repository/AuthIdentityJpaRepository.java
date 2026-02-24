package com.wtfrepo.backend.auth.infra.persistence.repository;

import com.wtfrepo.backend.auth.domain.OAuthProvider;
import com.wtfrepo.backend.auth.infra.persistence.entity.AuthIdentityJpaEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthIdentityJpaRepository extends JpaRepository<AuthIdentityJpaEntity, String> {

  Optional<AuthIdentityJpaEntity> findByProviderAndProviderSubject(
      OAuthProvider provider, String providerSubject);

  Optional<AuthIdentityJpaEntity> findFirstByUserIdAndProvider(
      String userId, OAuthProvider provider);

  List<AuthIdentityJpaEntity> findByUserId(String userId);
}
