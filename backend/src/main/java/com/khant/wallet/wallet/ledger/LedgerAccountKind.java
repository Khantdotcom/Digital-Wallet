package com.khant.wallet.wallet.ledger;

/**
 * Ledger account classification.
 *
 * <p>WALLET legs touch a user wallet. EXTERNAL represents the outside world
 * (cash in / cash out) so deposits and withdrawals still balance globally.
 */
public enum LedgerAccountKind {
  WALLET,
  EXTERNAL
}
