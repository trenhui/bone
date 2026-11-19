package com.bone.procurement.contract.domain.exception;

/** 跨租户访问审查记录：fail-closed，不泄露另一租户记录是否存在以外的任何信息。 */
public class CrossTenantAccessException extends RuntimeException {

  public CrossTenantAccessException(String message) {
    super(message);
  }
}
