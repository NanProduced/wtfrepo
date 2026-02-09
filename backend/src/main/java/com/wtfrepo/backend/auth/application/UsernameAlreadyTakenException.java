package com.wtfrepo.backend.auth.application;

public class UsernameAlreadyTakenException extends RuntimeException {

  public UsernameAlreadyTakenException(String message) {
    super(message);
  }

  public UsernameAlreadyTakenException(String message, Throwable cause) {
    super(message, cause);
  }
}
