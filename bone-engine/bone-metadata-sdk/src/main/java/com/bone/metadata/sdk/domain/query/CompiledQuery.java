package com.bone.metadata.sdk.domain.query;

import java.util.Map;

public class CompiledQuery {
    private final String sql;
    private final Map<String, Object> parameters;
    
    // 添加构造器
    public CompiledQuery(String sql, Map<String, Object> parameters) {
        this.sql = sql;
        this.parameters = parameters;
    }
    
    // 添加无参构造器
    public CompiledQuery() {
        this.sql = null;
        this.parameters = null;
    }
    
    // 显式添加getter方法
    public String getSql() {
        return sql;
    }
    
    public Map<String, Object> getParameters() {
        return parameters;
    }
}