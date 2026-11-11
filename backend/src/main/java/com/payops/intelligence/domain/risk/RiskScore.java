package com.payops.intelligence.domain.risk;

import java.util.List;

public record RiskScore(int score, String level, List<String> factors) {
}
