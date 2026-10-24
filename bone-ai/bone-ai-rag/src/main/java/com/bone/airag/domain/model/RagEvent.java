package com.bone.airag.domain.model;

/**
 * RAG 检索事件（降级可观测性载体；详设 §2.6 P1-10，2026-10-23 落地）。
 *
 * <p>机读通道：除 WARN 日志外，把「能力档位下降 / 数据卫生」事件结构化下沉，供接入方落 trace / 告警 （与 trace 落盘成功率 ≥99.9%
 * 硬依赖同一条告警线）。事件类型常量见 {@link com.bone.airag.application.RagApplicationService}（{@code DEGRADE_*} /
 * {@code EVENT_*}）。
 *
 * @param type 事件类型（稳定码，勿用于前端展示）
 * @param detail 人读详情（含租户 / 数量 / 原因等上下文）
 */
public record RagEvent(String type, String detail) {}
