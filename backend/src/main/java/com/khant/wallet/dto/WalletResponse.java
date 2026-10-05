package com.khant.wallet.dto;

/** Wallet view. {@code balance} is integer minor units (cents). */
public record WalletResponse(
    Long id,
    String name,
    long balance
) {
}
