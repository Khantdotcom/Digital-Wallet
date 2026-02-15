package com.khant.wallet.risk;

import com.khant.wallet.domain.TransactionType;
import com.khant.wallet.repository.WalletTransactionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class HighVelocityWithdrawalRiskRule implements RiskRule {

  private final WalletTransactionRepository walletTransactionRepository;

  public HighVelocityWithdrawalRiskRule(WalletTransactionRepository walletTransactionRepository) {
    this.walletTransactionRepository = walletTransactionRepository;
  }

  @Override
  public Optional<RiskSignal> evaluate(RiskContext context) {
    if (context.operation() != WalletOperation.WITHDRAW) {
      return Optional.empty();
    }

    Instant since = Instant.now().minus(1, ChronoUnit.HOURS);
    long recentWithdrawCount = walletTransactionRepository.countByWalletIdAndTypeAndCreatedAtAfter(
        context.walletId(),
        TransactionType.WITHDRAW,
        since
    );

    if (recentWithdrawCount >= 3) {
      return Optional.of(new RiskSignal("high withdrawal velocity (>=3/hour)", 35));
    }

    return Optional.empty();
  }
}
