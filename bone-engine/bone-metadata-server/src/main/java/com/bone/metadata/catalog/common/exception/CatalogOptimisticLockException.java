package com.bone.metadata.catalog.common.exception;

/** If-Match / version 与当前 catalog 实体不一致（HTTP 412）。 */
public class CatalogOptimisticLockException extends RuntimeException {

  public CatalogOptimisticLockException(String message) {
    super(message);
  }
}
