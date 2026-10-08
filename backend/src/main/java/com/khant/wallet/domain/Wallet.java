package com.khant.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "wallets")
public class Wallet {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 120)
  private String name;

  /** Cached balance in integer minor units (cents). */
  @Column(nullable = false)
  private long balance = 0L;

  /**
   * Optimistic concurrency token. Production mutations still take
   * {@code SELECT … FOR UPDATE}; this increments on every balance change so
   * Lab 1 can compare optimistic retries against pessimistic waits.
   */
  @Column(nullable = false)
  private long version = 0L;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @PreUpdate
  public void onUpdate() {
    updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public String getName() {
    return name;
  }

  public long getBalance() {
    return balance;
  }

  public long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setBalance(long balance) {
    this.balance = balance;
  }

  public void setVersion(long version) {
    this.version = version;
  }

  /** Apply a balance mutation and bump the optimistic version token. */
  public void applyBalanceDelta(long deltaMinorUnits) {
    this.balance = this.balance + deltaMinorUnits;
    this.version = this.version + 1L;
  }
}
