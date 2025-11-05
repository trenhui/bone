package com.bone.metadata.sdk.domain.query;

import lombok.Data;

import java.util.*;

@Data
public class CompiledQuery {
    private final String sql;
    private final Map<String, Object> parameters;

    public CompiledQuery(String sql, Map<String, Object> parameters) {
        this.sql = sql;
        this.parameters = parameters != null ? parameters : Map.of();
    }

    public CompiledQuery(String sql, List<Object> positional) {
        this.sql = sql;
        this.parameters = toNamed(positional);
    }

    public CompiledQuery(String sql, Object... params) {
        this(sql, Arrays.asList(params));
    }

    private static Map<String, Object> toNamed(List<Object> list) {
        if (list == null || list.isEmpty()) return Map.of();
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            map.put("p" + i, list.get(i));
        }
        return map;
    }
}