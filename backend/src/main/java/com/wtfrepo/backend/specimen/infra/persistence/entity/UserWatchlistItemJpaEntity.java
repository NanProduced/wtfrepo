package com.wtfrepo.backend.specimen.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** User watchlist membership for specimen quick access and recommendation signals. */
@Getter
@Entity
@Table(
    name = "user_watchlist_item",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_user_watchlist_item_user_specimen",
          columnNames = {"user_id", "specimen_id"})
    })
public class UserWatchlistItemJpaEntity {

  @Id
  @Column(name = "item_id", nullable = false, length = 64)
  private String itemId;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "specimen_id", nullable = false, length = 64)
  private String specimenId;

  @Column(name = "source", nullable = false, length = 64)
  private String source;

  @Column(name = "added_at", nullable = false)
  private Instant addedAt;

  protected UserWatchlistItemJpaEntity() {}

  public static UserWatchlistItemJpaEntity create(String userId, String specimenId, String source) {
    UserWatchlistItemJpaEntity entity = new UserWatchlistItemJpaEntity();
    entity.itemId = "wl_" + UUID.randomUUID();
    entity.userId = userId;
    entity.specimenId = specimenId;
    entity.source = source;
    entity.addedAt = Instant.now();
    return entity;
  }
}
