package com.bone.airag.domain.gateway;

/** 嵌入端口（过渡：哈希占位；终局接 bge-m3 / ONNX）。消费方可 {@code @Bean} 覆盖。 */
public interface EmbeddingPort {
  float[] embed(String text);
}
