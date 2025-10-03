package com.bone.metadata.sdk.domain.query;

import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class BatchCompiledQuery extends CompiledQuery {
    private final List<Map<String, Object>> batchParameters;
    public BatchCompiledQuery(String sql,  List<Map<String, Object>> batchParameters) {
        super(sql, batchParameters.get(0));
        this.batchParameters = batchParameters;
    }
}
