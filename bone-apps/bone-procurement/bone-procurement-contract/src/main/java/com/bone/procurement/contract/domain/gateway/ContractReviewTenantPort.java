package com.bone.procurement.contract.domain.gateway;

/** 当前登录租户出站端口：租户只从登录态取，不接收请求自报。 */
@FunctionalInterface
public interface ContractReviewTenantPort {

  /** 当前登录租户 ID；无登录上下文返回 null（调用方必须 fail-closed）。 */
  String currentTenantId();
}
