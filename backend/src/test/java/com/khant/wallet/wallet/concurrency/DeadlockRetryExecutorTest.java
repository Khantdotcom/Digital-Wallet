package com.khant.wallet.wallet.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;

class DeadlockRetryExecutorTest {

  @Test
  void retriesTransientDeadlockThenSucceeds() {
    DeadlockRetryExecutor executor = new DeadlockRetryExecutor(5);
    AtomicInteger attempts = new AtomicInteger();

    String result = executor.execute(() -> {
      if (attempts.incrementAndGet() < 3) {
        throw new DeadlockLoserDataAccessException("simulated deadlock", null);
      }
      return "ok";
    });

    assertThat(result).isEqualTo("ok");
    assertThat(attempts.get()).isEqualTo(3);
  }

  @Test
  void givesUpAfterMaxAttempts() {
    DeadlockRetryExecutor executor = new DeadlockRetryExecutor(3);
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(() -> executor.execute(() -> {
      attempts.incrementAndGet();
      throw new CannotAcquireLockException("still locked");
    })).isInstanceOf(CannotAcquireLockException.class);

    assertThat(attempts.get()).isEqualTo(3);
  }

  @Test
  void doesNotRetryBusinessErrors() {
    DeadlockRetryExecutor executor = new DeadlockRetryExecutor(5);
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(() -> executor.execute(() -> {
      attempts.incrementAndGet();
      throw new IllegalArgumentException("bad input");
    })).isInstanceOf(IllegalArgumentException.class);

    assertThat(attempts.get()).isEqualTo(1);
  }
}
