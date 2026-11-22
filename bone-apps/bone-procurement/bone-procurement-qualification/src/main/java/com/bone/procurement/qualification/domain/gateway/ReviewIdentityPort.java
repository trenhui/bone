package com.bone.procurement.qualification.domain.gateway;

/**
 * 人工终决的登录态身份来源（租户 + 具名操作人）。租户与操作人 MUST 取自已签名登录态，不接受请求自报。
 *
 * <p>无登录态或租户/操作人无法解析时返回 {@code null}，调用方必须 fail-closed 拒绝，不得用默认租户或空操作人落库。
 */
public interface ReviewIdentityPort {

  record ReviewIdentity(Long tenantId, String reviewer) {}

  /** 当前登录态身份；不可判定时返回 null。 */
  ReviewIdentity current();
}
