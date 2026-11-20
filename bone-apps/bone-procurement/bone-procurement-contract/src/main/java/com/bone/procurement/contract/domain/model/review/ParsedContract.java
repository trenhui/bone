package com.bone.procurement.contract.domain.model.review;

import java.util.List;

/**
 * 合同解析结果（本上下文自有 DTO）。
 *
 * <p>字段对齐 bone-ai-document 的 DocumentIR 中审查编排真正需要的部分；provider 元信息等解析器私有细节 不进本上下文。跨上下文转换在
 * infrastructure 的 ContractParsePortAdapter 完成，domain 不依赖 aidoc。
 *
 * @param docId 文档标识（租户内唯一）
 * @param tenantId 租户
 * @param sourceRef 来源引用（文件 key / URL）
 * @param pageCount 页数
 * @param clauses 条款切片（含 span 定位）
 * @param partial 是否部分解析结果（属降级输入，调用方应转人工）
 */
public record ParsedContract(
    String docId,
    String tenantId,
    String sourceRef,
    int pageCount,
    List<ParsedClause> clauses,
    boolean partial) {

  public ParsedContract {
    clauses = clauses == null ? List.of() : List.copyOf(clauses);
  }

  public static ParsedContract of(
      String docId,
      String tenantId,
      String sourceRef,
      int pageCount,
      List<ParsedClause> clauses,
      boolean partial) {
    return new ParsedContract(docId, tenantId, sourceRef, pageCount, clauses, partial);
  }
}
