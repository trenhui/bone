package com.bone.procurement.business.domain.gateway;

/** 登录态身份端口：租户与操作人只由安全上下文裁决，请求体不得自报。 */
public interface BusinessIdentityPort {

  Actor current();

  record Actor(Long tenantId, String operator) {}
}
