package com.boukensha.exception;

public class ApiError extends BoukenshaException {
  public ApiError(String message) {
    super(message);
  }

  public ApiError(String message, Throwable cause) {
    super(message, cause);
  }
}
