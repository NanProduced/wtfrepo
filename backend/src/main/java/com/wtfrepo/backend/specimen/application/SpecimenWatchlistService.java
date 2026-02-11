package com.wtfrepo.backend.specimen.application;

import com.wtfrepo.backend.specimen.application.model.SpecimenModels.AddWatchlistResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.RemoveWatchlistResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.WatchlistItemResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.WatchlistPage;
import com.wtfrepo.backend.specimen.application.support.SpecimenConstants;
import com.wtfrepo.backend.specimen.application.support.SpecimenExceptions;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenArenaMetricsJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenArenaMetricsJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.entity.UserWatchlistItemJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.UserWatchlistItemJpaRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SpecimenWatchlistService {

  private final SpecimenJpaRepository specimenJpaRepository;
  private final SpecimenArenaMetricsJpaRepository specimenArenaMetricsJpaRepository;
  private final UserWatchlistItemJpaRepository userWatchlistItemJpaRepository;
  private final SpecimenContractProperties specimenContractProperties;

  public SpecimenWatchlistService(
      SpecimenJpaRepository specimenJpaRepository,
      SpecimenArenaMetricsJpaRepository specimenArenaMetricsJpaRepository,
      UserWatchlistItemJpaRepository userWatchlistItemJpaRepository,
      SpecimenContractProperties specimenContractProperties) {
    this.specimenJpaRepository = specimenJpaRepository;
    this.specimenArenaMetricsJpaRepository = specimenArenaMetricsJpaRepository;
    this.userWatchlistItemJpaRepository = userWatchlistItemJpaRepository;
    this.specimenContractProperties = specimenContractProperties;
  }


  @Transactional(readOnly = true)
  public WatchlistPage listWatchlist(String userId, String cursor, Integer limit, String sort) {
    WatchlistSort watchlistSort = resolveWatchlistSort(sort);
    int resolvedLimit = resolveLimit(limit);
    int pageIndex = resolvePageIndex(cursor);

    List<UserWatchlistItemJpaEntity> watchlistItems = userWatchlistItemJpaRepository.findByUserId(userId);
    if (watchlistItems.isEmpty()) {
      return new WatchlistPage(List.of(), null, false);
    }

    Set<String> specimenIds = watchlistItems.stream().map(UserWatchlistItemJpaEntity::getSpecimenId).collect(
        LinkedHashSet::new,
        LinkedHashSet::add,
        LinkedHashSet::addAll);
    Map<String, SpecimenJpaEntity> specimenMap =
        specimenJpaRepository.findAllById(specimenIds).stream()
            .collect(
                LinkedHashMap::new,
                (map, specimen) -> map.put(specimen.getSpecimenId(), specimen),
                LinkedHashMap::putAll);
    // TODO(M01-arena): replace direct metrics repository access with arena read facade.
    Map<String, SpecimenArenaMetricsJpaEntity> metricsMap =
        specimenArenaMetricsJpaRepository.findAllById(specimenIds).stream()
            .collect(
                LinkedHashMap::new,
                (map, metrics) -> map.put(metrics.getSpecimenId(), metrics),
                LinkedHashMap::putAll);

    List<WatchlistItemResult> itemResults = new ArrayList<>();
    for (UserWatchlistItemJpaEntity watchlistItem : watchlistItems) {
      SpecimenJpaEntity specimen = specimenMap.get(watchlistItem.getSpecimenId());
      if (specimen == null) {
        continue;
      }
      SpecimenArenaMetricsJpaEntity metrics = metricsMap.get(specimen.getSpecimenId());
      int elo = metrics == null ? 1200 : metrics.getElo();
      double hype = metrics == null ? 0.0D : metrics.getHype();
      itemResults.add(
          new WatchlistItemResult(
              watchlistItem.getItemId(),
              specimen.getSpecimenId(),
              specimen.getRepoFullName(),
              elo,
              hype,
              watchlistItem.getAddedAt()));
    }

    sortWatchlist(itemResults, watchlistSort);

    int startIndex = pageIndex * resolvedLimit;
    if (startIndex >= itemResults.size()) {
      return new WatchlistPage(List.of(), null, false);
    }

    int endIndex = Math.min(itemResults.size(), startIndex + resolvedLimit);
    List<WatchlistItemResult> pagedItems = itemResults.subList(startIndex, endIndex);
    boolean hasMore = endIndex < itemResults.size();
    String nextCursor = hasMore ? String.valueOf(pageIndex + 1) : null;

    return new WatchlistPage(pagedItems, nextCursor, hasMore);
  }

  @Transactional
  public AddWatchlistResult addWatchlist(String userId, String specimenId, String source) {
    requireSpecimen(specimenId);

    if (userWatchlistItemJpaRepository.existsByUserIdAndSpecimenId(userId, specimenId)) {
      return userWatchlistItemJpaRepository.findByUserId(userId).stream()
          .filter(item -> specimenId.equals(item.getSpecimenId()))
          .findFirst()
          .map(item -> new AddWatchlistResult(true, item.getItemId()))
          .orElse(new AddWatchlistResult(true, null));
    }

    long currentCount = userWatchlistItemJpaRepository.countByUserId(userId);
    if (currentCount >= specimenContractProperties.getWatchlistMaxItems()) {
      throw SpecimenExceptions.conflict(SpecimenConstants.Message.WATCHLIST_LIMIT_EXCEEDED);
    }

    UserWatchlistItemJpaEntity entity =
        UserWatchlistItemJpaEntity.create(userId, specimenId, StringUtils.hasText(source) ? source : "DETAIL");

    try {
      userWatchlistItemJpaRepository.save(entity);
      return new AddWatchlistResult(true, entity.getItemId());
    } catch (DataIntegrityViolationException ex) {
      return userWatchlistItemJpaRepository.findByUserId(userId).stream()
          .filter(item -> specimenId.equals(item.getSpecimenId()))
          .findFirst()
          .map(item -> new AddWatchlistResult(true, item.getItemId()))
          .orElseThrow(() -> ex);
    }
  }

  @Transactional
  public RemoveWatchlistResult removeWatchlist(String userId, String specimenId) {
    userWatchlistItemJpaRepository.deleteByUserIdAndSpecimenId(userId, specimenId);
    return new RemoveWatchlistResult(true);
  }

  private void requireSpecimen(String specimenId) {
    boolean exists = specimenJpaRepository.existsById(specimenId);
    if (!exists) {
      throw SpecimenExceptions.notFound(SpecimenConstants.Message.SPECIMEN_NOT_FOUND);
    }
  }

  private WatchlistSort resolveWatchlistSort(String sort) {
    if (!StringUtils.hasText(sort)) {
      return WatchlistSort.LATEST;
    }
    try {
      return WatchlistSort.valueOf(sort.toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw SpecimenExceptions.validation(SpecimenConstants.Message.INVALID_SORT);
    }
  }

  private int resolveLimit(Integer limit) {
    int resolved =
        limit == null || limit <= 0 ? specimenContractProperties.getWatchlistDefaultLimit() : limit;
    return Math.min(resolved, specimenContractProperties.getWatchlistMaxLimit());
  }

  private int resolvePageIndex(String cursor) {
    if (!StringUtils.hasText(cursor)) {
      return 0;
    }
    try {
      int pageIndex = Integer.parseInt(cursor);
      if (pageIndex < 0) {
        throw new NumberFormatException("negative");
      }
      return pageIndex;
    } catch (NumberFormatException ex) {
      throw SpecimenExceptions.validation(SpecimenConstants.Message.INVALID_CURSOR);
    }
  }

  private void sortWatchlist(List<WatchlistItemResult> items, WatchlistSort sort) {
    Comparator<WatchlistItemResult> comparator;
    if (sort == WatchlistSort.ELO) {
      comparator =
          Comparator.comparing(WatchlistItemResult::elo).reversed()
              .thenComparing(WatchlistItemResult::addedAt, Comparator.reverseOrder());
    } else if (sort == WatchlistSort.HYPE) {
      comparator =
          Comparator.comparing(WatchlistItemResult::hype).reversed()
              .thenComparing(WatchlistItemResult::addedAt, Comparator.reverseOrder());
    } else {
      comparator = Comparator.comparing(WatchlistItemResult::addedAt).reversed();
    }
    items.sort(comparator);
  }

  private enum WatchlistSort {
    LATEST,
    ELO,
    HYPE
  }
}
