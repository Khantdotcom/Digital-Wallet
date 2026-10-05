package com.khant.wallet.wallet.ledger;

import com.khant.wallet.domain.Wallet;
import com.khant.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves money conservation: global debits == credits, and each wallet balance
 * matches the signed sum of its completed WALLET ledger legs.
 */
@Service
public class LedgerReconciliationService {

  private final LedgerEntryRepository ledgerEntryRepository;
  private final WalletRepository walletRepository;

  public LedgerReconciliationService(
      LedgerEntryRepository ledgerEntryRepository,
      WalletRepository walletRepository
  ) {
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.walletRepository = walletRepository;
  }

  @Transactional(readOnly = true)
  public ReconciliationReport reconcile() {
    BigDecimal totalDebits = nullToZero(ledgerEntryRepository.sumCompletedDebits());
    BigDecimal totalCredits = nullToZero(ledgerEntryRepository.sumCompletedCredits());
    boolean globallyBalanced = totalDebits.compareTo(totalCredits) == 0;

    List<WalletBalanceMismatch> mismatches = new ArrayList<>();
    for (Wallet wallet : walletRepository.findAll()) {
      BigDecimal ledgerBalance = nullToZero(ledgerEntryRepository.sumCompletedWalletSignedAmount(wallet.getId()));
      if (wallet.getBalance().compareTo(ledgerBalance) != 0) {
        mismatches.add(new WalletBalanceMismatch(wallet.getId(), wallet.getBalance(), ledgerBalance));
      }
    }

    return new ReconciliationReport(totalDebits, totalCredits, globallyBalanced, List.copyOf(mismatches));
  }

  private static BigDecimal nullToZero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO.setScale(2) : value;
  }

  public record WalletBalanceMismatch(Long walletId, BigDecimal walletBalance, BigDecimal ledgerBalance) {
  }

  public record ReconciliationReport(
      BigDecimal totalDebits,
      BigDecimal totalCredits,
      boolean globallyBalanced,
      List<WalletBalanceMismatch> walletMismatches
  ) {
    public boolean isFullyReconciled() {
      return globallyBalanced && walletMismatches.isEmpty();
    }
  }
}
