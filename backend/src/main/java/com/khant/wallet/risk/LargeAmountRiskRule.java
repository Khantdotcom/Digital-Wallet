package com.khant.wallet.risk;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class LargeAmountRiskRule implements RiskRule {

  private static final BigDecimal THRESHOLD = new BigDecimal("10000.00");

  @Override
  public Optional<RiskSignal> evaluate(RiskContext context) {
    if (context.amount().compareTo(THRESHOLD) >= 0) {
      return Optional.of(new RiskSignal("amount exceeds 10,000", 40));
    }

    return Optional.empty();
  }
}
