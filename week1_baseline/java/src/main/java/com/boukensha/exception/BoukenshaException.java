package com.boukensha.exception;

public class BoukenkshaException extends RuntimeException {
  public BoukenkshaException(String message) {
    super(message);
  }

  public BoukenkshaException(String message, Throwable cause) {
    super(message, cause);
  }
}
