package com.wtfrepo.backend.specimen.infra.persistence.repository;

import com.wtfrepo.backend.specimen.infra.persistence.entity.TagDimensionJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagDimensionJpaRepository extends JpaRepository<TagDimensionJpaEntity, String> {

  List<TagDimensionJpaEntity> findAllByOrderBySortOrderAscDimensionKeyAsc();
}
