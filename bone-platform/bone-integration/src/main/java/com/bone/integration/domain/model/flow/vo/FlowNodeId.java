package com.bone.integration.domain.model.flow.vo;

import com.bone.core.exception.DomainException;

public record FlowNodeId(Long value) {
    public FlowNodeId {
        if (value == null || value <= 0) {
            throw new DomainException("流程节点ID必须大于0");
        }
    }

    public static FlowNodeId of(Long value) {
        return new FlowNodeId(value);
    }
}