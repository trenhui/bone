package com.bone.metadata.sdk.query.builder;

import com.bone.core.domain.extension.ExtensibleObject;
import com.bone.core.util.ReflectionUtil;
import com.bone.metadata.sdk.domain.model.ColumnMetadata;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.query.context.UpsertContext;
import com.bone.metadata.sdk.support.util.SqlUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UpsertBuilder implements SqlQueryBuilder<UpsertContext> {

  // —— 1. 支持 SqlQueryBuilder<UpsertContext> —— //

  @Override
  public CompiledQuery build(UpsertContext ctx) {
    return switch (ctx.getDbType()) {
      case MYSQL -> buildMySql(ctx);
      case POSTGRESQL -> buildPostgres(ctx);
      default -> throw new UnsupportedOperationException("Unsupported DB: " + ctx.getDbType());
    };
  }

  private CompiledQuery buildMySql(UpsertContext ctx) {
    String table = ctx.getTable().getName();
    Object entity = ctx.getEntity();
    List<ColumnMetadata> cols = ctx.getTable().getColumns();
    ColumnMetadata tenantCol = ctx.getTable().getTenantIdColumn();

    String colsSql = cols.stream().map(ColumnMetadata::getName).collect(Collectors.joining(", "));
    String valsSql = cols.stream().map(c -> ":" + c.getName()).collect(Collectors.joining(", "));
    String updSql =
        cols.stream()
            .filter(c -> !c.isPrimaryKey() && !(tenantCol != null && c == tenantCol))
            .map(c -> c.getName() + "=VALUES(" + c.getName() + ")")
            .collect(Collectors.joining(", "));

    String sql =
        "INSERT INTO "
            + table
            + " ("
            + colsSql
            + ") VALUES ("
            + valsSql
            + ")"
            + " ON DUPLICATE KEY UPDATE "
            + updSql;

    Map<String, Object> params = new LinkedHashMap<>();
    for (var c : cols) {
      Object value =
          (tenantCol != null && c == tenantCol)
              ? SqlUtil.toJdbcParameter(
                  TenantFilterInjector.resolveInsertTenantValue(ctx.getTable(), entity))
              : SqlUtil.toJdbcParameter(ReflectionUtil.getFieldValue(entity, c.getFieldName()));
      params.put(c.getName(), value);
    }
    if (entity instanceof ExtensibleObject ext) {
      @SuppressWarnings("unchecked")
      Map<String, Object> extraProps = ext.getExtraProperties();
      extraProps.forEach((k, v) -> params.put("ext_" + k, v));
    }
    return new CompiledQuery(sql, params);
  }

  private CompiledQuery buildPostgres(UpsertContext ctx) {
    String table = ctx.getTable().getName();
    Object entity = ctx.getEntity();
    List<ColumnMetadata> cols = ctx.getTable().getColumns();
    String pk = ctx.getTable().getPrimaryKey().getName();
    ColumnMetadata tenantCol = ctx.getTable().getTenantIdColumn();

    String colsSql = cols.stream().map(ColumnMetadata::getName).collect(Collectors.joining(", "));
    String valsSql = cols.stream().map(c -> ":" + c.getName()).collect(Collectors.joining(", "));
    String conflictSql =
        cols.stream()
            .filter(c -> !c.isPrimaryKey() && !(tenantCol != null && c == tenantCol))
            .map(c -> c.getName() + "=EXCLUDED." + c.getName())
            .collect(Collectors.joining(", "));

    String sql =
        "INSERT INTO "
            + table
            + " ("
            + colsSql
            + ") VALUES ("
            + valsSql
            + ")"
            + " ON CONFLICT ("
            + pk
            + ") DO UPDATE SET "
            + conflictSql;

    Map<String, Object> params = new LinkedHashMap<>();
    for (var c : cols) {
      Object value =
          (tenantCol != null && c == tenantCol)
              ? SqlUtil.toJdbcParameter(
                  TenantFilterInjector.resolveInsertTenantValue(ctx.getTable(), entity))
              : SqlUtil.toJdbcParameter(ReflectionUtil.getFieldValue(entity, c.getFieldName()));
      params.put(c.getName(), value);
    }
    if (entity instanceof ExtensibleObject ext) {
      @SuppressWarnings("unchecked")
      Map<String, Object> extraProps = ext.getExtraProperties();
      extraProps.forEach((k, v) -> params.put("ext_" + k, v));
    }
    return new CompiledQuery(sql, params);
  }
}
