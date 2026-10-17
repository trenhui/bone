package com.bone.aiapp.web;

import java.math.BigDecimal;

/** 评测写入面 DTO（case / run；租户从上下文取，禁入参传租户）。 */
public final class EvalWriteRequests {

  private EvalWriteRequests() {}

  /** 评测用例写入请求。 */
  public record EvalCaseRequest(
      String scene,
      String caseType,
      String input,
      String expected,
      boolean expectedHit,
      Integer difficulty,
      BigDecimal kappa) {}

  /** 评测运行写入请求（一次门禁判定快照）。 */
  public record EvalRunRequest(
      String gate,
      Long caseId,
      String predicted,
      String expected,
      boolean expectedHit,
      boolean predictedHit,
      String citedClause,
      BigDecimal judgeScore,
      boolean passed) {}
}
