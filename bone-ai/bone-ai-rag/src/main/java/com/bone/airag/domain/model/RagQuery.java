package com.bone.airag.domain.model;

/**
 * RAG 检索请求。
 *
 * <p>{@code minScore} / {@code maxScan} 为检索护栏（详设 §2.6）：
 * <ul>
 *   <li>{@code maxScan}——容量护栏，fail-closed：作用域切片数超限即抛 {@code AI_RAG_SCOPE_OVER_LIMIT}，
 *       绝不静默截断（「以为检了全集」的假设不成立时引用完整性失真）；</li>
 *   <li>{@code minScore}——分数阈值，在精排后按最终分过滤（宁缺毋滥，返回数可小于 topK）；
 *       量纲双轨：接入 cross-encoder 时为 rerank 分，未接入（NoOp/降级）时为 RRF 融合分（~0.01 量级），
 *       默认 0 = 不启用，阈值须按实测分布校准（{@code bone.ai.rag.min-score}）。</li>
 * </ul>
 */
public record RagQuery(String query, String docScope, int topK, double minScore, int maxScan) {

  /** 默认容量护栏：过渡态（全量载入内存打分）下单作用域切片数上限，超出 fail-closed。 */
  public static final int DEFAULT_MAX_SCAN = 10_000;

  /** 默认返回条数（topK 未显式指定时）。 */
  public static final int DEFAULT_TOP_K = 5;

  /** topK 上限：防单查询拖垮推理 provider / DB（精排按 topK 截断）。 */
  public static final int MAX_TOP_K = 50;

  /** 兼容 3 参构造：不启用分数阈值 + 默认容量护栏。 */
  public RagQuery(String query, String docScope, int topK) {
    this(query, docScope, topK, 0.0, DEFAULT_MAX_SCAN);
  }

  public RagQuery {
    // 紧凑构造器内钳制（记录类组件末位隐式赋字段；query/docScope 原样透传）
    topK = topK <= 0 ? DEFAULT_TOP_K : Math.min(topK, MAX_TOP_K);
    maxScan = maxScan <= 0 ? DEFAULT_MAX_SCAN : maxScan;
    minScore = minScore < 0 ? 0.0 : minScore;
  }
}
