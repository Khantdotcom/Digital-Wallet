package com.khant.wallet.wallet.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Safe money helpers. TravelPay stores cash as {@link BigDecimal} with scale 2
 * (DECIMAL in Postgres) — never {@code double}/{@code float}.
 */
public final class MoneyAmounts {

  public static final int SCALE = 2;

  private MoneyAmounts() {
  }

  /**
   * Normalize and validate a money amount. Rejects null, non-positive values,
   * and more than two decimal places (avoids silent rounding of millicents).
   */
  public static BigDecimal requirePositiveMoney(BigDecimal amount) {
    if (amount == null) {
      throw new IllegalArgumentException("amount is required");
    }
    if (amount.scale() > SCALE) {
      throw new IllegalArgumentException("amount must have at most " + SCALE + " decimal places");
    }
    BigDecimal normalized = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("amount must be greater than 0");
    }
    return normalized;
  }
}
