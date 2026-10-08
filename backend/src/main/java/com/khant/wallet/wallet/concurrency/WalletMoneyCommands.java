package com.khant.wallet.wallet.concurrency;

import com.khant.wallet.domain.Wallet;
import com.khant.wallet.dto.MoneyRequest;
import com.khant.wallet.dto.TransferRequest;
import com.khant.wallet.service.WalletService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Public money-mutation entry that retries transient deadlocks outside the
 * transactional boundary so each attempt starts a fresh transaction.
 *
 * <p>{@link WalletService} methods stay {@code @Transactional}; calling them
 * through this facade is required for HTTP and stress harnesses.
 */
@Service
public class WalletMoneyCommands {

  private final WalletService walletService;
  private final DeadlockRetryExecutor deadlockRetryExecutor;

  public WalletMoneyCommands(WalletService walletService, DeadlockRetryExecutor deadlockRetryExecutor) {
    this.walletService = walletService;
    this.deadlockRetryExecutor = deadlockRetryExecutor;
  }

  public Wallet deposit(Long userId, Long walletId, MoneyRequest request) {
    return deadlockRetryExecutor.execute(() -> walletService.deposit(userId, walletId, request));
  }

  public Wallet withdraw(Long userId, Long walletId, MoneyRequest request) {
    return deadlockRetryExecutor.execute(() -> walletService.withdraw(userId, walletId, request));
  }

  public List<Wallet> transfer(Long userId, TransferRequest request) {
    return deadlockRetryExecutor.execute(() -> walletService.transfer(userId, request));
  }
}
