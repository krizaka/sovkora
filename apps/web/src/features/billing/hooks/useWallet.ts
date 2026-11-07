"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { BillingApi } from "@/services/billing.api";
import { availableCredits, type Wallet } from "@krizaka/orazaka-shared";

/** How low the balance must fall, as a share of what it was, before the banner shows. */
const DEFAULT_LOW_BALANCE_RATIO = 0.1;

export interface WalletState {
  wallet: Wallet | null;
  available: number;
  isLow: boolean;
  isLoading: boolean;
  refresh: () => Promise<void>;
}

/**
 * The signed-in user's balance, kept current from the SSE stream.
 *
 * Push rather than poll. A balance that only refreshes on navigation is a number
 * the user acts on after it stopped being true — and the whole reason the design
 * insists on showing cost before the click is that acting on a stale figure is
 * how an unexpected debit happens.
 *
 * The stream is the same one job progress rides; the server distinguishes the two
 * by event name. A dropped event costs a stale figure until the next one or the
 * next load, which is why the initial fetch still happens and nothing here
 * retries a lost message.
 */
export function useWallet(): WalletState {
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [lowBalanceSignal, setLowBalanceSignal] = useState(false);
  const sourceRef = useRef<EventSource | null>(null);

  const refresh = useCallback(async () => {
    try {
      setWallet(await BillingApi.fetchWallet());
    } catch {
      // A wallet that cannot be read must not blank the UI it decorates; the last
      // known figure stays until the next successful read.
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // Deferred: loading synchronously inside the effect sets state during the same
    // commit and cascades a second render.
    const timer = setTimeout(() => void refresh(), 0);
    return () => clearTimeout(timer);
  }, [refresh]);

  useEffect(() => {
    const source = new EventSource("/api/v1/jobs/stream");
    sourceRef.current = source;

    source.addEventListener("wallet-balance", () => {
      // The event carries the new balance, but the wallet has two buckets and a
      // held figure; re-reading is cheaper than teaching the client to reassemble
      // them from a partial payload it would then have to keep in step.
      void refresh();
    });

    source.addEventListener("wallet-low", () => {
      setLowBalanceSignal(true);
      void refresh();
    });

    return () => {
      source.close();
      sourceRef.current = null;
    };
  }, [refresh]);

  const available = wallet ? availableCredits(wallet) : 0;
  const granted = wallet ? wallet.balanceGranted + wallet.balancePurchased : 0;
  const isLow = lowBalanceSignal || (granted > 0 && available <= granted * DEFAULT_LOW_BALANCE_RATIO);

  return { wallet, available, isLow, isLoading, refresh };
}
