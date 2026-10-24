package com.bone.aitrace.domain.model;

/** 证据类型（批一 LLM 调用；2026-10-24 R6 增 RAG 检索事件，后续可扩展规则判定等）。 */
public enum EvidenceType {
  /** 一次 LLM 调用（合规三元组：输入哈希 + 请求/响应 IR + 模型版本）。 */
  LLM_CALL,

  /**
   * 一次 RAG 检索事件（降级 / 数据卫生信号；详设 §2.6 P1-10）。
   *
   * <p>与 {@link #LLM_CALL} 的区别：LLM 调用是合规铁证（租户缺失 fail-closed 拒落），RAG 事件是运行时观测信号（租户缺失仅 WARN
   * 跳过）——前者丢了是审计链失真，后者丢了只影响降级可观测性，姿态不可混同。
   */
  RAG_EVENT
}
