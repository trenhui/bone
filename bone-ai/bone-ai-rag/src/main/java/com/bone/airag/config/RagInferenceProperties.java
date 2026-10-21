package com.bone.airag.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 推理服务配置（{@code bone.ai.rag.*}）。
 *
 * <p>嵌入 / 重排序默认走<b>共享推理服务</b>（TEI / infinity 兼容，多副本友好，避免每副本加载 GB 级模型）。 未配置 {@code base-url}
 * 时分别回退：嵌入 → 过渡哈希（无语义，仅保链路可跑）、重排序 → {@code NoOpReranker} （保持 RRF 序）。进程内 ONNX（onnxruntime + 模型文件）作为
 * L4 部署备选，经同一 {@code EmbeddingPort} / {@code RerankPort} 端口由消费方以 {@code @Bean} 覆盖接入。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bone.ai.rag")
public class RagInferenceProperties {

  private Embedding embedding = new Embedding();
  private Rerank rerank = new Rerank();

  /** 进入重排序的候选池大小（≥ topK）：RRF 融合后取前 N 名送 cross-encoder 精排，默认 30（详设 §2.6 top-30）。 */
  private int candidatePool = 30;

  @Getter
  @Setter
  public static class Embedding {
    /** 共享嵌入服务 base-url（如 {@code http://tei:80}）；空则回退过渡哈希实现。 */
    private String baseUrl;

    private String model = "BAAI/bge-m3";

    /** 嵌入模型版本标识，写入 chunk.embedding_ver；与之不符的旧向量 fail-closed 跳过向量路。 */
    private String version = "bge-m3@http";

    private Duration timeout = Duration.ofSeconds(10);
  }

  @Getter
  @Setter
  public static class Rerank {
    /** 共享重排序服务 base-url（如 {@code http://tei:80}）；空则回退 NoOpReranker。 */
    private String baseUrl;

    private String model = "BAAI/bge-reranker-v2-m3";
    private Duration timeout = Duration.ofSeconds(10);
  }
}
