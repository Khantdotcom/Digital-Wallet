package com.khant.wallet.dto;

public record AuthResponse(String accessToken, String tokenType, Long userId, String email) {}
