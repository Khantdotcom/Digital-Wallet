package com.khant.wallet.dto;

import com.khant.wallet.domain.TransactionStatus;
import com.khant.wallet.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionHistoryItemResponse(
    Long id,
    Long walletId,
    Long relatedWalletId,
    TransactionType type,
    TransactionStatus status,
    BigDecimal amount,
    String note,
    Instant createdAt,
    Instant completedAt,
    UUID movementGroupId,
    String failureReason
) {
}
