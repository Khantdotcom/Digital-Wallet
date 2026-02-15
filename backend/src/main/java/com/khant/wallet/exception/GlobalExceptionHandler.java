package com.khant.wallet.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
        .collect(Collectors.toMap(
            FieldError::getField,
            fieldError -> fieldError.getDefaultMessage() == null ? "invalid value" : fieldError.getDefaultMessage(),
            (first, second) -> first
        ));

    ApiErrorResponse body = new ApiErrorResponse(
        Instant.now(),
        HttpStatus.BAD_REQUEST.value(),
        HttpStatus.BAD_REQUEST.getReasonPhrase(),
        "Validation failed",
        fieldErrors
    );

    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(EmailAlreadyExistsException.class)
  public ResponseEntity<ApiErrorResponse> handleConflict(EmailAlreadyExistsException ex) {
    ApiErrorResponse body = new ApiErrorResponse(
        Instant.now(),
        HttpStatus.CONFLICT.value(),
        HttpStatus.CONFLICT.getReasonPhrase(),
        ex.getMessage(),
        Map.of()
    );

    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<ApiErrorResponse> handleUnauthorized(InvalidCredentialsException ex) {
    ApiErrorResponse body = new ApiErrorResponse(
        Instant.now(),
        HttpStatus.UNAUTHORIZED.value(),
        HttpStatus.UNAUTHORIZED.getReasonPhrase(),
        ex.getMessage(),
        Map.of()
    );

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
  }


  @ExceptionHandler(WalletNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleWalletNotFound(WalletNotFoundException ex) {
    ApiErrorResponse body = new ApiErrorResponse(
        Instant.now(),
        HttpStatus.NOT_FOUND.value(),
        HttpStatus.NOT_FOUND.getReasonPhrase(),
        ex.getMessage(),
        Map.of()
    );

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
  }

  @ExceptionHandler(InsufficientFundsException.class)
  public ResponseEntity<ApiErrorResponse> handleInsufficientFunds(InsufficientFundsException ex) {
    ApiErrorResponse body = new ApiErrorResponse(
        Instant.now(),
        HttpStatus.BAD_REQUEST.value(),
        HttpStatus.BAD_REQUEST.getReasonPhrase(),
        ex.getMessage(),
        Map.of()
    );

    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiErrorResponse> handleBadRequest(IllegalArgumentException ex) {
    ApiErrorResponse body = new ApiErrorResponse(
        Instant.now(),
        HttpStatus.BAD_REQUEST.value(),
        HttpStatus.BAD_REQUEST.getReasonPhrase(),
        ex.getMessage(),
        Map.of()
    );

    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
    ApiErrorResponse body = new ApiErrorResponse(
        Instant.now(),
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
        "Unexpected server error while processing: " + request.getRequestURI(),
        Map.of()
    );

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }
}
