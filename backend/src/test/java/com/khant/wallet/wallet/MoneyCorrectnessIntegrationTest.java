package com.khant.wallet.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khant.wallet.domain.TransactionStatus;
import com.khant.wallet.domain.TransactionType;
import com.khant.wallet.domain.User;
import com.khant.wallet.domain.Wallet;
import com.khant.wallet.domain.WalletTransaction;
import com.khant.wallet.dto.CreateWalletRequest;
import com.khant.wallet.dto.MoneyRequest;
import com.khant.wallet.dto.TransferRequest;
import com.khant.wallet.exception.InsufficientFundsException;
import com.khant.wallet.repository.UserRepository;
import com.khant.wallet.repository.WalletRepository;
import com.khant.wallet.repository.WalletTransactionRepository;
import com.khant.wallet.service.WalletService;
import com.khant.wallet.wallet.ledger.LedgerAccountKind;
import com.khant.wallet.wallet.ledger.LedgerDirection;
import com.khant.wallet.wallet.ledger.LedgerEntry;
import com.khant.wallet.wallet.ledger.LedgerEntryRepository;
import com.khant.wallet.wallet.ledger.LedgerReconciliationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 01 exit proofs against real PostgreSQL + Flyway schema (integer minor units).
 */
@SpringBootTest
@ActiveProfiles("test")
class MoneyCorrectnessIntegrationTest {

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
  private LedgerEntryRepository ledgerEntryRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Long userId;
  private Wallet travel;
  private Wallet savings;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("TRUNCATE TABLE ledger_entries, transactions, risk_events, wallets, users RESTART IDENTITY CASCADE");

    User user = new User();
    user.setEmail("phase01-" + UUID.randomUUID() + "@vinterledger.test");
    user.setPasswordHash(passwordEncoder.encode("password-123"));
    userId = userRepository.save(user).getId();

