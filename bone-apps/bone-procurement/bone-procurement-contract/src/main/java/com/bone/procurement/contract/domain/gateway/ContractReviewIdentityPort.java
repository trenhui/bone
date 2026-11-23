package com.bone.procurement.contract.domain.gateway;

/**
 * 合同人工终决身份端口：复核人只能来自登录态，绝不读请求体。
 *
 * <p>返回 {@code null} 表示匿名或身份不可解析，调用方必须 fail-closed 拒绝终决。
 */
public interface ContractReviewIdentityPort {
  String currentReviewer();
}
