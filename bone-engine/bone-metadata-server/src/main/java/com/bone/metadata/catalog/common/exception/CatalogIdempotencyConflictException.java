package com.bone.metadata.catalog.common.exception;

/** 同一 Idempotency-Key 用于不同请求体（HTTP 409）。 */
public class CatalogIdempotencyConflictException extends RuntimeException {

  public CatalogIdempotencyConflictException(String message) {
    super(message);
  }
}
