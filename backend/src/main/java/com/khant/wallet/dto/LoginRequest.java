package com.khant.wallet.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank @Email(message = "must be a valid email") String email,
    @NotBlank String password
) {}
