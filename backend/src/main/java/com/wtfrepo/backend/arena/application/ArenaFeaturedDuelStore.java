package com.wtfrepo.backend.arena.application;

import java.util.Optional;

/** Store for anonymous featured duel payload. */
public interface ArenaFeaturedDuelStore {

  Optional<ArenaDuelService.DuelResult> findFeaturedDuel();

  void saveFeaturedDuel(ArenaDuelService.DuelResult duelResult);
}

