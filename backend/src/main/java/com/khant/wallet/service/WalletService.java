package com.khant.wallet.service;

import com.khant.wallet.domain.TransactionStatus;
import com.khant.wallet.domain.TransactionType;
import com.khant.wallet.domain.User;
import com.khant.wallet.domain.Wallet;
import com.khant.wallet.domain.WalletTransaction;
import com.khant.wallet.dto.CreateWalletRequest;
import com.khant.wallet.dto.MoneyRequest;
import com.khant.wallet.dto.PageResponse;
import com.khant.wallet.dto.TransactionHistoryItemResponse;
import com.khant.wallet.dto.TransferRequest;
import com.khant.wallet.exception.InsufficientFundsException;
import com.khant.wallet.exception.WalletNotFoundException;
import com.khant.wallet.repository.UserRepository;
import com.khant.wallet.repository.WalletRepository;
import com.khant.wallet.repository.WalletTransactionRepository;
import com.khant.wallet.risk.RiskService;
import com.khant.wallet.risk.WalletOperation;
import com.khant.wallet.wallet.ledger.LedgerPostingService;
import com.khant.wallet.wallet.money.MoneyAmounts;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {

  private final WalletRepository walletRepository;
  private final WalletTransactionRepository walletTransactionRepository;
  private final UserRepository userRepository;
  private final RiskService riskService;
  private final LedgerPostingService ledgerPostingService;

  public WalletService(
      WalletRepository walletRepository,
      WalletTransactionRepository walletTransactionRepository,
      UserRepository userRepository,
      RiskService riskService,
      LedgerPostingService ledgerPostingService
  ) {
    this.walletRepository = walletRepository;
    this.walletTransactionRepository = walletTransactionRepository;
    this.userRepository = userRepository;
    this.riskService = riskService;
    this.ledgerPostingService = ledgerPostingService;
  }

  @Transactional
  public Wallet createWallet(Long userId, CreateWalletRequest request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

    Wallet wallet = new Wallet();
    wallet.setUser(user);
    wallet.setName(request.name().trim());
    wallet.setBalance(0L);

    return walletRepository.save(wallet);
  }

  @Transactional(readOnly = true)
  public List<Wallet> listWallets(Long userId) {
    return walletRepository.findByUserId(userId);
  }

  @Transactional
  public Wallet deposit(Long userId, Long walletId, MoneyRequest request) {
    long amount = MoneyAmounts.requirePositiveMinorUnits(request.amount());
    Wallet wallet = walletRepository.findByIdAndUserIdForUpdate(walletId, userId)
        .orElseThrow(() -> new WalletNotFoundException(walletId));

    riskService.assessAndRecord(userId, walletId, WalletOperation.DEPOSIT, amount);

    WalletTransaction tx = newPendingMovement(
        wallet,
        null,
        TransactionType.DEPOSIT,
        amount,
        request.note(),
        userId,
        UUID.randomUUID()
    );
    walletTransactionRepository.save(tx);

    wallet.setBalance(wallet.getBalance() + amount);
    ledgerPostingService.postDeposit(tx, wallet, amount);
    tx.markCompleted();

    return wallet;
  }

  /**
   * Insufficient-funds failures keep their FAILED movement row for audit.
   * Without {@code noRollbackFor}, the exception would erase the PENDING→FAILED trail.
   */
  @Transactional(noRollbackFor = InsufficientFundsException.class)
  public Wallet withdraw(Long userId, Long walletId, MoneyRequest request) {
    long amount = MoneyAmounts.requirePositiveMinorUnits(request.amount());
    Wallet wallet = walletRepository.findByIdAndUserIdForUpdate(walletId, userId)
        .orElseThrow(() -> new WalletNotFoundException(walletId));

    riskService.assessAndRecord(userId, walletId, WalletOperation.WITHDRAW, amount);

    WalletTransaction tx = newPendingMovement(
        wallet,
        null,
        TransactionType.WITHDRAW,
        amount,
        request.note(),
        userId,
        UUID.randomUUID()
    );
    walletTransactionRepository.save(tx);

    if (wallet.getBalance() < amount) {
      tx.markFailed("Insufficient funds");
      throw new InsufficientFundsException(walletId);
    }

    wallet.setBalance(wallet.getBalance() - amount);
    ledgerPostingService.postWithdraw(tx, wallet, amount);
    tx.markCompleted();

    return wallet;
  }

  @Transactional(noRollbackFor = InsufficientFundsException.class)
  public List<Wallet> transfer(Long userId, TransferRequest request) {
    if (request.sourceWalletId().equals(request.targetWalletId())) {
      throw new IllegalArgumentException("sourceWalletId and targetWalletId must differ");
    }

    long amount = MoneyAmounts.requirePositiveMinorUnits(request.amount());

    List<Long> orderedIds = request.sourceWalletId() < request.targetWalletId()
        ? List.of(request.sourceWalletId(), request.targetWalletId())
        : List.of(request.targetWalletId(), request.sourceWalletId());

    List<Wallet> lockedWallets = walletRepository.findAllByIdInForUpdateOrdered(orderedIds);
    if (lockedWallets.size() != 2) {
      throw new WalletNotFoundException(request.sourceWalletId());
    }

    Wallet source = lockedWallets.get(0).getId().equals(request.sourceWalletId())
        ? lockedWallets.get(0)
        : lockedWallets.get(1);
    Wallet target = source == lockedWallets.get(0) ? lockedWallets.get(1) : lockedWallets.get(0);

    if (!source.getUser().getId().equals(userId) || !target.getUser().getId().equals(userId)) {
      throw new WalletNotFoundException(request.sourceWalletId());
    }

    riskService.assessAndRecord(userId, source.getId(), WalletOperation.TRANSFER, amount);

    UUID movementGroupId = UUID.randomUUID();

    WalletTransaction outTx = newPendingMovement(
        source,
        target,
        TransactionType.TRANSFER_OUT,
        amount,
        request.note(),
        userId,
        movementGroupId
    );
    WalletTransaction inTx = newPendingMovement(
        target,
        source,
        TransactionType.TRANSFER_IN,
        amount,
        request.note(),
        userId,
        movementGroupId
    );
    walletTransactionRepository.save(outTx);
    walletTransactionRepository.save(inTx);

    if (source.getBalance() < amount) {
      outTx.markFailed("Insufficient funds");
      inTx.markFailed("Insufficient funds");
      throw new InsufficientFundsException(source.getId());
    }

    source.setBalance(source.getBalance() - amount);
    target.setBalance(target.getBalance() + amount);
    ledgerPostingService.postTransfer(outTx, inTx, source, target, amount);
    outTx.markCompleted();
    inTx.markCompleted();

    return List.of(source, target);
  }

  @Transactional(readOnly = true)
  public PageResponse<TransactionHistoryItemResponse> getTransactionHistory(Long userId, Long walletId, Pageable pageable) {
    walletRepository.findByIdAndUserId(walletId, userId).orElseThrow(() -> new WalletNotFoundException(walletId));

    Page<TransactionHistoryItemResponse> page = walletTransactionRepository
        .findByWalletIdAndWalletUserId(walletId, userId, pageable)
        .map(this::toHistoryItem);

    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isLast()
    );
  }

  private WalletTransaction newPendingMovement(
      Wallet wallet,
      Wallet relatedWallet,
      TransactionType type,
      long amount,
      String note,
      Long userId,
      UUID movementGroupId
  ) {
    WalletTransaction tx = new WalletTransaction();
    tx.setWallet(wallet);
    tx.setRelatedWallet(relatedWallet);
    tx.setType(type);
    tx.setAmount(amount);
    tx.setNote(note);
    tx.setCreatedByUserId(userId);
    tx.setMovementGroupId(movementGroupId);
    tx.setStatus(TransactionStatus.PENDING);
    return tx;
  }

  private TransactionHistoryItemResponse toHistoryItem(WalletTransaction transaction) {
    Long relatedWalletId = transaction.getRelatedWallet() == null ? null : transaction.getRelatedWallet().getId();
    return new TransactionHistoryItemResponse(
        transaction.getId(),
        transaction.getWallet().getId(),
        relatedWalletId,
        transaction.getType(),
        transaction.getStatus(),
        transaction.getAmount(),
        transaction.getNote(),
        transaction.getCreatedAt(),
        transaction.getCompletedAt(),
        transaction.getMovementGroupId(),
        transaction.getFailureReason()
    );
  }
}
