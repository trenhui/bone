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

  /** 乐观锁旧值参数名（ADR-0031 D1）：与安全上下文互不冲突。 */
  public static final String VERSION_PARAM = "__bone_version_old__";

  @Override
  public CompiledQuery build(DynamicUpdateContext ctx) {
    TableMetadata table = ctx.getTable();
    Object e = ctx.getEntity();
    ColumnMetadata pk = table.getPrimaryKey();

    Map<String, Object> params = new LinkedHashMap<>();
    List<String> clauses = new ArrayList<>();

    for (ColumnMetadata c : table.getColumns()) {
      if (c.isPrimaryKey()) continue;
      // 版本列不在 SET 里逐字段赋值：它由 SDK 统一自增（version = version + 1），见下方 isVersioned 分支
      if (c.isVersion()) continue;
      // 租户归属列（tenant_id）仅写入一次：INSERT 时从可信 TenantContext 取值（见 BatchInsertBuilder），
      // 之后不可更新——既契合 ADR-0029 单租户不变量，也关闭"实体字段被填错值→UPDATE 改写行归属→跨租户破坏"的向量。
      // WHERE 租户护栏仍由可信上下文注入，与 SET 排除互不冲突。
      if (table.isTenantScoped() && c == table.getTenantIdColumn()) continue;
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

    // 原生乐观锁（ADR-0031 D1）：SET version = version + 1，WHERE 携带实体加载时的旧值。
    // 未标注 @Version 的表不受影响（零向后兼容风险）。
    if (table.isVersioned()) {
      ColumnMetadata vc = table.getVersion();
      clauses.add(vc.getName() + " = " + vc.getName() + " + 1");
      Object oldVersion =
          SqlUtil.toJdbcParameter(ReflectionUtil.getFieldValue(e, vc.getFieldName()));
      whereSql.append(" AND ").append(vc.getName()).append(" = :").append(VERSION_PARAM);
      params.put(VERSION_PARAM, oldVersion);
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
