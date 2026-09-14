package com.bone.architecture.fixture.application.service;

/** 规则单测夹具：简单名恰为 BusinessException（noCustomBusinessException 违规）。 */
public class BusinessException extends RuntimeException {

  public BusinessException(String message) {
    super(message);
  }
}
