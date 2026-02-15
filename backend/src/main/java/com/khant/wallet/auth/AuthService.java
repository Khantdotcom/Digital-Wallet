package com.khant.wallet.auth;

import com.khant.wallet.domain.User;
import com.khant.wallet.dto.AuthResponse;
import com.khant.wallet.dto.LoginRequest;
import com.khant.wallet.dto.RegisterRequest;
import com.khant.wallet.exception.EmailAlreadyExistsException;
import com.khant.wallet.exception.InvalidCredentialsException;
import com.khant.wallet.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    String normalizedEmail = request.email().trim().toLowerCase();

    if (userRepository.findByEmail(normalizedEmail).isPresent()) {
      throw new EmailAlreadyExistsException(normalizedEmail);
    }

    User user = new User();
    user.setEmail(normalizedEmail);
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    User savedUser = userRepository.save(user);

    String token = jwtService.generateToken(savedUser.getId(), savedUser.getEmail());
    return new AuthResponse(token, "Bearer", savedUser.getId(), savedUser.getEmail());
  }

  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest request) {
    String normalizedEmail = request.email().trim().toLowerCase();

    User user = userRepository.findByEmail(normalizedEmail)
        .orElseThrow(InvalidCredentialsException::new);

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }

    String token = jwtService.generateToken(user.getId(), user.getEmail());
    return new AuthResponse(token, "Bearer", user.getId(), user.getEmail());
  }
}
