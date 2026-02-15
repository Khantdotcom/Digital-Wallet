package com.khant.wallet.risk;

import java.math.BigDecimal;

public record RiskContext(
    Long userId,
    Long walletId,
    WalletOperation operation,
    BigDecimal amount
) {
}
