package com.bone.airag.domain.gateway;

import com.bone.airag.domain.model.RerankCandidate;
import com.bone.airag.domain.model.RerankScored;
import java.util.List;

/**
 * 重排序端口（cross-encoder / bge-reranker）：对双路召回的候选切片按与查询的相关性精排。
 *
 * <p>RRF 融合是「多路召回的粗融合」，cross-encoder 在 query-document 对上直接打分，精度显著更高， 是 RAG「引用正确率」达标的关键一环（详设 §2.6
 * top-30 → top-5）。消费方可 {@code @Bean} 覆盖为接共享 推理服务（TEI / infinity）或进程内 ONNX 实现。
 */
public interface RerankPort {

  /**
   * 对候选按相关性降序重排，返回前 {@code topK}。
   *
   * @param query 用户查询
   * @param candidates 候选切片（已按先验分粗排）
   * @param topK 返回条数（实现方按分数降序截断）
   */
  List<RerankScored> rerank(String query, List<RerankCandidate> candidates, int topK);

  /**
   * 该实现是否为「无精排」兜底（不产出 cross-encoder 分）。默认 {@code false}。
   *
   * <p>用途：检索链的最终分存在<b>量纲双轨</b>——rerank 正常时为 cross-encoder 分（0~1 / 对数）， 降级或默认 NoOp 时为 RRF 融合分（~0.01
   * 量级）。{@code minScore} 阈值是按 rerank 量纲标的， 一旦降级/未配 rerank 仍用同一阈值过滤，会把所有结果误杀成空（事故期间质量腰斩）。 故 {@code
   * minScore} 仅当 {@code !isNoOp() 且未降级}（即分数为 rerank 量纲）时才生效； 其余情形跳过阈值，保留 RRF 序。见 {@code
   * RagApplicationService.retrieve} 与诊断复核 v1.2。
   */
  default boolean isNoOp() {
    return false;
  }
}
