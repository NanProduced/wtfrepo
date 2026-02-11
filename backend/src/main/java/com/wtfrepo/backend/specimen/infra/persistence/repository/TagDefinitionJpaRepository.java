package com.wtfrepo.backend.specimen.infra.persistence.repository;

import com.wtfrepo.backend.specimen.infra.persistence.entity.TagDefinitionJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.TagDefinitionJpaId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagDefinitionJpaRepository
    extends JpaRepository<TagDefinitionJpaEntity, TagDefinitionJpaId> {

  List<TagDefinitionJpaEntity> findByDimensionKeyInOrderByDisplayOrderAscTagKeyAsc(
      Collection<String> dimensionKeys);

  List<TagDefinitionJpaEntity> findByTagKeyInOrderByDisplayOrderAscTagKeyAsc(
      Collection<String> tagKeys);
}
