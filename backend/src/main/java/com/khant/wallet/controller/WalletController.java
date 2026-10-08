package com.khant.wallet.controller;

import com.khant.wallet.domain.Wallet;
import com.khant.wallet.dto.CreateWalletRequest;
import com.khant.wallet.dto.MoneyRequest;
import com.khant.wallet.dto.PageResponse;
import com.khant.wallet.dto.TransactionHistoryItemResponse;
import com.khant.wallet.dto.TransferRequest;
import com.khant.wallet.dto.WalletResponse;
import com.khant.wallet.service.WalletService;
import com.khant.wallet.wallet.concurrency.WalletMoneyCommands;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wallets")
public class WalletController {

  private final WalletService walletService;
  private final WalletMoneyCommands walletMoneyCommands;

  public WalletController(WalletService walletService, WalletMoneyCommands walletMoneyCommands) {
    this.walletService = walletService;
    this.walletMoneyCommands = walletMoneyCommands;
  }

  @PostMapping
  public WalletResponse createWallet(Authentication authentication, @Valid @RequestBody CreateWalletRequest request) {
    Long userId = (Long) authentication.getPrincipal();
    return map(walletService.createWallet(userId, request));
  }

  @GetMapping
  public List<WalletResponse> listWallets(Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return walletService.listWallets(userId).stream().map(this::map).toList();
  }

  @GetMapping("/{walletId}/transactions")
  public PageResponse<TransactionHistoryItemResponse> getWalletTransactions(
      Authentication authentication,
      @PathVariable Long walletId,
      @PageableDefault(size = 20) Pageable pageable
  ) {
    Long userId = (Long) authentication.getPrincipal();
    return walletService.getTransactionHistory(userId, walletId, pageable);
  }

  @PostMapping("/{walletId}/deposit")
  public WalletResponse deposit(
      Authentication authentication,
      @PathVariable Long walletId,
      @Valid @RequestBody MoneyRequest request
  ) {
    Long userId = (Long) authentication.getPrincipal();
    return map(walletMoneyCommands.deposit(userId, walletId, request));
  }

  @PostMapping("/{walletId}/withdraw")
  public WalletResponse withdraw(
      Authentication authentication,
      @PathVariable Long walletId,
      @Valid @RequestBody MoneyRequest request
  ) {
    Long userId = (Long) authentication.getPrincipal();
    return map(walletMoneyCommands.withdraw(userId, walletId, request));
  }

  @PostMapping("/transfer")
  public List<WalletResponse> transfer(Authentication authentication, @Valid @RequestBody TransferRequest request) {
    Long userId = (Long) authentication.getPrincipal();
    return walletMoneyCommands.transfer(userId, request).stream().map(this::map).toList();
  }

  private WalletResponse map(Wallet wallet) {
    return new WalletResponse(wallet.getId(), wallet.getName(), wallet.getBalance());
  }
}
