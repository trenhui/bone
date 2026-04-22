package com.bone.integration.domain.model.connector.vo;

import com.bone.core.exception.DomainException;

public record ConnectorId(Long value) {
    public ConnectorId {
        if (value == null || value <= 0) {
            throw new DomainException("连接器ID必须大于0");
        }
    }

    public static ConnectorId of(Long value) {
        return new ConnectorId(value);
    }
}