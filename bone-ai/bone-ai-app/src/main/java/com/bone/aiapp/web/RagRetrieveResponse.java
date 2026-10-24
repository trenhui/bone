package com.bone.aiapp.web;

import com.bone.airag.domain.model.RagResult;
import java.util.List;

/**
 * 受控检索响应（API 层 DTO）：命中切片 + 降级痕迹。
 *
 * <p>{@code degradations} 非空表示本次检索发生了能力档位下降（{@code EMBEDDING_DEGRADED} = 回落 BM25 单路、 {@code
 * RERANK_DEGRADED} = 回落 RRF 粗序）——调用方 / 评测管线可观测，而非只能翻服务端日志（详设 §2.6 P1-10）。 引用强制语义不随降级改变：结果仍全部带出处锚点。
 */
public record RagRetrieveResponse(List<RagResult> results, List<String> degradations) {}
