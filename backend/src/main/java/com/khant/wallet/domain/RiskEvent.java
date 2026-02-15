package com.khant.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "risk_events")
public class RiskEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "wallet_id")
  private Long walletId;

  @Column(nullable = false, length = 30)
  private String operation;

  @Column(name = "risk_score", nullable = false)
  private int riskScore;

  @Column(name = "risk_level", nullable = false, length = 20)
  private String riskLevel;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String reasons;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public void setWalletId(Long walletId) {
    this.walletId = walletId;
  }

  public void setOperation(String operation) {
    this.operation = operation;
  }

  public void setRiskScore(int riskScore) {
    this.riskScore = riskScore;
  }

  public void setRiskLevel(String riskLevel) {
    this.riskLevel = riskLevel;
  }

  public void setReasons(String reasons) {
    this.reasons = reasons;
  }
}
