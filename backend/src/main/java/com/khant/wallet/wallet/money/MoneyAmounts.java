package com.khant.wallet.wallet.money;

/**
 * Safe money helpers. Money Engine stores cash as integer <em>minor units</em>
 * (cents for a two-decimal currency) — never {@code double}/{@code float}, and
 * no longer {@link java.math.BigDecimal} scale-2 on the ledger path.
 */
public final class MoneyAmounts {

  /** Assumed ISO-style exponent for the demo currency (USD cents). */
  public static final int MINOR_UNITS_EXPONENT = 2;

  private MoneyAmounts() {
  }

  /**
   * Validate a money amount in minor units. Rejects null and non-positive values.
   */
  public static long requirePositiveMinorUnits(Long amountMinorUnits) {
    if (amountMinorUnits == null) {
      throw new IllegalArgumentException("amount is required");
    }
    if (amountMinorUnits <= 0L) {
      throw new IllegalArgumentException("amount must be greater than 0 minor units");
    }
    return amountMinorUnits;
  }
}
