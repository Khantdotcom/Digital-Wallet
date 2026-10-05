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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

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

  /** Amount in integer minor units (cents). */
  @Column(nullable = false)
  private long amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TransactionStatus status = TransactionStatus.PENDING;

  @Column(name = "movement_group_id", nullable = false)
  private UUID movementGroupId = UUID.randomUUID();

  @Column(name = "created_by_user_id")
  private Long createdByUserId;

  @Column(name = "failure_reason", length = 255)
  private String failureReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Column(length = 255)
  private String note;

  @PreUpdate
  public void onUpdate() {
    updatedAt = Instant.now();
  }

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

  public long getAmount() {
    return amount;
  }

  public TransactionStatus getStatus() {
    return status;
  }

  public UUID getMovementGroupId() {
    return movementGroupId;
  }

  public Long getCreatedByUserId() {
    return createdByUserId;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
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

  public void setAmount(long amount) {
    this.amount = amount;
  }

  public void setStatus(TransactionStatus status) {
    this.status = status;
  }

  public void setMovementGroupId(UUID movementGroupId) {
    this.movementGroupId = movementGroupId;
  }

  public void setCreatedByUserId(Long createdByUserId) {
    this.createdByUserId = createdByUserId;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public void markCompleted() {
    this.status = TransactionStatus.COMPLETED;
    this.completedAt = Instant.now();
    this.failureReason = null;
  }

  public void markFailed(String reason) {
    this.status = TransactionStatus.FAILED;
    this.completedAt = Instant.now();
    this.failureReason = reason;
  }
}
