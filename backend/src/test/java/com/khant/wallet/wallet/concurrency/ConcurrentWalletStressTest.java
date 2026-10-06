package com.khant.wallet.wallet.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.khant.wallet.domain.TransactionStatus;
import com.khant.wallet.domain.User;
import com.khant.wallet.domain.Wallet;
import com.khant.wallet.dto.CreateWalletRequest;
import com.khant.wallet.dto.MoneyRequest;
import com.khant.wallet.dto.TransferRequest;
import com.khant.wallet.exception.InsufficientFundsException;
import com.khant.wallet.repository.UserRepository;
import com.khant.wallet.repository.WalletRepository;
import com.khant.wallet.repository.WalletTransactionRepository;
import com.khant.wallet.service.WalletService;
import com.khant.wallet.wallet.ledger.LedgerReconciliationService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Lab 1 / Phase 02: ≥100 workers smash withdraw/transfer; money invariants must hold.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentWalletStressTest {

  private static final int WORKERS = 100;

  @Autowired
  private WalletMoneyCommands walletMoneyCommands;

  @Autowired
  private WalletService walletService;

  @Autowired
  private LedgerReconciliationService reconciliationService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private WalletRepository walletRepository;

  @Autowired
  private WalletTransactionRepository walletTransactionRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Long userId;
  private Wallet hot;
  private Wallet cold;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("TRUNCATE TABLE ledger_entries, transactions, risk_events, wallets, users RESTART IDENTITY CASCADE");

    User user = new User();
    user.setEmail("lab01-" + UUID.randomUUID() + "@vinterledger.test");
    user.setPasswordHash(passwordEncoder.encode("password-123"));
    userId = userRepository.save(user).getId();

    hot = walletService.createWallet(userId, new CreateWalletRequest("Hot"));
    cold = walletService.createWallet(userId, new CreateWalletRequest("Cold"));
  }

  @Test
  void concurrentWithdraws_fromUnderfundedWallet_neverOverdraftOrBreakLedger() throws Exception {
    // 50.00 major → 5000 cents; 100 workers each try to withdraw 100 cents.
    long starting = 5_000L;
    long perWithdraw = 100L;
    walletMoneyCommands.deposit(userId, hot.getId(), new MoneyRequest(starting, "seed"));

    AtomicInteger successes = new AtomicInteger();
    AtomicInteger insufficient = new AtomicInteger();
    AtomicInteger otherFailures = new AtomicInteger();
    List<String> unexpected = Collections.synchronizedList(new ArrayList<>());

    runWorkers(WORKERS, () -> {
      try {
        walletMoneyCommands.withdraw(userId, hot.getId(), new MoneyRequest(perWithdraw, "race"));
        successes.incrementAndGet();
      } catch (InsufficientFundsException expected) {
        insufficient.incrementAndGet();
      } catch (RuntimeException ex) {
        otherFailures.incrementAndGet();
        unexpected.add(ex.getClass().getSimpleName() + ": " + ex.getMessage());
      }
    });

    Wallet refreshed = walletRepository.findById(hot.getId()).orElseThrow();
    long completedWithdraws = walletTransactionRepository.findAll().stream()
        .filter(tx -> tx.getType().name().equals("WITHDRAW") && tx.getStatus() == TransactionStatus.COMPLETED)
        .count();

    assertThat(unexpected).as("unexpected failures: %s", unexpected).isEmpty();
    assertThat(otherFailures.get()).isZero();
    assertThat(successes.get()).isEqualTo((int) (starting / perWithdraw));
    assertThat(successes.get() + insufficient.get()).isEqualTo(WORKERS);
    assertThat(completedWithdraws).isEqualTo(successes.get());
    assertThat(refreshed.getBalance()).isZero();
    assertThat(refreshed.getBalance()).isGreaterThanOrEqualTo(0L);
    assertThat(reconciliationService.reconcile().isFullyReconciled()).isTrue();
  }

  @Test
  void concurrentTransfers_fromOneHotWallet_conserveMoneyAndNeverGoNegative() throws Exception {
    long starting = 10_000L;
    long perTransfer = 100L;
    walletMoneyCommands.deposit(userId, hot.getId(), new MoneyRequest(starting, "seed"));

    AtomicInteger successes = new AtomicInteger();
    AtomicInteger insufficient = new AtomicInteger();

    runWorkers(WORKERS, () -> {
      try {
        walletMoneyCommands.transfer(
            userId,
            new TransferRequest(hot.getId(), cold.getId(), perTransfer, "burst")
        );
        successes.incrementAndGet();
      } catch (InsufficientFundsException expected) {
        insufficient.incrementAndGet();
      }
    });

    Wallet hotAfter = walletRepository.findById(hot.getId()).orElseThrow();
    Wallet coldAfter = walletRepository.findById(cold.getId()).orElseThrow();

    assertThat(successes.get()).isEqualTo((int) (starting / perTransfer));
    assertThat(successes.get() + insufficient.get()).isEqualTo(WORKERS);
    assertThat(hotAfter.getBalance()).isZero();
    assertThat(coldAfter.getBalance()).isEqualTo(starting);
    assertThat(hotAfter.getBalance() + coldAfter.getBalance()).isEqualTo(starting);
    assertThat(reconciliationService.reconcile().isFullyReconciled()).isTrue();
  }

  @Test
  void crossingTransfers_AtoB_and_BtoA_completeWithoutPermanentDeadlock() throws Exception {
    long seed = 50_000L;
    walletMoneyCommands.deposit(userId, hot.getId(), new MoneyRequest(seed, "seed-a"));
    walletMoneyCommands.deposit(userId, cold.getId(), new MoneyRequest(seed, "seed-b"));

    AtomicInteger successes = new AtomicInteger();
    AtomicInteger insufficient = new AtomicInteger();
    AtomicInteger otherFailures = new AtomicInteger();
    List<String> unexpected = Collections.synchronizedList(new ArrayList<>());

    runWorkers(WORKERS, index -> {
      boolean aToB = index % 2 == 0;
      Long source = aToB ? hot.getId() : cold.getId();
      Long target = aToB ? cold.getId() : hot.getId();
      try {
        walletMoneyCommands.transfer(
            userId,
            new TransferRequest(source, target, 100L, "cross-" + index)
        );
        successes.incrementAndGet();
      } catch (InsufficientFundsException expected) {
        insufficient.incrementAndGet();
      } catch (RuntimeException ex) {
        otherFailures.incrementAndGet();
        unexpected.add(ex.getClass().getSimpleName() + ": " + ex.getMessage());
      }
    });

    Wallet hotAfter = walletRepository.findById(hot.getId()).orElseThrow();
    Wallet coldAfter = walletRepository.findById(cold.getId()).orElseThrow();

    assertThat(unexpected).as("unexpected: %s", unexpected).isEmpty();
    assertThat(otherFailures.get()).isZero();
    assertThat(successes.get() + insufficient.get()).isEqualTo(WORKERS);
    assertThat(hotAfter.getBalance()).isGreaterThanOrEqualTo(0L);
    assertThat(coldAfter.getBalance()).isGreaterThanOrEqualTo(0L);
    assertThat(hotAfter.getBalance() + coldAfter.getBalance()).isEqualTo(seed * 2);
    assertThat(reconciliationService.reconcile().isFullyReconciled()).isTrue();
  }

  private void runWorkers(int workers, Runnable task) throws Exception {
    runWorkers(workers, ignored -> task.run());
  }

  private void runWorkers(int workers, IndexedTask task) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(workers);
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>(workers);

    try {
      for (int i = 0; i < workers; i++) {
        final int index = i;
        futures.add(pool.submit(() -> {
          ready.countDown();
          start.await();
          task.run(index);
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
  }

  @FunctionalInterface
  private interface IndexedTask {
    void run(int index) throws Exception;
  }
}
