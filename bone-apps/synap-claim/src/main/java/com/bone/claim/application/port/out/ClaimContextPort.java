package com.bone.claim.application.port.out;

import java.math.BigDecimal;

/**
 * 案件上下文查询端口（出站，防腐层）。
 *
 * <p>决策图各节点所需的轻量只读上下文（赔案金额/类型 + 证据齐备度/解析度/验真/最低 OCR 置信度）。 一期由本地 {@code
 * LocalClaimContextPortAdapter} 经 {@code ClaimCaseRepository}/{@code EvidenceRepository} 拼装；
 * 二期替换为查询服务/读模型投影——领域与应用层零感知。
 */
public interface ClaimContextPort {

  /** 取案件决策上下文；案件不存在时由适配器抛领域异常（fail-safe）。 */
  ClaimContextView getContext(long claimId);

  /** 决策图只读上下文视图（值对象，禁携带实体引用）。 */
  record ClaimContextView(
      long claimId,
      String claimType,
      BigDecimal claimAmount,
      boolean hasEvidence,
      boolean evidenceAllParsed,
      BigDecimal minOcrConfidence,
      boolean evidenceAllVerifyPassed) {}
}
