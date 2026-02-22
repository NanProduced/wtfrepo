package com.wtfrepo.backend.economy.domain;

/** Wallet ledger entry types used by economy accounting records. */
public enum EconomyLedgerType {
  ARENA_VOTE_COST,
  DAILY_CLAIM,
  GAME_REWARD,
  BET,
  BET_WIN,
  HOUSE_STAKE,
  HOUSE_WIN,
  MOON_DOOM_BONUS,
  FORCE_SETTLE_REFUND,
  RAKE
}
