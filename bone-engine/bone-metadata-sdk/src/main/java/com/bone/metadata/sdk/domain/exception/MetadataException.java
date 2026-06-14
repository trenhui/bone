package com.bone.metadata.sdk.domain.exception;

public class MetadataException extends SDKException {
  private static final long serialVersionUID = 1L;

  public MetadataException(String message) {
    super("METADATA_ERROR", message);
  }

  public MetadataException(String message, Throwable cause) {
    super("METADATA_ERROR", message, cause);
  }
}
