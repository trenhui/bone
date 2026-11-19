package com.bone.procurement.contract.domain.exception;

/** 审查记录不存在。 */
public class ReviewNotFoundException extends RuntimeException {

  public ReviewNotFoundException(String message) {
    super(message);
  }
}
