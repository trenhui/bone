package com.bone.metadata.sdk.query.builder;

import com.bone.core.domain.extension.ExtensibleObject;
import com.bone.core.util.ReflectionUtil;
import com.bone.metadata.sdk.query.context.UpsertContext;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.domain.model.ColumnMetadata;

import java.util.*;
import java.util.stream.Collectors;

public class UpsertSqlBuilder implements SqlQueryBuilder<UpsertContext> {

    // —— 1. 支持 SqlQueryBuilder<UpsertContext> —— //

    @Override
    public CompiledQuery build(UpsertContext ctx) {
        return switch (ctx.getDbType()) {
            case MYSQL      -> buildMySql(ctx);
            case POSTGRESQL -> buildPostgres(ctx);
            default         -> throw new UnsupportedOperationException("Unsupported DB: " + ctx.getDbType());
        };
    }

    private CompiledQuery buildMySql(UpsertContext ctx) {
        String table = ctx.getTable().getName();
        Object entity = ctx.getEntity();
        List<ColumnMetadata> cols = ctx.getTable().getColumns();

        String colsSql = cols.stream()
                .map(ColumnMetadata::getName)
                .collect(Collectors.joining(", "));
        String valsSql = cols.stream()
                .map(c -> ":" + c.getName())
                .collect(Collectors.joining(", "));
        String updSql  = cols.stream()
                .filter(c -> !c.isPrimaryKey())
                .map(c -> c.getName() + "=VALUES(" + c.getName() + ")")
                .collect(Collectors.joining(", "));

        String sql = "INSERT INTO " + table
                + " (" + colsSql + ") VALUES (" + valsSql + ")"
                + " ON DUPLICATE KEY UPDATE " + updSql;

        Map<String,Object> params = new LinkedHashMap<>();
        for (var c : cols) {
            params.put(c.getName(), ReflectionUtil.getFieldValue(entity, c.getFieldName()));
        }
        if (entity instanceof ExtensibleObject ext) {
            ext.getExtraProperties()
                    .forEach((k,v) -> params.put("ext_" + k, v));
        }
        return new CompiledQuery(sql, params);
    }

    private CompiledQuery buildPostgres(UpsertContext ctx) {
        String table = ctx.getTable().getName();
        Object entity = ctx.getEntity();
        List<ColumnMetadata> cols = ctx.getTable().getColumns();
        String pk   = ctx.getTable().getPrimaryKey().getName();

        String colsSql    = cols.stream()
                .map(ColumnMetadata::getName)
                .collect(Collectors.joining(", "));
        String valsSql    = cols.stream()
                .map(c -> ":" + c.getName())
                .collect(Collectors.joining(", "));
        String conflictSql= cols.stream()
                .filter(c -> !c.isPrimaryKey())
                .map(c -> c.getName() + "=EXCLUDED." + c.getName())
                .collect(Collectors.joining(", "));

        String sql = "INSERT INTO " + table
                + " (" + colsSql + ") VALUES (" + valsSql + ")"
                + " ON CONFLICT (" + pk + ") DO UPDATE SET " + conflictSql;

        Map<String,Object> params = new LinkedHashMap<>();
        for (var c : cols) {
            params.put(c.getName(), ReflectionUtil.getFieldValue(entity, c.getFieldName()));
        }
        if (entity instanceof ExtensibleObject ext) {
            ext.getExtraProperties()
                    .forEach((k,v) -> params.put("ext_" + k, v));
        }
        return new CompiledQuery(sql, params);
    }
}