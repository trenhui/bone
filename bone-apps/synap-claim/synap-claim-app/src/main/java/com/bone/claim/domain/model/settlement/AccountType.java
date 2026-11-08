package com.bone.claim.domain.model.settlement;

/**
 * 账户类型（§5.4 公账/个账分账）。
 *
 * <p>企业补充医疗险资金归集方决定赔付去向，理算须分账（{@code NO_ACCOUNT} 无账户场景直接赔付员工）。
 * 一期：只区分分账与冻结；直付账户余额查询为二期，但分账字段一期必须落库（对账与后续直付接入依赖）。
 */
public enum AccountType {
  NO_ACCOUNT,
  PUBLIC,
  PERSONAL
}
