package dev.notetrail.api;

public class ApiValidationException extends RuntimeException {
  public ApiValidationException(String message) {
    super(message);
  }
}
