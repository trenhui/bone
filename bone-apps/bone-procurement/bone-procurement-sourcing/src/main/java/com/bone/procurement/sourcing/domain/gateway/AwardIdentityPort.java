package com.bone.procurement.sourcing.domain.gateway;

/**
 * 人工授标身份端口（只读）。租户与操作人只来自登录态，绝不读取请求体；匿名或无法解析时返回 null， 调用方必须 fail-closed。定义在 sourcing 域以避免依赖
 * qualification 域的准入概念。
 */
public interface AwardIdentityPort {

  record AwardIdentity(Long tenantId, String operator) {}

  /** 当前登录态授标身份；匿名或缺少租户/操作人返回 null。 */
  AwardIdentity current();
}
