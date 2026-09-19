package com.bone.metadata.sdk.query.builder;

import com.bone.core.domain.extension.ExtensibleObject;
import com.bone.core.tenant.context.TenantContext;
import com.bone.core.util.ReflectionUtil;
import com.bone.metadata.sdk.domain.exception.MissingTenantContextException;
import com.bone.metadata.sdk.domain.model.ColumnMetadata;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.query.context.DynamicUpdateContext;
import com.bone.metadata.sdk.support.util.SqlUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DynamicUpdateBuilder implements SqlQueryBuilder<DynamicUpdateContext> {
  @Override
  public CompiledQuery build(DynamicUpdateContext ctx) {
    TableMetadata table = ctx.getTable();
    Object e = ctx.getEntity();
    ColumnMetadata pk = table.getPrimaryKey();

    Map<String, Object> params = new LinkedHashMap<>();
    List<String> clauses = new ArrayList<>();

    for (ColumnMetadata c : table.getColumns()) {
      if (c.isPrimaryKey()) continue;
      Object v = SqlUtil.toJdbcParameter(ReflectionUtil.getFieldValue(e, c.getFieldName()));
      clauses.add(c.getName() + " = :" + c.getName());
      params.put(c.getName(), v);
    }
    if (e instanceof ExtensibleObject ext) {
      @SuppressWarnings("unchecked")
      Map<String, Object> extraProps = ext.getExtraProperties();
      extraProps.forEach(
          (k, v) -> {
            clauses.add("ext_" + k + " = :ext_" + k);
            params.put("ext_" + k, v);
          });
    }

    // WHERE：主键 + 可信租户过滤（ADR-0029）
    StringBuilder whereSql = new StringBuilder(pk.getName()).append(" = :").append(pk.getName());
    if (table.isTenantScoped()) {
      Long tid = TenantContext.getTenantIdAsLong();
      if (tid == null) {
        throw new MissingTenantContextException(table.getName());
      }
      whereSql
          .append(" AND ")
          .append(table.getTenantIdColumn().getName())
          .append(" = :")
          .append(TenantFilterInjector.PARAM);
      params.put(TenantFilterInjector.PARAM, tid);
    }

    params.put(
        pk.getName(), SqlUtil.toJdbcParameter(ReflectionUtil.getFieldValue(e, pk.getFieldName())));
    if (clauses.isEmpty()) {
      throw new IllegalStateException("没有可更新字段");
    }
    String sql =
        "UPDATE " + table.getName() + " SET " + String.join(", ", clauses) + " WHERE " + whereSql;
    return new CompiledQuery(sql, params);
  }
}
