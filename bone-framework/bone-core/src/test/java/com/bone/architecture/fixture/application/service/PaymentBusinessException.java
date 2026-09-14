package com.bone.architecture.fixture.application.service;

/** 规则单测夹具：*BusinessException 后缀异常（noBusinessExceptionSuffix 违规）。 */
public class PaymentBusinessException extends RuntimeException {

  public PaymentBusinessException(String message) {
    super(message);
  }
}
