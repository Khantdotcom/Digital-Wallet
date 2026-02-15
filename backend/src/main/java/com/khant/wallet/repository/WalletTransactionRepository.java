package com.khant.wallet.repository;

import com.khant.wallet.domain.TransactionType;
import com.khant.wallet.domain.WalletTransaction;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

  long countByWalletIdAndTypeAndCreatedAtAfter(Long walletId, TransactionType type, Instant createdAt);

  Page<WalletTransaction> findByWalletIdAndWalletUserId(Long walletId, Long userId, Pageable pageable);
}
