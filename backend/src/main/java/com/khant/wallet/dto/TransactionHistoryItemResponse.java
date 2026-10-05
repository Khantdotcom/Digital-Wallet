package com.khant.wallet.dto;

import com.khant.wallet.domain.TransactionStatus;
import com.khant.wallet.domain.TransactionType;
import java.time.Instant;
import java.util.UUID;

/** History item. Amounts are integer minor units (cents). */
public record TransactionHistoryItemResponse(
    Long id,
    Long walletId,
    Long relatedWalletId,
    TransactionType type,
    TransactionStatus status,
    long amount,
    String note,
    Instant createdAt,
    Instant completedAt,
    UUID movementGroupId,
    String failureReason
) {
}
