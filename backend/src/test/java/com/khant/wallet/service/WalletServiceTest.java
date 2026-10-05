package com.khant.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.khant.wallet.domain.TransactionStatus;
import com.khant.wallet.domain.User;
import com.khant.wallet.domain.Wallet;
import com.khant.wallet.domain.WalletTransaction;
import com.khant.wallet.dto.MoneyRequest;
import com.khant.wallet.dto.TransferRequest;
import com.khant.wallet.exception.InsufficientFundsException;
import com.khant.wallet.repository.UserRepository;
import com.khant.wallet.repository.WalletRepository;
import com.khant.wallet.repository.WalletTransactionRepository;
import com.khant.wallet.risk.RiskService;
import com.khant.wallet.risk.WalletOperation;
import com.khant.wallet.wallet.ledger.LedgerPostingService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

  @Mock
  private WalletRepository walletRepository;

  @Mock
  private WalletTransactionRepository walletTransactionRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private RiskService riskService;

  @Mock
  private LedgerPostingService ledgerPostingService;

  @InjectMocks
  private WalletService walletService;

  @Test
  void withdraw_shouldMarkFailedAndThrow_whenInsufficientFunds() {
    Long userId = 1L;
    Long walletId = 10L;
    Wallet wallet = wallet(walletId, userId, new BigDecimal("5.00"));
    MoneyRequest request = new MoneyRequest(new BigDecimal("10.00"), "atm");

    when(walletRepository.findByIdAndUserIdForUpdate(walletId, userId)).thenReturn(Optional.of(wallet));
    when(walletTransactionRepository.save(any(WalletTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

    assertThatThrownBy(() -> walletService.withdraw(userId, walletId, request))
        .isInstanceOf(InsufficientFundsException.class)
        .hasMessageContaining("Insufficient funds");

    ArgumentCaptor<WalletTransaction> txCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
    verify(walletTransactionRepository).save(txCaptor.capture());
    assertThat(txCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.FAILED);
    assertThat(wallet.getBalance()).isEqualByComparingTo("5.00");
    verify(riskService).assessAndRecord(userId, walletId, WalletOperation.WITHDRAW, request.amount());
    verify(ledgerPostingService, never()).postWithdraw(any(), any(), any());
  }

  @Test
  void transfer_shouldThrow_whenSourceAndTargetAreSame() {
    Long userId = 1L;
    TransferRequest request = new TransferRequest(20L, 20L, new BigDecimal("10.00"), "self");

    assertThatThrownBy(() -> walletService.transfer(userId, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must differ");

    verify(walletRepository, never()).findAllByIdInForUpdateOrdered(any());
    verify(walletTransactionRepository, never()).save(any());
  }

  @Test
  void transfer_shouldMoveFundsBetweenWallets_whenRequestIsValid() {
    Long userId = 1L;
    Wallet source = wallet(1L, userId, new BigDecimal("100.00"));
    Wallet target = wallet(2L, userId, new BigDecimal("25.00"));
    TransferRequest request = new TransferRequest(1L, 2L, new BigDecimal("30.00"), "rent");

    when(walletRepository.findAllByIdInForUpdateOrdered(List.of(1L, 2L))).thenReturn(List.of(source, target));
    when(walletTransactionRepository.save(any(WalletTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

    List<Wallet> result = walletService.transfer(userId, request);

    assertThat(result).containsExactly(source, target);
    assertThat(source.getBalance()).isEqualByComparingTo("70.00");
    assertThat(target.getBalance()).isEqualByComparingTo("55.00");
    verify(riskService).assessAndRecord(userId, 1L, WalletOperation.TRANSFER, request.amount());
    verify(walletTransactionRepository, times(2)).save(any());
    verify(ledgerPostingService).postTransfer(any(), any(), any(), any(), any());
  }

  @Test
  void deposit_shouldRejectMoreThanTwoDecimalPlaces() {
    Long userId = 1L;
    Long walletId = 10L;
    MoneyRequest request = new MoneyRequest(new BigDecimal("10.001"), "bad scale");

    assertThatThrownBy(() -> walletService.deposit(userId, walletId, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("decimal places");

    verify(walletRepository, never()).findByIdAndUserIdForUpdate(any(), any());
  }

  private Wallet wallet(Long walletId, Long userId, BigDecimal balance) {
    User user = new User();
    ReflectionTestUtils.setField(user, "id", userId);

    Wallet wallet = new Wallet();
    ReflectionTestUtils.setField(wallet, "id", walletId);
    wallet.setUser(user);
    wallet.setName("primary");
    wallet.setBalance(balance);

    return wallet;
  }
}
