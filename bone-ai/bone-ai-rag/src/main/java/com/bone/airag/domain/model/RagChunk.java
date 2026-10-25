package com.bone.airag.domain.model;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * RAG 切片（受控检索的最小存储单元）。
 *
 * <p>承载「引用强制」所需锚点——{@code clauseRef}（条款 id）+ {@code spanStart/spanEnd}（条款内 span），
 * 无此二者的切片视为「无出处」，检索结果须 fail-closed 丢弃（详设 §2.6「无出处不得生成」）。
 *
 * <p>嵌入向量过渡形态存于 {@code embedding} 列（CSV float，终局改 MySQL JSON 列 + 向量检索）。
 *
 * <p>{@code docVersion} 为增量更新幂等批键（详设 §2.6）：ingest 时同版本且切片数一致 ⇒ 跳过； 异版本或残缺（崩溃半截）⇒ 软删该文档旧切片后重建。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("ai_rag_chunk")
public class RagChunk extends TenantAggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "doc_id")
  private String docId;

  @Column(name = "doc_version")
  private String docVersion; // 增量幂等批键：同版本同切片数 ⇒ 跳过；异版本/残缺 ⇒ 软删重建（详设 §2.6）

  @Column(name = "section_path")
  private String sectionPath; // chunk.path 面包屑（条款/章节层级）

  @Column(name = "chunk_text")
  private String text;

  @Column(name = "embedding")
  private String embedding; // 过渡：CSV float；终局 MySQL JSON 列

  @Column(name = "embedding_ver")
  private String embeddingVer; // 嵌入模型版本（换模型维度不符时 fail-closed 告警，不静默 0 分）

  @Column(name = "clause_ref")
  private String clauseRef; // 引用强制锚点（clause_id）

  @Column(name = "span_start")
  private int spanStart;

  @Column(name = "span_end")
  private int spanEnd;

  @Deleted private Boolean deleted = false;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  public static RagChunk of(
      Long id,
      Long tenantId,
      String docId,
      String docVersion,
      String sectionPath,
      String text,
      float[] embedding,
      String embeddingVer,
      String clauseRef,
      int spanStart,
      int spanEnd) {
    RagChunk chunk = new RagChunk();
    chunk.id = id;
    chunk.setTenantId(tenantId);
    chunk.docId = docId;
    chunk.docVersion = docVersion;
    chunk.sectionPath = sectionPath;
    chunk.text = text;
    chunk.embedding = encode(embedding);
    chunk.embeddingVer = embeddingVer;
    chunk.clauseRef = clauseRef;
    chunk.spanStart = spanStart;
    chunk.spanEnd = spanEnd;
    LocalDateTime now = LocalDateTime.now();
    chunk.createdAt = now;
    chunk.updatedAt = now;
    return chunk;
  }

  public float[] parseEmbedding() {
    return decode(embedding);
  }

  private static String encode(float[] v) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < v.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(v[i]);
    }
    return sb.toString();
  }

  private static float[] decode(String s) {
    if (s == null || s.isEmpty()) {
      return new float[0];
    }
    String[] parts = s.split(",");
    float[] v = new float[parts.length];
    for (int i = 0; i < parts.length; i++) {
      v[i] = Float.parseFloat(parts[i]);
    }
    return v;
  }
}
