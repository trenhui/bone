package com.bone.claim.application.port.out;

import java.util.Optional;

/**
 * 保单快照查询端口（出站，对接保单/承保系统，§3.5）。
 *
 * <p>报案前置校验用：保单生效中、被保人匹配、计划版本锁定值。 一期由本地 {@code *PortAdapter} 以存量同步数据应答；二期切真实保单系统。
 */
public interface PolicyQueryPort {

  /** 保单快照（不存在返回 {@link Optional#empty()}）。 */
  Optional<PolicySnapshot> findActivePolicy(String policyNo, Long planId);

  /** 保单快照（报案校验最小字段集）。 */
  record PolicySnapshot(
      String policyNo,
      Long planId,
      String planVersion,
      String insuredIdHash,
      java.time.Instant effectiveFrom,
      java.time.Instant effectiveTo) {}
}
