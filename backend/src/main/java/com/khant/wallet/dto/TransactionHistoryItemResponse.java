package com.khant.wallet.dto;

import com.khant.wallet.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionHistoryItemResponse(
    Long id,
    Long walletId,
    Long relatedWalletId,
    TransactionType type,
    BigDecimal amount,
    String note,
    Instant createdAt
) {
}
