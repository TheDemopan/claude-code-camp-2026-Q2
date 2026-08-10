package com.boukensha.exception;

public class BoukenshaException extends RuntimeException {
  public BoukenshaException(String message) {
    super(message);
  }

  public BoukenshaException(String message, Throwable cause) {
    super(message, cause);
  }
}
