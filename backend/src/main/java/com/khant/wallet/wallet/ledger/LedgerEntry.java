package com.khant.wallet.wallet.ledger;

import com.khant.wallet.domain.Wallet;
import com.khant.wallet.domain.WalletTransaction;
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
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "movement_group_id", nullable = false)
  private UUID movementGroupId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "transaction_id", nullable = false)
  private WalletTransaction transaction;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "wallet_id")
  private Wallet wallet;

  @Enumerated(EnumType.STRING)
  @Column(name = "account_kind", nullable = false, length = 20)
  private LedgerAccountKind accountKind;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private LedgerDirection direction;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public Long getId() {
    return id;
  }

  public UUID getMovementGroupId() {
    return movementGroupId;
  }

  public WalletTransaction getTransaction() {
    return transaction;
  }

  public Wallet getWallet() {
    return wallet;
  }

  public LedgerAccountKind getAccountKind() {
    return accountKind;
  }

  public LedgerDirection getDirection() {
    return direction;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setMovementGroupId(UUID movementGroupId) {
    this.movementGroupId = movementGroupId;
  }

  public void setTransaction(WalletTransaction transaction) {
    this.transaction = transaction;
  }

  public void setWallet(Wallet wallet) {
    this.wallet = wallet;
  }

  public void setAccountKind(LedgerAccountKind accountKind) {
    this.accountKind = accountKind;
  }

  public void setDirection(LedgerDirection direction) {
    this.direction = direction;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }
}