    travel = walletService.createWallet(userId, new CreateWalletRequest("Travel"));
    savings = walletService.createWallet(userId, new CreateWalletRequest("Savings"));
  }

  @Test
  void completedMovements_shouldKeepGlobalDebitsEqualToCredits() {
    // 100.00 + 25.50 + 40.00 major → cents
    walletService.deposit(userId, travel.getId(), new MoneyRequest(10_000L, "top-up"));
    walletService.withdraw(userId, travel.getId(), new MoneyRequest(2_550L, "taxi"));
    walletService.transfer(
        userId,
        new TransferRequest(travel.getId(), savings.getId(), 4_000L, "split")
    );

    LedgerReconciliationService.ReconciliationReport report = reconciliationService.reconcile();

    assertThat(report.totalDebits()).isEqualTo(report.totalCredits());
    assertThat(report.isFullyReconciled()).isTrue();
    // deposit 10000 + withdraw 2550 + transfer 4000 = 16550 cents of debit (and credit)
    assertThat(report.totalDebits()).isEqualTo(16_550L);
  }

  @Test
  void walletBalances_shouldNeverGoNegative_andMatchLedger() {
    walletService.deposit(userId, travel.getId(), new MoneyRequest(5_000L, "load"));

    assertThatThrownBy(() ->
        walletService.withdraw(userId, travel.getId(), new MoneyRequest(5_001L, "overdraw"))
    ).isInstanceOf(InsufficientFundsException.class);

    Wallet refreshed = walletRepository.findById(travel.getId()).orElseThrow();
    assertThat(refreshed.getBalance()).isEqualTo(5_000L);
    assertThat(refreshed.getBalance()).isGreaterThanOrEqualTo(0L);

    assertThat(reconciliationService.reconcile().isFullyReconciled()).isTrue();
  }

  @Test
  void completedMovements_mustLeaveDurableLedgerRows_andNoLostTransactions() {
    walletService.deposit(userId, travel.getId(), new MoneyRequest(8_000L, "load"));
    walletService.transfer(
        userId,
        new TransferRequest(travel.getId(), savings.getId(), 3_000L, "move")
    );

    List<WalletTransaction> completed = walletTransactionRepository.findAll().stream()
        .filter(tx -> tx.getStatus() == TransactionStatus.COMPLETED)
        .toList();
    assertThat(completed).hasSize(3);

    for (WalletTransaction tx : completed) {
      long legs = ledgerEntryRepository.countByMovementGroupId(tx.getMovementGroupId());
      assertThat(legs)
          .as("completed movement %s must have durable ledger legs", tx.getId())
          .isGreaterThanOrEqualTo(2);
    }

    assertThat(ledgerEntryRepository.findAll()).hasSize(4);
  }

  @Test
  void insufficientWithdraw_shouldEndPendingAsFailed_withoutLedgerOrBalanceChange() {
    walletService.deposit(userId, travel.getId(), new MoneyRequest(1_000L, "seed"));

    assertThatThrownBy(() ->
        walletService.withdraw(userId, travel.getId(), new MoneyRequest(9_900L, "fail"))
    ).isInstanceOf(InsufficientFundsException.class);

    List<WalletTransaction> failed = walletTransactionRepository.findAll().stream()
        .filter(tx -> tx.getType() == TransactionType.WITHDRAW)
        .toList();

    assertThat(failed).hasSize(1);
    assertThat(failed.get(0).getStatus()).isEqualTo(TransactionStatus.FAILED);
    assertThat(failed.get(0).getFailureReason()).contains("Insufficient");
    assertThat(ledgerEntryRepository.countByMovementGroupId(failed.get(0).getMovementGroupId())).isZero();
    assertThat(walletRepository.findById(travel.getId()).orElseThrow().getBalance()).isEqualTo(1_000L);
  }

  @Test
  void completedTransactions_andLedgerEntries_areImmutableInDatabase() {
    walletService.deposit(userId, travel.getId(), new MoneyRequest(1_234L, "immutable"));

    WalletTransaction completed = walletTransactionRepository.findAll().stream()
        .filter(tx -> tx.getStatus() == TransactionStatus.COMPLETED)
        .findFirst()
        .orElseThrow();
    LedgerEntry entry = ledgerEntryRepository.findAll().get(0);

    assertThatThrownBy(() ->
        jdbcTemplate.update("UPDATE transactions SET amount = 1 WHERE id = ?", completed.getId())
    ).hasMessageContaining("immutable");

    assertThatThrownBy(() ->
        jdbcTemplate.update("UPDATE ledger_entries SET amount = 1 WHERE id = ?", entry.getId())
    ).hasMessageContaining("immutable");

    assertThatThrownBy(() ->
        jdbcTemplate.update("DELETE FROM ledger_entries WHERE id = ?", entry.getId())
    ).hasMessageContaining("immutable");
  }

  @Test
  void schema_rejectsNegativeBalanceAndNonPositiveAmounts() {
    Long walletId = travel.getId();

    assertThatThrownBy(() ->
        jdbcTemplate.update("UPDATE wallets SET balance = -1 WHERE id = ?", walletId)
    ).hasMessageContaining("wallets_balance_non_negative");

    assertThatThrownBy(() ->
        jdbcTemplate.update(
            """
            INSERT INTO transactions (wallet_id, type, amount, status, movement_group_id, created_by_user_id)
            VALUES (?, 'DEPOSIT', 0, 'PENDING', ?, ?)
            """,
            walletId,
            UUID.randomUUID(),
            userId
        )
    ).hasMessageContaining("transactions_amount_positive");
  }

  @Test
  void moneyColumns_useBigintMinorUnitsNotNumericOrFloat() {
    List<Map<String, Object>> walletCols = jdbcTemplate.queryForList(
        """
        SELECT data_type
        FROM information_schema.columns
        WHERE table_name = 'wallets' AND column_name = 'balance'
        """
    );
    List<Map<String, Object>> ledgerCols = jdbcTemplate.queryForList(
        """
        SELECT data_type
        FROM information_schema.columns
        WHERE table_name = 'ledger_entries' AND column_name = 'amount'
        """
    );

    assertThat(walletCols).hasSize(1);
    assertThat(walletCols.get(0).get("data_type")).isEqualTo("bigint");

    assertThat(ledgerCols).hasSize(1);
    assertThat(ledgerCols.get(0).get("data_type")).isEqualTo("bigint");
  }

  @Test
  void transfer_isTwoWalletLegsThatConserveMoney() {
    walletService.deposit(userId, travel.getId(), new MoneyRequest(10_000L, "seed"));
    walletService.transfer(
        userId,
        new TransferRequest(travel.getId(), savings.getId(), 3_500L, "pair")
    );

    UUID groupId = walletTransactionRepository.findAll().stream()
        .filter(tx -> tx.getType() == TransactionType.TRANSFER_OUT)
        .findFirst()
        .orElseThrow()
        .getMovementGroupId();

    List<LedgerEntry> transferLegs = ledgerEntryRepository.findAll().stream()
        .filter(e -> e.getMovementGroupId().equals(groupId))
        .toList();

    assertThat(transferLegs).hasSize(2);
    assertThat(transferLegs).allMatch(e -> e.getAccountKind() == LedgerAccountKind.WALLET);

    long debits = transferLegs.stream()
        .filter(e -> e.getDirection() == LedgerDirection.DEBIT)
        .mapToLong(LedgerEntry::getAmount)
        .sum();
    long credits = transferLegs.stream()
        .filter(e -> e.getDirection() == LedgerDirection.CREDIT)
        .mapToLong(LedgerEntry::getAmount)
        .sum();

    assertThat(debits).isEqualTo(credits);
    assertThat(walletRepository.findById(travel.getId()).orElseThrow().getBalance()).isEqualTo(6_500L);
    assertThat(walletRepository.findById(savings.getId()).orElseThrow().getBalance()).isEqualTo(3_500L);
  }

  @Test
  void compensatingEntry_wouldBeANewOppositeMovement_notAnUpdate() {
    walletService.deposit(userId, travel.getId(), new MoneyRequest(2_000L, "mistaken"));
    walletService.withdraw(userId, travel.getId(), new MoneyRequest(2_000L, "compensate mistaken deposit"));

    assertThat(walletRepository.findById(travel.getId()).orElseThrow().getBalance()).isEqualTo(0L);
    assertThat(reconciliationService.reconcile().isFullyReconciled()).isTrue();
    assertThat(walletTransactionRepository.findAll()).allMatch(tx ->
        tx.getStatus() == TransactionStatus.COMPLETED
    );
    assertThat(ledgerEntryRepository.findAll()).hasSize(4);
  }
}
