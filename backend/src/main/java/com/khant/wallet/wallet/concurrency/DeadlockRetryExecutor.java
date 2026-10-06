package com.khant.wallet.wallet.concurrency;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionSystemException;

/**
 * Bounded retry for transient lock failures on the wallet money path.
 *
 * <p>Primary deadlock prevention is still deterministic lock ordering. This executor
 * is the safety net when Postgres aborts a transaction as a deadlock victim or when
 * an optimistic version conflict races a hot wallet.
 */
@Component
public class DeadlockRetryExecutor {

  private static final Logger log = LoggerFactory.getLogger(DeadlockRetryExecutor.class);

  public static final int DEFAULT_MAX_ATTEMPTS = 5;
  private static final long BASE_BACKOFF_MILLIS = 5L;

  private final int maxAttempts;

  public DeadlockRetryExecutor() {
    this(DEFAULT_MAX_ATTEMPTS);
  }

  public DeadlockRetryExecutor(int maxAttempts) {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be >= 1");
    }
    this.maxAttempts = maxAttempts;
  }

  public <T> T execute(Supplier<T> work) {
    int attempt = 1;
    while (true) {
      try {
        return work.get();
      } catch (RuntimeException ex) {
        if (!isRetryableLockFailure(ex) || attempt >= maxAttempts) {
          throw ex;
        }
        log.warn(
            "Transient lock failure on wallet money path (attempt {}/{}): {}",
            attempt,
            maxAttempts,
            rootMessage(ex)
        );
        sleepBackoff(attempt);
        attempt++;
      }
    }
  }

  public void execute(Runnable work) {
    execute(() -> {
      work.run();
      return null;
    });
  }

  static boolean isRetryableLockFailure(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof DeadlockLoserDataAccessException
          || current instanceof CannotAcquireLockException
          || current instanceof PessimisticLockingFailureException
          || current instanceof ObjectOptimisticLockingFailureException) {
        return true;
      }
      if (current instanceof TransactionSystemException) {
        // Fall through to cause inspection.
      }
      String message = current.getMessage();
      if (message != null) {
        String lower = message.toLowerCase();
        if (lower.contains("deadlock")
            || lower.contains("could not serialize access")
            || lower.contains("lock wait timeout")
            || lower.contains("40p01")
            || lower.contains("40001")) {
          return true;
        }
      }
      current = current.getCause();
    }
    return false;
  }

  private static void sleepBackoff(int attempt) {
    try {
      Thread.sleep(BASE_BACKOFF_MILLIS * attempt);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while backing off after lock failure", interrupted);
    }
  }

  private static String rootMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current.getClass().getSimpleName() + ": " + current.getMessage();
  }
}
