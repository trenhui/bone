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

  /**
   * 分数阈值（精排后按最终分过滤，默认 0 = 不启用）。
   *
   * <p>量纲双轨：接入 cross-encoder 时为 rerank 分（按实测分布校准）；未接入时为 RRF 融合分（~0.01 量级）。 阈值须按评测集回流数据标定，默认关闭避免误杀（详设 §2.6）。
   */
  private double minScore = 0.0;

  /**
   * 容量护栏（fail-closed）：单作用域切片数上限，超限抛 {@code AI_RAG_SCOPE_OVER_LIMIT} 而非静默截断， 默认 10000（过渡态全量载入内存打分的兜底；终局索引化后此阈值即迁移触发信号）。
   */
  private int maxScan = 10_000;

  /**
   * MMR 多样性权重 λ（top-K 选取：{@code λ·相关性 − (1−λ)·与已选的最大相似度}，防 top-K 同质化；默认 0 = 关闭）。
   *
   * <p>λ 约定：λ∈(0,1) 生效；λ=1 纯相关性（数学退化，等价关闭）；λ=0 按约定视为关闭而非数学上的纯多样性
   * ——避免运维误配 0 得到乱序结果。阈值须按评测集回流数据校准（批二），默认关闭避免误伤（详设 §2.6 P1-9）。
   */
  private double mmrLambda = 0.0;

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
