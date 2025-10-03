package com.bone.metadata.sdk.domain.exception;

public class MetadataException extends RuntimeException {
    public MetadataException(String message, Throwable cause) {
        super(message, cause);
    }

    public MetadataException(String message) {
        super(message);
    }
}