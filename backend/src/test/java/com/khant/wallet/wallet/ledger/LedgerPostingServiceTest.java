package com.khant.wallet.wallet.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khant.wallet.domain.TransactionType;
import com.khant.wallet.domain.WalletTransaction;
import com.khant.wallet.wallet.money.MoneyAmounts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerPostingServiceTest {

  @Test
  void assertBalanced_shouldPass_whenDebitsEqualCredits() {
    LedgerEntry debit = leg(LedgerDirection.DEBIT, 1000L);
    LedgerEntry credit = leg(LedgerDirection.CREDIT, 1000L);

    LedgerPostingService.assertBalanced(List.of(debit, credit));
  }

  @Test
  void assertBalanced_shouldFail_whenLegsDoNotConserveMoney() {
    LedgerEntry debit = leg(LedgerDirection.DEBIT, 1000L);
    LedgerEntry credit = leg(LedgerDirection.CREDIT, 999L);

    assertThatThrownBy(() -> LedgerPostingService.assertBalanced(List.of(debit, credit)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unbalanced");
  }

  @Test
  void moneyAmounts_shouldRejectNonPositiveMinorUnits() {
    assertThatThrownBy(() -> MoneyAmounts.requirePositiveMinorUnits(0L))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(MoneyAmounts.requirePositiveMinorUnits(120L)).isEqualTo(120L);
  }

  private static LedgerEntry leg(LedgerDirection direction, long amountMinorUnits) {
    WalletTransaction tx = new WalletTransaction();
    tx.setType(TransactionType.DEPOSIT);
    tx.setAmount(amountMinorUnits);
    tx.setMovementGroupId(UUID.randomUUID());

    LedgerEntry entry = new LedgerEntry();
    entry.setMovementGroupId(tx.getMovementGroupId());
    entry.setTransaction(tx);
    entry.setAccountKind(LedgerAccountKind.EXTERNAL);
    entry.setDirection(direction);
    entry.setAmount(amountMinorUnits);
    return entry;
  }
}
