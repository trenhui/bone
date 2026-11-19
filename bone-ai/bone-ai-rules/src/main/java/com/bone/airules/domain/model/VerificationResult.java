package com.bone.airules.domain.model;

/** 规则包验签结论（G12 双签名门）：只有 ACCEPTED 可进入发布，其余一律拒绝且不产出可执行规则。 */
public enum VerificationResult {
  ACCEPTED,
  REJECTED_SIGNATURE,
  REJECTED_SIGNER_NOT_DISTINCT,
  REJECTED_KEY_UNKNOWN,
  REJECTED_ALGORITHM,
  REJECTED_PAYLOAD_HASH
}
