package com.khant.wallet.dto;

import java.math.BigDecimal;

public record WalletResponse(
    Long id,
    String name,
    BigDecimal balance
) {
}
