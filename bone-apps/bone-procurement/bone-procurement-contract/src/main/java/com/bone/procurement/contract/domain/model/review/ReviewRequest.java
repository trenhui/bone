package com.bone.procurement.contract.domain.model.review;

import java.util.List;

/** AI 审查请求：携带解析后的条款锚点（本上下文自有 DTO），不携带合同原文全文。 */
public record ReviewRequest(
    String reviewId, String contractNo, String tenantId, List<ParsedClause> clauses) {

  public ReviewRequest {
    clauses = clauses == null ? List.of() : List.copyOf(clauses);
  }
}
