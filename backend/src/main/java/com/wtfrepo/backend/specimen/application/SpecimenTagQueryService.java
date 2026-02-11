package com.wtfrepo.backend.specimen.application;

import com.wtfrepo.backend.specimen.application.model.SpecimenModels.TagDimensionResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.TagListResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.TagResult;
import com.wtfrepo.backend.specimen.application.support.SpecimenJsonCodec;
import com.wtfrepo.backend.specimen.infra.persistence.entity.TagDefinitionJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.TagDimensionJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.repository.TagDefinitionJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.TagDimensionJpaRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpecimenTagQueryService {

  private final TagDimensionJpaRepository tagDimensionJpaRepository;
  private final TagDefinitionJpaRepository tagDefinitionJpaRepository;
  private final SpecimenContractProperties specimenContractProperties;
  private final SpecimenJsonCodec specimenJsonCodec;

  public SpecimenTagQueryService(
      TagDimensionJpaRepository tagDimensionJpaRepository,
      TagDefinitionJpaRepository tagDefinitionJpaRepository,
      SpecimenContractProperties specimenContractProperties,
      SpecimenJsonCodec specimenJsonCodec) {
    this.tagDimensionJpaRepository = tagDimensionJpaRepository;
    this.tagDefinitionJpaRepository = tagDefinitionJpaRepository;
    this.specimenContractProperties = specimenContractProperties;
    this.specimenJsonCodec = specimenJsonCodec;
  }

  @Transactional(readOnly = true)
  public TagListResult listTags() {
    List<TagDimensionJpaEntity> dimensions =
        tagDimensionJpaRepository.findAllByOrderBySortOrderAscDimensionKeyAsc();

    if (dimensions.isEmpty()) {
      return new TagListResult(specimenContractProperties.getTagConfigVersion(), List.of());
    }

    List<String> dimensionKeys = dimensions.stream().map(TagDimensionJpaEntity::getDimensionKey).toList();
    List<TagDefinitionJpaEntity> definitions =
        tagDefinitionJpaRepository.findByDimensionKeyInOrderByDisplayOrderAscTagKeyAsc(dimensionKeys);

    Map<String, List<TagResult>> tagsByDimensionKey = new LinkedHashMap<>();
    for (TagDefinitionJpaEntity definition : definitions) {
      if (!"ACTIVE".equalsIgnoreCase(definition.getStatus())) {
        continue;
      }
      tagsByDimensionKey
          .computeIfAbsent(definition.getDimensionKey(), key -> new ArrayList<>())
          .add(
              new TagResult(
                  definition.getTagKey(),
                  definition.getNameZh(),
                  definition.getNameEn(),
                  specimenJsonCodec.readObject(definition.getUiMetaJson())));
    }

    List<TagDimensionResult> resultDimensions =
        dimensions.stream()
            .filter(dimension -> "ACTIVE".equalsIgnoreCase(dimension.getStatus()))
            .map(
                dimension ->
                    new TagDimensionResult(
                        dimension.getDimensionKey(),
                        dimension.getNameZh(),
                        dimension.getNameEn(),
                        dimension.getSelectMode(),
                        dimension.isRequired(),
                        dimension.getSortOrder(),
                        specimenJsonCodec.readObject(dimension.getUiMetaJson()),
                        tagsByDimensionKey.getOrDefault(dimension.getDimensionKey(), List.of())))
            .toList();

    return new TagListResult(specimenContractProperties.getTagConfigVersion(), resultDimensions);
  }
}
