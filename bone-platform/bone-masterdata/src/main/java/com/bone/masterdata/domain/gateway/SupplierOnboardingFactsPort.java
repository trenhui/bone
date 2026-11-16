package com.bone.masterdata.domain.gateway;

/**
 * 供应商准入所需的<b>变更请求域事实</b>端口。
 *
 * <p>spec:54 要求 {@code onboarding_approved} 成立的前提之一是「变更请求全段已批准」。这是变更请求域的事实，
 * 供应商域无权自行判定，也不允许由调用方在命令里自报（自报即自证）。
 *
 * <p>本切片未接变更请求域的生产实现，因此该端口在 main 里没有 bean；测试用夹具显式提供 true/false 两种取值， 负向注入可验证「未全段批准 → 迁移必红」。生产实现属
 * R-2。
 */
public interface SupplierOnboardingFactsPort {

  /** 该租户该主体的准入变更请求是否已全段批准。 */
  boolean changeRequestFullyApproved(long tenantId, String partyId);
}
