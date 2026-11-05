package com.bone.claim.application.port.out;

import java.math.BigDecimal;
import java.util.List;

/**
 * 案件上下文查询端口（出站，防腐层）。
 *
 * <p>决策图各节点所需的轻量只读上下文（赔案金额/类型 + 证据齐备度/解析度/验真/最低 OCR 置信度 + 材料解析事实）。 一期由本地 {@code
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
      boolean evidenceAllVerifyPassed,
      List<EvidenceFact> evidenceFacts) {

    public ClaimContextView {
      evidenceFacts = evidenceFacts == null ? List.of() : List.copyOf(evidenceFacts);
    }

    /**
     * 兼容构造：无材料事实（既有调用点与测试桩）。
     *
     * <p>保留它而不是让所有调用方改签名，是因为「没有材料事实」是一个真实状态（无材料案件、读模型未投影）， 不该迫使调用方编一个空列表出来。
     */
    public ClaimContextView(
        long claimId,
        String claimType,
        BigDecimal claimAmount,
        boolean hasEvidence,
        boolean evidenceAllParsed,
        BigDecimal minOcrConfidence,
        boolean evidenceAllVerifyPassed) {
      this(
          claimId,
          claimType,
          claimAmount,
          hasEvidence,
          evidenceAllParsed,
          minOcrConfidence,
          evidenceAllVerifyPassed,
          List.of());
    }
  }

  /**
   * 单条材料事实（感知层解析产物，带原文定位）。
   *
   * <p><b>它是认知层唯一的材料输入</b>：此前 AI 只收到「有没有材料 / 材料解析完没 / 最低 OCR 置信度」这类聚合标量， 于是模型只能复述这些标量（实测 {@code
   * aiFacts} 的 code 就是 {@code claimType}/{@code claimAmount}）， 「从材料抽取事实」这件事从未发生。
   *
   * @param docType 材料类型（发票 / 病历 / 处方…），供模型按材料种类取舍
   * @param code 受控字段码（感知层产出，如 {@code invoice_no} / {@code diagnosis_code}）
   * @param value 字段值（已按 {@code EvidenceFactPolicy} 截断；不含被禁止出域的字段）
   * @param evidenceRef 原文定位（如 {@code text@67:8}）—— 模型引用事实时必须原样照抄它， 系统据此校验「模型说的事实在材料里确实存在」
   */
  record EvidenceFact(String docType, String code, String value, String evidenceRef) {}
}
