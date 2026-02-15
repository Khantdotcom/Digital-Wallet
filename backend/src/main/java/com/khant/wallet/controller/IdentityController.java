package com.khant.wallet.controller;

import com.khant.wallet.dto.IdentityResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me")
public class IdentityController {

  @GetMapping
  public IdentityResponse me(Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return new IdentityResponse(userId);
  }
}
