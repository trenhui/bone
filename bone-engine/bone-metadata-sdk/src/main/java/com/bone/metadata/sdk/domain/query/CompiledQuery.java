package com.bone.metadata.sdk.domain.query;

import lombok.Data;

import java.util.Map;

@Data
public  class CompiledQuery {
    private final String sql;
    private final Map<String, Object> parameters;
}