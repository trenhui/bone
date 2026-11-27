package com.bone.procurement.business.domain.model.business;

/** 投标显式状态；撤回与拒绝为终态，不允许隐式复活。 */
public enum BidStatus {
  DRAFT,
  SUBMITTED,
  WITHDRAWN,
  REJECTED
}
