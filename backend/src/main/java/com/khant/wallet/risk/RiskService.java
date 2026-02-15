package com.khant.wallet.risk;

import com.khant.wallet.domain.RiskEvent;
import com.khant.wallet.repository.RiskEventRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RiskService {

  private final List<RiskRule> riskRules;
  private final RiskEventRepository riskEventRepository;

  public RiskService(List<RiskRule> riskRules, RiskEventRepository riskEventRepository) {
    this.riskRules = riskRules;
    this.riskEventRepository = riskEventRepository;
  }

  public RiskAssessment assessAndRecord(Long userId, Long walletId, WalletOperation operation, BigDecimal amount) {
    RiskContext context = new RiskContext(userId, walletId, operation, amount);

    int totalScore = 0;
    List<String> reasons = new ArrayList<>();
    for (RiskRule riskRule : riskRules) {
      RiskSignal signal = riskRule.evaluate(context).orElse(null);
      if (signal != null) {
        reasons.add(signal.reason());
        totalScore += signal.score();
      }
    }

    String level = toLevel(totalScore);
    if (reasons.isEmpty()) {
      reasons.add("no rules triggered");
    }

    RiskEvent riskEvent = new RiskEvent();
    riskEvent.setUserId(userId);
    riskEvent.setWalletId(walletId);
    riskEvent.setOperation(operation.name());
    riskEvent.setRiskScore(totalScore);
    riskEvent.setRiskLevel(level);
    riskEvent.setReasons(String.join("; ", reasons));
    riskEventRepository.save(riskEvent);

    return new RiskAssessment(totalScore, level, reasons);
  }

  private String toLevel(int totalScore) {
    if (totalScore >= 60) {
      return "HIGH";
    }
    if (totalScore >= 30) {
      return "MEDIUM";
    }
    return "LOW";
  }
}
