package com.khant.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TransferRequest(
    @NotNull(message = "sourceWalletId is required")
    Long sourceWalletId,
    @NotNull(message = "targetWalletId is required")
    Long targetWalletId,
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    BigDecimal amount,
    String note
) {
}
