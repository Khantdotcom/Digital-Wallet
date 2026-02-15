package com.khant.wallet.exception;

public class InsufficientFundsException extends RuntimeException {

  public InsufficientFundsException(Long walletId) {
    super("Insufficient funds in wallet: " + walletId);
  }
}
