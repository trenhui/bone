package com.bone.aiapp.web;

import java.util.List;

/** RAG 入库请求（API 层 DTO，与领域 RagIngestSegment 解耦）。 */
public record RagIngestRequest(String docId, List<Segment> segments) {

  /** 单切片：章节路径 + 文本 + 引用强制锚点（clauseRef / span）。 */
  public record Segment(
      String sectionPath, String text, String clauseRef, int spanStart, int spanEnd) {}
}
