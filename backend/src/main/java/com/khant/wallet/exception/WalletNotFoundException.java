package com.khant.wallet.exception;

public class WalletNotFoundException extends RuntimeException {

  public WalletNotFoundException(Long walletId) {
    super("Wallet not found: " + walletId);
  }
}
