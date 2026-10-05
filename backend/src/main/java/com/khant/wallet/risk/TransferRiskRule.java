package com.khant.wallet.risk;

import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TransferRiskRule implements RiskRule {

  /** 5,000.00 major units in cents. */
  private static final long THRESHOLD_MINOR_UNITS = 500_000L;

  @Override
  public Optional<RiskSignal> evaluate(RiskContext context) {
    if (context.operation() == WalletOperation.TRANSFER && context.amount() >= THRESHOLD_MINOR_UNITS) {
      return Optional.of(new RiskSignal("large transfer exceeds 5,000 major units", 25));
    }

    return Optional.empty();
  }
}
