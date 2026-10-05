package com.khant.wallet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Money movement request. {@code amount} is integer minor units (cents), not major currency units.
 */
public record MoneyRequest(
    @NotNull(message = "amount is required")
    @Min(value = 1, message = "amount must be at least 1 minor unit")
    Long amount,
    String note
) {
}
