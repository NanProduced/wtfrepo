"use client";

import { create } from "zustand";
import {
  claimWalletDaily,
  getWallet,
  getWalletLedger,
} from "@/shared/api/wallet";
import type {
  WalletDailyClaimResult,
  WalletLedgerItem,
  WalletView,
} from "@/shared/types/wallet";

interface WalletState {
  wallet: WalletView | null;
  ledger: WalletLedgerItem[];
  ledgerCursor: string | null;
  ledgerHasMore: boolean;
  isLoadingWallet: boolean;
  isLoadingLedger: boolean;
  isSubmitting: boolean;
  lastError: string | null;
  fetchWallet: () => Promise<void>;
  fetchLedger: (params?: { limit?: number; reason?: string }) => Promise<void>;
  fetchMoreLedger: (params?: { limit?: number; reason?: string }) => Promise<void>;
  claimDaily: () => Promise<WalletDailyClaimResult | null>;
  reset: () => void;
}

const initialState = {
  wallet: null as WalletView | null,
  ledger: [] as WalletLedgerItem[],
  ledgerCursor: null as string | null,
  ledgerHasMore: false,
  isLoadingWallet: false,
  isLoadingLedger: false,
  isSubmitting: false,
  lastError: null as string | null,
};

export const useWalletStore = create<WalletState>((set, get) => ({
  ...initialState,

  fetchWallet: async () => {
    set((state) => ({ ...state, isLoadingWallet: true, lastError: null }));
    try {
      const wallet = await getWallet();
      set((state) => ({
        ...state,
        wallet,
        isLoadingWallet: false,
      }));
    } catch (error) {
      set((state) => ({
        ...state,
        isLoadingWallet: false,
        lastError:
          typeof error === "object" && error !== null && "message" in error
            ? String((error as { message: string }).message)
            : "Failed to fetch wallet.",
      }));
    }
  },

  fetchLedger: async (params = {}) => {
    set((state) => ({ ...state, isLoadingLedger: true, lastError: null }));
    try {
      const page = await getWalletLedger({
        limit: params.limit,
        reason: params.reason,
      });
      set((state) => ({
        ...state,
        ledger: page.items,
        ledgerCursor: page.nextCursor,
        ledgerHasMore: page.hasMore,
        isLoadingLedger: false,
      }));
    } catch (error) {
      set((state) => ({
        ...state,
        isLoadingLedger: false,
        lastError:
          typeof error === "object" && error !== null && "message" in error
            ? String((error as { message: string }).message)
            : "Failed to fetch wallet ledger.",
      }));
    }
  },

  fetchMoreLedger: async (params = {}) => {
    const cursor = get().ledgerCursor;
    if (!cursor || !get().ledgerHasMore) {
      return;
    }
    set((state) => ({ ...state, isLoadingLedger: true, lastError: null }));
    try {
      const page = await getWalletLedger({
        cursor,
        limit: params.limit,
        reason: params.reason,
      });
      set((state) => ({
        ...state,
        ledger: [...state.ledger, ...page.items],
        ledgerCursor: page.nextCursor,
        ledgerHasMore: page.hasMore,
        isLoadingLedger: false,
      }));
    } catch (error) {
      set((state) => ({
        ...state,
        isLoadingLedger: false,
        lastError:
          typeof error === "object" && error !== null && "message" in error
            ? String((error as { message: string }).message)
            : "Failed to load more wallet records.",
      }));
    }
  },

  claimDaily: async () => {
    set((state) => ({ ...state, isSubmitting: true, lastError: null }));
    try {
      const result = await claimWalletDaily();
      set((state) => ({
        ...state,
        wallet: state.wallet
          ? {
              ...state.wallet,
              balance: result.balanceAfter,
              dailyClaimed: true,
            }
          : state.wallet,
        isSubmitting: false,
      }));
      return result;
    } catch (error) {
      set((state) => ({
        ...state,
        isSubmitting: false,
        lastError:
          typeof error === "object" && error !== null && "message" in error
            ? String((error as { message: string }).message)
            : "Failed to claim daily reward.",
      }));
      return null;
    }
  },

  reset: () => {
    set({ ...initialState });
  },
}));

