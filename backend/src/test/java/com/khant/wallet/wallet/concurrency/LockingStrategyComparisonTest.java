package com.khant.wallet.wallet.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Lab 1 evidence: same withdraw workload under three strategies.
 *
 * <p>Uses JDBC so strategies stay comparable without Spring/JPA lock mode noise.
 * Production Vinter Ledger path mirrors {@link Strategy#PESSIMISTIC_FOR_UPDATE}.
 */
@SpringBootTest
@ActiveProfiles("test")
class LockingStrategyComparisonTest {

  private static final Logger log = LoggerFactory.getLogger(LockingStrategyComparisonTest.class);

  private static final int WORKERS = 100;
  private static final long STARTING_BALANCE = 5_000L;
  private static final long WITHDRAW_AMOUNT = 100L;
  private static final int EXPECTED_SUCCESSES = (int) (STARTING_BALANCE / WITHDRAW_AMOUNT);

  @Autowired
  private DataSource dataSource;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private Long userId;
  private Long walletId;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("TRUNCATE TABLE ledger_entries, transactions, risk_events, wallets, users RESTART IDENTITY CASCADE");

    String hash = passwordEncoder.encode("password-123");
    userId = jdbcTemplate.queryForObject(
        "INSERT INTO users (email, password_hash) VALUES (?, ?) RETURNING id",
        Long.class,
        "cmp-" + UUID.randomUUID() + "@vinterledger.test",
        hash
    );
  }

  @Test
  void comparePessimisticOptimisticAndNaiveWithdrawStrategies() throws Exception {
    Map<Strategy, StrategyResult> results = new EnumMap<>(Strategy.class);

    for (Strategy strategy : Strategy.values()) {
      walletId = createWallet(STARTING_BALANCE);
      StrategyResult result = runStrategy(strategy);
      results.put(strategy, result);
      log.info(
          "Lab1 strategy={} workers={} successes={} aborts={} retries={} violations={} elapsedMs={} finalBalance={}",
          strategy,
          WORKERS,
          result.successes(),
          result.aborts(),
          result.retries(),
          result.violations(),
          result.elapsedMillis(),
          result.finalBalance()
      );
    }

    StrategyResult pessimistic = results.get(Strategy.PESSIMISTIC_FOR_UPDATE);
    StrategyResult optimistic = results.get(Strategy.OPTIMISTIC_VERSION);
    StrategyResult naive = results.get(Strategy.NAIVE_LOST_UPDATE);

    assertThat(pessimistic.violations()).as("pessimistic must keep money safe").isZero();
    assertThat(pessimistic.successes()).isEqualTo(EXPECTED_SUCCESSES);
    assertThat(pessimistic.finalBalance()).isZero();

    assertThat(optimistic.violations()).as("optimistic+retry must keep money safe").isZero();
    assertThat(optimistic.successes()).isEqualTo(EXPECTED_SUCCESSES);
    assertThat(optimistic.finalBalance()).isZero();
    assertThat(optimistic.retries()).as("hot wallet should force optimistic retries").isGreaterThan(0);

    assertThat(naive.violations())
        .as("naive read-modify-write should lose updates under contention")
        .isGreaterThan(0);
    // Classic lost update: almost every worker "succeeds", but the last writer wins,
    // so final balance stays far above zero while successes exceed affordable withdraws.
    assertThat(naive.successes()).isGreaterThan(EXPECTED_SUCCESSES);
    assertThat(naive.finalBalance()).isGreaterThan(0L);

    // Print a compact markdown table for copying into lab-01-results.md
    StringBuilder table = new StringBuilder();
    table.append("| Strategy | Workers | Successes | Aborts | Retries | Violations | Final balance | Elapsed ms |\n");
    table.append("|----------|--------:|----------:|-------:|--------:|-----------:|--------------:|-----------:|\n");
    for (Strategy strategy : Strategy.values()) {
      StrategyResult r = results.get(strategy);
      table.append(String.format(
          Locale.ROOT,
          "| %s | %d | %d | %d | %d | %d | %d | %d |%n",
          strategy.name(),
          WORKERS,
          r.successes(),
          r.aborts(),
          r.retries(),
          r.violations(),
          r.finalBalance(),
          r.elapsedMillis()
      ));
    }
    log.info("Lab1 comparison table:\n{}", table);
  }

  private StrategyResult runStrategy(Strategy strategy) throws Exception {
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger aborts = new AtomicInteger();
    AtomicInteger retries = new AtomicInteger();
    AtomicInteger violations = new AtomicInteger();
    AtomicLong totalElapsedNanos = new AtomicLong();

    ExecutorService pool = Executors.newFixedThreadPool(WORKERS);
    CountDownLatch ready = new CountDownLatch(WORKERS);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>(WORKERS);

    long wallStart = System.nanoTime();
    try {
      for (int i = 0; i < WORKERS; i++) {
        futures.add(pool.submit(() -> {
          ready.countDown();
          start.await();
          long opStart = System.nanoTime();
          try {
            Outcome outcome = switch (strategy) {
              case PESSIMISTIC_FOR_UPDATE -> withdrawPessimistic();
              case OPTIMISTIC_VERSION -> withdrawOptimistic(retries);
              case NAIVE_LOST_UPDATE -> withdrawNaive();
            };
            if (outcome == Outcome.SUCCESS) {
              successes.incrementAndGet();
            } else {
              aborts.incrementAndGet();
            }
          } catch (SQLException ex) {
            // CHECK (balance >= 0) or other integrity failures count as caught corruption attempts.
            if (isBalanceCheckViolation(ex)) {
              violations.incrementAndGet();
              aborts.incrementAndGet();
            } else {
              throw ex;
            }
          } finally {
            totalElapsedNanos.addAndGet(System.nanoTime() - opStart);
          }
          return null;
        }));
      }

      assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      for (Future<?> future : futures) {
        future.get(120, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - wallStart);
    long finalBalance = jdbcTemplate.queryForObject(
        "SELECT balance FROM wallets WHERE id = ?",
        Long.class,
        walletId
    );

    int measuredViolations = violations.get();
    if (finalBalance < 0) {
      measuredViolations++;
    }
    long expectedRemaining = STARTING_BALANCE - ((long) successes.get() * WITHDRAW_AMOUNT);
    if (finalBalance != expectedRemaining) {
      // Lost update: more money left than accounting says, or overspend without CHECK catching it.
      measuredViolations++;
    }

    return new StrategyResult(
        successes.get(),
        aborts.get(),
        retries.get(),
        measuredViolations,
        finalBalance,
        elapsedMillis
    );
  }

  private Outcome withdrawPessimistic() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        long balance;
        try (PreparedStatement lock = connection.prepareStatement(
            "SELECT balance FROM wallets WHERE id = ? FOR UPDATE"
        )) {
          lock.setLong(1, walletId);
          try (ResultSet rs = lock.executeQuery()) {
            if (!rs.next()) {
              throw new SQLException("wallet missing");
            }
            balance = rs.getLong(1);
          }
        }

        if (balance < WITHDRAW_AMOUNT) {
          connection.rollback();
          return Outcome.ABORT_INSUFFICIENT;
        }

        try (PreparedStatement update = connection.prepareStatement(
            "UPDATE wallets SET balance = balance - ?, version = version + 1, updated_at = NOW() WHERE id = ?"
        )) {
          update.setLong(1, WITHDRAW_AMOUNT);
          update.setLong(2, walletId);
          update.executeUpdate();
        }
        connection.commit();
        return Outcome.SUCCESS;
      } catch (SQLException ex) {
        connection.rollback();
        throw ex;
      }
    }
  }

  private Outcome withdrawOptimistic(AtomicInteger retries) throws SQLException {
    final int maxAttempts = 32;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try (Connection connection = dataSource.getConnection()) {
        connection.setAutoCommit(false);
        try {
          long balance;
          long version;
          try (PreparedStatement select = connection.prepareStatement(
              "SELECT balance, version FROM wallets WHERE id = ?"
          )) {
            select.setLong(1, walletId);
            try (ResultSet rs = select.executeQuery()) {
              if (!rs.next()) {
                throw new SQLException("wallet missing");
              }
              balance = rs.getLong(1);
              version = rs.getLong(2);
            }
          }

          if (balance < WITHDRAW_AMOUNT) {
            connection.rollback();
            return Outcome.ABORT_INSUFFICIENT;
          }

          int updated;
          try (PreparedStatement update = connection.prepareStatement(
              """
              UPDATE wallets
              SET balance = balance - ?, version = version + 1, updated_at = NOW()
              WHERE id = ? AND version = ? AND balance >= ?
              """
          )) {
            update.setLong(1, WITHDRAW_AMOUNT);
            update.setLong(2, walletId);
            update.setLong(3, version);
            update.setLong(4, WITHDRAW_AMOUNT);
            updated = update.executeUpdate();
          }

          if (updated == 1) {
            connection.commit();
            return Outcome.SUCCESS;
          }

          connection.rollback();
          retries.incrementAndGet();
        } catch (SQLException ex) {
          connection.rollback();
          throw ex;
        }
      }
    }
    return Outcome.ABORT_INSUFFICIENT;
  }

  private Outcome withdrawNaive() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        long balance;
        try (PreparedStatement select = connection.prepareStatement(
            "SELECT balance FROM wallets WHERE id = ?"
        )) {
          select.setLong(1, walletId);
          try (ResultSet rs = select.executeQuery()) {
            if (!rs.next()) {
              throw new SQLException("wallet missing");
            }
            balance = rs.getLong(1);
          }
        }

        // Widen the race window so lost updates are observable under 100 workers.
        try {
          Thread.sleep(2L);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new SQLException("interrupted", interrupted);
        }

        if (balance < WITHDRAW_AMOUNT) {
          connection.rollback();
          return Outcome.ABORT_INSUFFICIENT;
        }

        long newBalance = balance - WITHDRAW_AMOUNT;
        try (PreparedStatement update = connection.prepareStatement(
            "UPDATE wallets SET balance = ?, version = version + 1, updated_at = NOW() WHERE id = ?"
        )) {
          update.setLong(1, newBalance);
          update.setLong(2, walletId);
          update.executeUpdate();
        }
        connection.commit();
        return Outcome.SUCCESS;
      } catch (SQLException ex) {
        connection.rollback();
        throw ex;
      }
    }
  }

  private Long createWallet(long balance) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO wallets (user_id, name, balance, version)
        VALUES (?, ?, ?, 0)
        RETURNING id
        """,
        Long.class,
        userId,
        "cmp-" + UUID.randomUUID(),
        balance
    );
  }

  private static boolean isBalanceCheckViolation(SQLException ex) {
    SQLException current = ex;
    while (current != null) {
      String message = current.getMessage();
      if (message != null && message.toLowerCase(Locale.ROOT).contains("wallets_balance_non_negative")) {
        return true;
      }
      current = current.getNextException();
    }
    Throwable cause = ex.getCause();
    return cause instanceof SQLException sql && isBalanceCheckViolation(sql);
  }

  private enum Strategy {
    PESSIMISTIC_FOR_UPDATE,
    OPTIMISTIC_VERSION,
    NAIVE_LOST_UPDATE
  }

  private enum Outcome {
    SUCCESS,
    ABORT_INSUFFICIENT
  }

  private record StrategyResult(
      int successes,
      int aborts,
      int retries,
      int violations,
      long finalBalance,
      long elapsedMillis
  ) {
  }
}
