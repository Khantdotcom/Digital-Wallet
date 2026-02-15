package com.khant.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWalletRequest(
    @NotBlank(message = "name is required")
    @Size(max = 120, message = "name must be at most 120 characters")
    String name
) {
}
