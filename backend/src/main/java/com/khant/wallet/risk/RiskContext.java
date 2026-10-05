package com.khant.wallet.risk;

public record RiskContext(
    Long userId,
    Long walletId,
    WalletOperation operation,
    long amount
) {
}
