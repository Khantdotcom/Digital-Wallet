package com.khant.wallet.domain;

/**
 * Lifecycle of a user-facing money movement.
 *
 * <p>PENDING is written first so mid-flight failures leave an auditable FAILED
 * row instead of silent absence. COMPLETED means balances and ledger legs were
 * applied together. FAILED means no balance change and no ledger legs.
 */
public enum TransactionStatus {
  PENDING,
  COMPLETED,
  FAILED
}
