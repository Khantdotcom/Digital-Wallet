package com.khant.wallet.controller;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  private final JdbcTemplate jdbcTemplate;

  public HealthController(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "ok");
  }

  @GetMapping("/ready")
  public Map<String, String> ready() {
    jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    return Map.of("status", "ready");
  }

  @GetMapping("/secure-test")
  public String secure() {
    return "secured";
  }
}
