package com.khant.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions")
public class WalletTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "wallet_id", nullable = false)
  private Wallet wallet;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "related_wallet_id")
  private Wallet relatedWallet;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TransactionType type;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(length = 255)
  private String note;

  public Long getId() {
    return id;
  }

  public Wallet getWallet() {
    return wallet;
  }

  public Wallet getRelatedWallet() {
    return relatedWallet;
  }

  public TransactionType getType() {
    return type;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getNote() {
    return note;
  }

  public void setWallet(Wallet wallet) {
    this.wallet = wallet;
  }

  public void setRelatedWallet(Wallet relatedWallet) {
    this.relatedWallet = relatedWallet;
  }

  public void setType(TransactionType type) {
    this.type = type;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public void setNote(String note) {
    this.note = note;
  }
}
