package com.boukensha.exception;

public class ApiError extends BoukenkshaException {
  public ApiError(String message) {
    super(message);
  }

  public ApiError(String message, Throwable cause) {
    super(message, cause);
  }
}
