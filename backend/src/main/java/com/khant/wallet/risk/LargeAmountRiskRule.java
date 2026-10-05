package com.khant.wallet.risk;

import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class LargeAmountRiskRule implements RiskRule {

  /** 10,000.00 major units in cents. */
  private static final long THRESHOLD_MINOR_UNITS = 1_000_000L;

  @Override
  public Optional<RiskSignal> evaluate(RiskContext context) {
    if (context.amount() >= THRESHOLD_MINOR_UNITS) {
      return Optional.of(new RiskSignal("amount exceeds 10,000 major units", 40));
    }

    return Optional.empty();
  }
}
