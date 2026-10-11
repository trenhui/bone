package com.bone.metadata.engine.runtime;

import java.util.Map;

/**
 * 写路径前置钩子上下文（{@link RuntimeWriteHook} 入参）。
 *
 * @param entity 已发布的运行时实体定义
 * @param tenantId 当前租户
 * @param recordId 记录主键（创建时为 {@code null}）
 * @param payload 过滤后载荷：仅可写字段（不含审计列）；创建时已含主键/租户/软删默认值与入口状态； 钩子可直接修改本 Map 以改写写入内容（Directus filter
 *     可改载荷语义）
 * @param operatorId 当前操作者（可为 null）
 */
public record RuntimeWriteHookContext(
    PublishedRuntimeEntity entity,
    long tenantId,
    String recordId,
    Map<String, Object> payload,
    String operatorId) {}
