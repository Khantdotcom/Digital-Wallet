package com.khant.wallet.wallet.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khant.wallet.domain.TransactionType;
import com.khant.wallet.domain.Wallet;
import com.khant.wallet.domain.WalletTransaction;
import com.khant.wallet.wallet.money.MoneyAmounts;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerPostingServiceTest {

  @Test
  void assertBalanced_shouldPass_whenDebitsEqualCredits() {
    LedgerEntry debit = leg(LedgerDirection.DEBIT, "10.00");
    LedgerEntry credit = leg(LedgerDirection.CREDIT, "10.00");

    LedgerPostingService.assertBalanced(List.of(debit, credit));
  }

  @Test
  void assertBalanced_shouldFail_whenLegsDoNotConserveMoney() {
    LedgerEntry debit = leg(LedgerDirection.DEBIT, "10.00");
    LedgerEntry credit = leg(LedgerDirection.CREDIT, "9.99");

    assertThatThrownBy(() -> LedgerPostingService.assertBalanced(List.of(debit, credit)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unbalanced");
  }

  @Test
  void moneyAmounts_shouldRejectFloatingStyleExtraScale() {
    assertThatThrownBy(() -> MoneyAmounts.requirePositiveMoney(new BigDecimal("1.234")))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(MoneyAmounts.requirePositiveMoney(new BigDecimal("1.20")))
        .isEqualByComparingTo("1.20");
  }

  private static LedgerEntry leg(LedgerDirection direction, String amount) {
    WalletTransaction tx = new WalletTransaction();
    tx.setType(TransactionType.DEPOSIT);
    tx.setAmount(new BigDecimal(amount));
    tx.setMovementGroupId(UUID.randomUUID());

    LedgerEntry entry = new LedgerEntry();
    entry.setMovementGroupId(tx.getMovementGroupId());
    entry.setTransaction(tx);
    entry.setAccountKind(LedgerAccountKind.EXTERNAL);
    entry.setDirection(direction);
    entry.setAmount(new BigDecimal(amount));
    return entry;
  }
}
