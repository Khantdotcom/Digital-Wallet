package com.khant.wallet.service;

import com.khant.wallet.domain.TransactionType;
import com.khant.wallet.domain.User;
import com.khant.wallet.domain.Wallet;
import com.khant.wallet.domain.WalletTransaction;
import com.khant.wallet.dto.CreateWalletRequest;
import com.khant.wallet.dto.MoneyRequest;
import com.khant.wallet.dto.TransferRequest;
import com.khant.wallet.exception.InsufficientFundsException;
import com.khant.wallet.exception.WalletNotFoundException;
import com.khant.wallet.repository.UserRepository;
import com.khant.wallet.risk.RiskService;
import com.khant.wallet.risk.WalletOperation;
import com.khant.wallet.repository.WalletRepository;
import com.khant.wallet.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {

  private final WalletRepository walletRepository;
  private final WalletTransactionRepository walletTransactionRepository;
  private final UserRepository userRepository;
  private final RiskService riskService;

  public WalletService(
      WalletRepository walletRepository,
      WalletTransactionRepository walletTransactionRepository,
      UserRepository userRepository,
      RiskService riskService
  ) {
    this.walletRepository = walletRepository;
    this.walletTransactionRepository = walletTransactionRepository;
    this.userRepository = userRepository;
    this.riskService = riskService;
  }

  @Transactional
  public Wallet createWallet(Long userId, CreateWalletRequest request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

    Wallet wallet = new Wallet();
    wallet.setUser(user);
    wallet.setName(request.name().trim());
    wallet.setBalance(BigDecimal.ZERO);

    return walletRepository.save(wallet);
  }

  @Transactional(readOnly = true)
  public List<Wallet> listWallets(Long userId) {
    return walletRepository.findByUserId(userId);
  }

  @Transactional
  public Wallet deposit(Long userId, Long walletId, MoneyRequest request) {
    Wallet wallet = walletRepository.findByIdAndUserIdForUpdate(walletId, userId)
        .orElseThrow(() -> new WalletNotFoundException(walletId));

    riskService.assessAndRecord(userId, walletId, WalletOperation.DEPOSIT, request.amount());

    wallet.setBalance(wallet.getBalance().add(request.amount()));

    WalletTransaction tx = new WalletTransaction();
    tx.setWallet(wallet);
    tx.setType(TransactionType.DEPOSIT);
    tx.setAmount(request.amount());
    tx.setNote(request.note());
    walletTransactionRepository.save(tx);

    return wallet;
  }

  @Transactional
  public Wallet withdraw(Long userId, Long walletId, MoneyRequest request) {
    Wallet wallet = walletRepository.findByIdAndUserIdForUpdate(walletId, userId)
        .orElseThrow(() -> new WalletNotFoundException(walletId));

    riskService.assessAndRecord(userId, walletId, WalletOperation.WITHDRAW, request.amount());

    if (wallet.getBalance().compareTo(request.amount()) < 0) {
      throw new InsufficientFundsException(walletId);
    }

    wallet.setBalance(wallet.getBalance().subtract(request.amount()));

    WalletTransaction tx = new WalletTransaction();
    tx.setWallet(wallet);
    tx.setType(TransactionType.WITHDRAW);
    tx.setAmount(request.amount());
    tx.setNote(request.note());
    walletTransactionRepository.save(tx);

    return wallet;
  }

  @Transactional
  public List<Wallet> transfer(Long userId, TransferRequest request) {
    if (request.sourceWalletId().equals(request.targetWalletId())) {
      throw new IllegalArgumentException("sourceWalletId and targetWalletId must differ");
    }

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

    riskService.assessAndRecord(userId, source.getId(), WalletOperation.TRANSFER, request.amount());

    if (source.getBalance().compareTo(request.amount()) < 0) {
      throw new InsufficientFundsException(source.getId());
    }

    source.setBalance(source.getBalance().subtract(request.amount()));
    target.setBalance(target.getBalance().add(request.amount()));

    WalletTransaction outTx = new WalletTransaction();
    outTx.setWallet(source);
    outTx.setRelatedWallet(target);
    outTx.setType(TransactionType.TRANSFER_OUT);
    outTx.setAmount(request.amount());
    outTx.setNote(request.note());

    WalletTransaction inTx = new WalletTransaction();
    inTx.setWallet(target);
    inTx.setRelatedWallet(source);
    inTx.setType(TransactionType.TRANSFER_IN);
    inTx.setAmount(request.amount());
    inTx.setNote(request.note());

    walletTransactionRepository.save(outTx);
    walletTransactionRepository.save(inTx);

    return List.of(source, target);
  }
}
