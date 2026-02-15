package com.khant.wallet.risk;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TransferRiskRule implements RiskRule {

  private static final BigDecimal THRESHOLD = new BigDecimal("5000.00");

  @Override
  public Optional<RiskSignal> evaluate(RiskContext context) {
    if (context.operation() == WalletOperation.TRANSFER && context.amount().compareTo(THRESHOLD) >= 0) {
      return Optional.of(new RiskSignal("large transfer exceeds 5,000", 25));
    }

    return Optional.empty();
  }
}
