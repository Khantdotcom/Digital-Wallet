package com.khant.wallet.risk;

import java.util.Optional;

public interface RiskRule {

  Optional<RiskSignal> evaluate(RiskContext context);
}
