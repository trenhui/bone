package com.bone.claim.domain.model.event;

import com.bone.core.domain.DomainEvent;
import java.util.Map;

/**
 * 发件箱兜底重投的泛型事件载体。
 *
 * <p>原领域事件经 afterCommit 已按类型即时投送；本载体用于在 afterCommit 失败时的兜底重投。载荷保持为原始 JSON 解析出的 {@code Map}，事件类型沿用原始
 * {@code eventType}。完整的「按原始类型反序列化重投」需领域事件类型注册表，属后续迭代（见 OutboxPort 实现说明）。
 */
public record GenericOutboxEvent(
    String eventType, String aggregate, Long aggregateId, Map<String, Object> payload)
    implements DomainEvent {}
