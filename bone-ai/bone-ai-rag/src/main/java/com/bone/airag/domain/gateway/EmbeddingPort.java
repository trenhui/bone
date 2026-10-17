package com.bone.airag.domain.gateway;

/** 嵌入端口（过渡：哈希占位；终局接 bge-m3 / ONNX）。消费方可 {@code @Bean} 覆盖。 */
public interface EmbeddingPort {

  /** 当前嵌入模型版本（chunk 的 {@code embedding_ver} 与之比对，不符则 fail-closed 跳过该切片向量路）。 */
  String version();

  float[] embed(String text);
}
