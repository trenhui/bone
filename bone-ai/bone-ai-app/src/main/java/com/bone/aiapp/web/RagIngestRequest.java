package com.bone.aiapp.web;

import java.util.List;

/**
 * RAG 入库请求（API 层 DTO，与领域 RagIngestSegment 解耦）。
 *
 * <p>{@code docVersion} 为增量更新幂等批键（详设 §2.6）：同版本且切片数一致 ⇒ 幂等跳过； 异版本或残缺 ⇒ 失效旧切片后重建。
 */
public record RagIngestRequest(String docId, String docVersion, List<Segment> segments) {

  /** 单切片：章节路径 + 文本 + 引用强制锚点（clauseRef / span）。 */
  public record Segment(
      String sectionPath, String text, String clauseRef, int spanStart, int spanEnd) {}
}
