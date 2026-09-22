package com.bone.masterdata.domain.model.lineage.event;

import com.bone.core.domain.DomainEvent;

/** 数据血缘领域事件：记录来源 → 转换 → 消费的一条血缘边。 */
public record DataLineageEvent(
    String sourceEntity,
    String sourceField,
    String transformType,
    String targetEntity,
    String targetField,
    String schemaName)
    implements DomainEvent {}
