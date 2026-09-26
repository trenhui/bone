package com.bone.metadata.sdk.query.builder;

import com.bone.core.util.ReflectionUtil;
import com.bone.metadata.sdk.domain.model.ColumnMetadata;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.query.context.ConditionalUpdateContext;
import com.bone.metadata.sdk.query.criteria.Condition;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.support.util.SqlUtil;
import java.util.*;

/** 动态构建带条件的 UPDATE（支持实体字段和扩展字段）。 */
public class ConditionalUpdateBuilder implements SqlQueryBuilder<ConditionalUpdateContext> {

  @Override
  public CompiledQuery build(ConditionalUpdateContext ctx) {
    // 1. 准备元数据和上下文
    TableMetadata table = Objects.requireNonNull(ctx.getTable(), "TableMetadata 不能为空");
    Object entity = Objects.requireNonNull(ctx.getEntity(), "更新实体不能为空");
    Criteria<?> crit = Objects.requireNonNull(ctx.getCriteria(), "更新条件不能为空");

    // 2. 构造 SET 子句和参数
    List<String> setClauses = new ArrayList<>();
    Map<String, Object> params = new LinkedHashMap<>();

    // 2.1 主表普通列
    for (ColumnMetadata col : table.getColumns()) {
      if (col.isPrimaryKey()) continue;
      // version 列由 SDK 原生乐观锁独占管理（DynamicUpdateBuilder）：条件更新不 bump、不护栏，
      // 此处直接跳过，避免把它当普通列覆盖（ADR-0031 D1.6：条件/批量路径不支持乐观锁）
      if (col.isVersion()) continue;
      // 租户归属列（tenant_id）仅写入一次（INSERT 取可信上下文），更新路径不可写（见 DynamicUpdateBuilder 同款护栏）
      if (table.isTenantScoped() && col == table.getTenantIdColumn()) continue;
      Object value =
          SqlUtil.toJdbcParameter(ReflectionUtil.getFieldValue(entity, col.getFieldName()));
      if (value != null) {
        setClauses.add("m." + col.getName() + " = :" + col.getName());
        params.put(col.getName(), value);
      }
    }

    if (setClauses.isEmpty()) {
      throw new IllegalArgumentException("没有任何可更新的字段");
    }

    // 3. 构造 WHERE 子句和参数
    List<String> whereClauses = new ArrayList<>();
    for (Condition cond : crit.getMainConditions()) {
      String col = cond.getColumn();
      String op = cond.getOperator().getSymbol();
      whereClauses.add("m." + col + " " + op + " :" + cond.getParamName());
    }

    // 3.1 租户过滤（可信源 TenantContext；ADR-0029）——须在"缺少更新条件"校验前注入
    TenantFilterInjector.inject(whereClauses, params, table, crit, true);
    // 不支持扩展表条件
    if (!crit.getExtConditions().isEmpty()) {
      throw new UnsupportedOperationException("ConditionalUpdate 不支持扩展表条件");
    }
    // 把 Criteria 参数合并
    params.putAll(crit.getParameters());

    if (whereClauses.isEmpty()) {
      throw new IllegalArgumentException("缺少更新条件，避免全表更新");
    }

    // 4. 拼装最终 SQL
    StringBuilder sql =
        new StringBuilder()
            .append("UPDATE ")
            .append(table.getName())
            .append(" m")
            .append(" SET ")
            .append(String.join(", ", setClauses))
            .append(" WHERE ")
            .append(String.join(" AND ", whereClauses));

    return new CompiledQuery(sql.toString(), params);
  }
}
