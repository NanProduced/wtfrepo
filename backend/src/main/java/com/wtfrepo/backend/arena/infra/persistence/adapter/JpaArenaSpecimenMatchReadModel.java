package com.wtfrepo.backend.arena.infra.persistence.adapter;

import com.wtfrepo.backend.arena.application.ArenaSpecimenMatchReadModel;
import com.wtfrepo.backend.arena.application.support.ArenaMatchProperties;
import com.wtfrepo.backend.specimen.domain.SpecimenStatus;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenGithubMetadataJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenOfficialCommentaryJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenTagJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenGithubMetadataJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenOfficialCommentaryJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenTagJpaRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * JPA-backed specimen match read model implementation for arena duel.
 */
@Component
@ConditionalOnBean(SpecimenJpaRepository.class)
public class JpaArenaSpecimenMatchReadModel implements ArenaSpecimenMatchReadModel {

  private final SpecimenJpaRepository specimenJpaRepository;
  private final SpecimenTagJpaRepository specimenTagJpaRepository;
  private final SpecimenOfficialCommentaryJpaRepository specimenOfficialCommentaryJpaRepository;
  private final SpecimenGithubMetadataJpaRepository specimenGithubMetadataJpaRepository;
  private final ArenaMatchProperties matchProperties;

  public JpaArenaSpecimenMatchReadModel(
      SpecimenJpaRepository specimenJpaRepository,
      SpecimenTagJpaRepository specimenTagJpaRepository,
      SpecimenOfficialCommentaryJpaRepository specimenOfficialCommentaryJpaRepository,
      SpecimenGithubMetadataJpaRepository specimenGithubMetadataJpaRepository,
      ArenaMatchProperties matchProperties) {
    this.specimenJpaRepository = specimenJpaRepository;
    this.specimenTagJpaRepository = specimenTagJpaRepository;
    this.specimenOfficialCommentaryJpaRepository = specimenOfficialCommentaryJpaRepository;
    this.specimenGithubMetadataJpaRepository = specimenGithubMetadataJpaRepository;
    this.matchProperties = matchProperties;
  }

  @Override
  @Transactional(readOnly = true)
  public List<SpecimenMatchCandidate> listActiveCandidates() {
    List<SpecimenJpaEntity> activeSpecimens = specimenJpaRepository.findByStatus(SpecimenStatus.ACTIVE);
    if (activeSpecimens.isEmpty()) {
      return List.of();
    }

    Set<String> specimenIds =
        activeSpecimens.stream()
            .map(SpecimenJpaEntity::getSpecimenId)
            .collect(
                LinkedHashSet::new,
                LinkedHashSet::add,
                LinkedHashSet::addAll);

    Map<String, SpecimenOfficialCommentaryJpaEntity> commentaryBySpecimenId =
        specimenOfficialCommentaryJpaRepository.findAllById(specimenIds).stream()
            .collect(
                LinkedHashMap::new,
                (map, item) -> map.put(item.getSpecimenId(), item),
                LinkedHashMap::putAll);

    Map<String, SpecimenGithubMetadataJpaEntity> metadataBySpecimenId =
        specimenGithubMetadataJpaRepository.findAllById(specimenIds).stream()
            .collect(
                LinkedHashMap::new,
                (map, item) -> map.put(item.getSpecimenId(), item),
                LinkedHashMap::putAll);

    Map<String, List<SpecimenTagJpaEntity>> tagsBySpecimenId =
        specimenTagJpaRepository.findBySpecimenIdIn(specimenIds).stream()
            .collect(
                LinkedHashMap::new,
                (map, tag) -> map.computeIfAbsent(tag.getSpecimenId(), ignored -> new java.util.ArrayList<>()).add(tag),
                LinkedHashMap::putAll);

    return activeSpecimens.stream()
        .map(
            specimen ->
                toCandidate(
                    specimen,
                    tagsBySpecimenId.getOrDefault(specimen.getSpecimenId(), List.of()),
                    commentaryBySpecimenId.get(specimen.getSpecimenId()),
                    metadataBySpecimenId.get(specimen.getSpecimenId())))
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  private SpecimenMatchCandidate toCandidate(
      SpecimenJpaEntity specimen,
      List<SpecimenTagJpaEntity> tags,
      SpecimenOfficialCommentaryJpaEntity commentary,
      SpecimenGithubMetadataJpaEntity metadata) {
    String species =
        tags.stream()
            .filter(tag -> matchProperties.getSpeciesDimensionKey().equalsIgnoreCase(tag.getDimensionKey()))
            .map(SpecimenTagJpaEntity::getTagKey)
            .findFirst()
            .orElse(null);

    if (!StringUtils.hasText(species)) {
      return null;
    }

    List<String> diagnosisTags =
        tags.stream()
            .filter(tag -> matchProperties.getDiagnosisDimensionKey().equalsIgnoreCase(tag.getDimensionKey()))
            .map(SpecimenTagJpaEntity::getTagKey)
            .filter(StringUtils::hasText)
            .distinct()
            .sorted(Comparator.naturalOrder())
            .toList();

    String title = specimen.getRepoFullName();
    String tagline = resolveTagline(specimen, commentary);
    String thumbnailUrl = metadata != null ? metadata.getOwnerAvatarUrl() : null;

    return new SpecimenMatchCandidate(
        specimen.getSpecimenId(), title, tagline, thumbnailUrl, species, diagnosisTags);
  }

  private String resolveTagline(
      SpecimenJpaEntity specimen, SpecimenOfficialCommentaryJpaEntity commentary) {
    if (commentary == null) {
      return specimen.getRepoFullName();
    }
    if (StringUtils.hasText(commentary.getOneLinerZh())) {
      return commentary.getOneLinerZh();
    }
    if (StringUtils.hasText(commentary.getOneLinerEn())) {
      return commentary.getOneLinerEn();
    }
    return specimen.getRepoFullName();
  }
}