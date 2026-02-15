package com.khant.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsConfigTest {

  @Test
  void corsConfigurationSource_shouldAllowConfiguredLocalOrigins() {
    CorsConfig corsConfig = new CorsConfig(List.of("http://localhost:*", "http://127.0.0.1:*"));
    CorsConfigurationSource source = corsConfig.corsConfigurationSource();

    MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/auth/login");
    request.addHeader("Origin", "http://localhost:4173");
    CorsConfiguration corsConfiguration = source.getCorsConfiguration(request);

    assertThat(corsConfiguration).isNotNull();
    assertThat(corsConfiguration.checkOrigin("http://localhost:4173")).isEqualTo("http://localhost:4173");
    assertThat(corsConfiguration.checkOrigin("https://example.com")).isNull();
    assertThat(corsConfiguration.getAllowedMethods()).contains("OPTIONS", "POST");
    assertThat(corsConfiguration.getAllowCredentials()).isFalse();
  }
}
