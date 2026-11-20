package com.bone.procurement.contract.domain.model.review;

/** 审查记录不存在。 */
public class ReviewNotFoundException extends RuntimeException {

  public ReviewNotFoundException(String message) {
    super(message);
  }
}
