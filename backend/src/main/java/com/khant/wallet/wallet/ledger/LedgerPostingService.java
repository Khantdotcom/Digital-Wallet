package com.khant.wallet.wallet.ledger;

import com.khant.wallet.domain.Wallet;
import com.khant.wallet.domain.WalletTransaction;
import com.khant.wallet.wallet.money.MoneyAmounts;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Builds balanced double-entry legs for a money movement.
 *
 * <p>Invariant: for every movement group, sum(DEBIT) == sum(CREDIT).
 */
@Service
public class LedgerPostingService {

  private final LedgerEntryRepository ledgerEntryRepository;

  public LedgerPostingService(LedgerEntryRepository ledgerEntryRepository) {
    this.ledgerEntryRepository = ledgerEntryRepository;
  }

  public List<LedgerEntry> postDeposit(WalletTransaction movement, Wallet wallet, BigDecimal amount) {
    BigDecimal money = MoneyAmounts.requirePositiveMoney(amount);
    UUID groupId = movement.getMovementGroupId();

    List<LedgerEntry> legs = List.of(
        walletLeg(groupId, movement, wallet, LedgerDirection.CREDIT, money),
        externalLeg(groupId, movement, LedgerDirection.DEBIT, money)
    );
    return persistBalanced(legs);
  }

  public List<LedgerEntry> postWithdraw(WalletTransaction movement, Wallet wallet, BigDecimal amount) {
    BigDecimal money = MoneyAmounts.requirePositiveMoney(amount);
    UUID groupId = movement.getMovementGroupId();

    List<LedgerEntry> legs = List.of(
        walletLeg(groupId, movement, wallet, LedgerDirection.DEBIT, money),
        externalLeg(groupId, movement, LedgerDirection.CREDIT, money)
    );
    return persistBalanced(legs);
  }

  public List<LedgerEntry> postTransfer(
      WalletTransaction sourceMovement,
      WalletTransaction targetMovement,
      Wallet source,
      Wallet target,
      BigDecimal amount
  ) {
    BigDecimal money = MoneyAmounts.requirePositiveMoney(amount);
    UUID groupId = sourceMovement.getMovementGroupId();
    if (!groupId.equals(targetMovement.getMovementGroupId())) {
      throw new IllegalStateException("transfer legs must share movement_group_id");
    }

    List<LedgerEntry> legs = List.of(
        walletLeg(groupId, sourceMovement, source, LedgerDirection.DEBIT, money),
        walletLeg(groupId, targetMovement, target, LedgerDirection.CREDIT, money)
    );
    return persistBalanced(legs);
  }

  private List<LedgerEntry> persistBalanced(List<LedgerEntry> legs) {
    assertBalanced(legs);
    List<LedgerEntry> saved = new ArrayList<>(legs.size());
    for (LedgerEntry leg : legs) {
      saved.add(ledgerEntryRepository.save(leg));
    }
    return saved;
  }

  static void assertBalanced(List<LedgerEntry> legs) {
    BigDecimal debits = BigDecimal.ZERO;
    BigDecimal credits = BigDecimal.ZERO;
    for (LedgerEntry leg : legs) {
      if (leg.getDirection() == LedgerDirection.DEBIT) {
        debits = debits.add(leg.getAmount());
      } else if (leg.getDirection() == LedgerDirection.CREDIT) {
        credits = credits.add(leg.getAmount());
      } else {
        throw new IllegalStateException("ledger leg missing direction");
      }
    }
    if (debits.compareTo(credits) != 0) {
      throw new IllegalStateException("unbalanced ledger posting: debits=" + debits + " credits=" + credits);
    }
  }

  private static LedgerEntry walletLeg(
      UUID groupId,
      WalletTransaction movement,
      Wallet wallet,
      LedgerDirection direction,
      BigDecimal amount
  ) {
    LedgerEntry entry = new LedgerEntry();
    entry.setMovementGroupId(groupId);
    entry.setTransaction(movement);
    entry.setWallet(wallet);
    entry.setAccountKind(LedgerAccountKind.WALLET);
    entry.setDirection(direction);
    entry.setAmount(amount);
    return entry;
  }

  private static LedgerEntry externalLeg(
      UUID groupId,
      WalletTransaction movement,
      LedgerDirection direction,
      BigDecimal amount
  ) {
    LedgerEntry entry = new LedgerEntry();
    entry.setMovementGroupId(groupId);
    entry.setTransaction(movement);
    entry.setWallet(null);
    entry.setAccountKind(LedgerAccountKind.EXTERNAL);
    entry.setDirection(direction);
    entry.setAmount(amount);
    return entry;
  }
}
