package com.khant.wallet.risk;

import java.util.List;

public record RiskAssessment(int score, String level, List<String> reasons) {
}
