package com.khant.wallet.wallet.ledger;

import com.khant.wallet.domain.Wallet;
import com.khant.wallet.repository.WalletRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves money conservation in minor units: global debits == credits, and each
 * wallet balance matches the signed sum of its completed WALLET ledger legs.
 *
 * <p>Service / test-only for Phase 01 — no admin HTTP reconciliation endpoint yet
 * (deferred by product decision; add later when an operator UI needs it).
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
    long totalDebits = nullToZero(ledgerEntryRepository.sumCompletedDebits());
    long totalCredits = nullToZero(ledgerEntryRepository.sumCompletedCredits());
    boolean globallyBalanced = totalDebits == totalCredits;

    List<WalletBalanceMismatch> mismatches = new ArrayList<>();
    for (Wallet wallet : walletRepository.findAll()) {
      long ledgerBalance = nullToZero(ledgerEntryRepository.sumCompletedWalletSignedAmount(wallet.getId()));
      if (wallet.getBalance() != ledgerBalance) {
        mismatches.add(new WalletBalanceMismatch(wallet.getId(), wallet.getBalance(), ledgerBalance));
      }
    }

    return new ReconciliationReport(totalDebits, totalCredits, globallyBalanced, List.copyOf(mismatches));
  }

  private static long nullToZero(Long value) {
    return value == null ? 0L : value;
  }

  public record WalletBalanceMismatch(Long walletId, long walletBalance, long ledgerBalance) {
  }

  public record ReconciliationReport(
      long totalDebits,
      long totalCredits,
      boolean globallyBalanced,
      List<WalletBalanceMismatch> walletMismatches
  ) {
    public boolean isFullyReconciled() {
      return globallyBalanced && walletMismatches.isEmpty();
    }
  }
}
