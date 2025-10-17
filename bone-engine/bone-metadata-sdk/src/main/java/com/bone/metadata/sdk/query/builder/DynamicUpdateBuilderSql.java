package com.bone.metadata.sdk.query.builder;

import com.bone.core.domain.extension.ExtensibleObject;
import com.bone.core.util.ReflectionUtil;
import com.bone.metadata.sdk.query.context.DynamicUpdateContext;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.domain.model.ColumnMetadata;
import com.bone.metadata.sdk.domain.model.TableMetadata;

import java.util.*;

public class DynamicUpdateBuilderSql implements SqlQueryBuilder<DynamicUpdateContext> {
    @Override
    public CompiledQuery build(DynamicUpdateContext ctx) {
        TableMetadata table = ctx.getTable();
        Object e = ctx.getEntity();
        ColumnMetadata pk = table.getPrimaryKey();

        Map<String, Object> params = new LinkedHashMap<>();
        List<String> clauses = new ArrayList<>();

        for (ColumnMetadata c : table.getColumns()) {
            if (c.isPrimaryKey()) continue;
            Object v = ReflectionUtil.getFieldValue(e, c.getFieldName());
            clauses.add(c.getName() + " = :" + c.getName());
            params.put(c.getName(), v);
        }
        if (e instanceof ExtensibleObject ext) {
            ext.getExtraProperties().forEach((k, v) -> {
                clauses.add("ext_" + k + " = :ext_" + k);
                params.put("ext_" + k, v);
            });
        }

        params.put(pk.getName(), ReflectionUtil.getFieldValue(e, pk.getFieldName()));
        if (clauses.isEmpty()) {
            throw new IllegalStateException("没有可更新字段");
        }
        String sql = "UPDATE " + table.getName()
                + " SET " + String.join(", ", clauses)
                + " WHERE " + pk.getName() + " = :" + pk.getName();
        return new CompiledQuery(sql, params);
    }
}
