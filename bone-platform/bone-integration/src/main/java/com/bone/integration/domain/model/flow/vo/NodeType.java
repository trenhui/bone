package com.bone.integration.domain.model.flow.vo;

import com.bone.core.exception.DomainException;

public enum NodeType {
    START, END, HTTP, JDBC, FTP, MQ, TRANSFORM, DECISION, SCRIPT, LOG, WAIT, LOOP, PARALLEL, CUSTOM;

    public static NodeType fromString(String type) {
        if (type == null || type.isBlank()) {
            throw new DomainException("节点类型不能为空");
        }
        try {
            return NodeType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DomainException("不支持的节点类型: " + type);
        }
    }
}