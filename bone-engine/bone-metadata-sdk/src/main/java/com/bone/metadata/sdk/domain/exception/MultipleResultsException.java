package com.bone.metadata.sdk.domain.exception;

public class MultipleResultsException extends SDKException {
  private static final long serialVersionUID = 1L;

  public MultipleResultsException(String message) {
    super("MULTIPLE_RESULTS_ERROR", message);
  }

  public MultipleResultsException(String message, Throwable cause) {
    super("MULTIPLE_RESULTS_ERROR", message, cause);
  }
}
