package com.bone.procurement.contract.domain.exception;

/** 合同解析失败领域异常：解析失败必须 fail-closed 转人工，不得产出自动风险结论。 */
public class ContractParseFailedException extends RuntimeException {

  public ContractParseFailedException(String message) {
    super(message);
  }

  public ContractParseFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
