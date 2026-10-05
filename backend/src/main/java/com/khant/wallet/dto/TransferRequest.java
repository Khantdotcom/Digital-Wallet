package com.khant.wallet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Transfer request. {@code amount} is integer minor units (cents).
 */
public record TransferRequest(
    @NotNull(message = "sourceWalletId is required")
    Long sourceWalletId,
    @NotNull(message = "targetWalletId is required")
    Long targetWalletId,
    @NotNull(message = "amount is required")
    @Min(value = 1, message = "amount must be at least 1 minor unit")
    Long amount,
    String note
) {
}
