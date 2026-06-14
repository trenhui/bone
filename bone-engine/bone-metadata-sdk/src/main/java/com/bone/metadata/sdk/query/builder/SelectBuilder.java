package com.bone.metadata.sdk.query.builder;

import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.query.context.SelectContext;
import com.bone.metadata.sdk.query.criteria.Condition;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.sql.dialect.DatabaseDialect;
import java.util.*;
import java.util.stream.Collectors;

/** 动态构建 SELECT，按需 LEFT JOIN ext_data_reserved， 并且扩展字段在 WHERE 中也使用真实物理列名。 */
public class SelectBuilder implements SqlQueryBuilder<SelectContext> {

  private final MetadataService metadataService;
  private final DatabaseDialect dialect;

  public SelectBuilder(MetadataService metadataService, DatabaseDialect dialect) {
    this.metadataService = metadataService;
    this.dialect = dialect;
  }

  @Override
  public CompiledQuery build(SelectContext ctx) {
    TableMetadata tbl = ctx.getTable();
    Criteria<?> c = ctx.getCriteria();
    AllocationContext extCtx = ctx.getExtContext();

    // 拷贝用户传入的参数
    Map<String, Object> params = new LinkedHashMap<>(c.getParameters());

    StringBuilder sql = new StringBuilder("SELECT ");

    // 1) 主表列列表
    String mainCols =
        tbl.getColumns().stream()
            .map(col -> "m." + col.getName())
            .collect(Collectors.joining(", "));
    sql.append(mainCols);

    // 2) 扩展表列列表
    Map<String, FieldMetadata> logicalToMeta = Collections.emptyMap();
    if (c.requiresExtJoin()) {
      List<String> logicals =
          c.getExtConditions().stream().map(Condition::getColumn).distinct().toList();

      List<FieldMetadata> metas = metadataService.findExtensionFieldsByNames(extCtx, logicals);
      logicalToMeta = metas.stream().collect(Collectors.toMap(FieldMetadata::getName, m -> m));

      String extCols =
          metas.stream()
              .map(m -> "ext." + m.getColumnName() + " AS " + m.getName())
              .collect(Collectors.joining(", "));
      sql.append(", ").append(extCols);
    }

    // 3) FROM + optional JOIN
    sql.append(" FROM ").append(tbl.getName()).append(" m");
    if (c.requiresExtJoin()) {
      // 绑定 JOIN 用到的参数
      params.put("ext_tenant_id", extCtx.getTenantId());
      params.put("ext_app_code", extCtx.getAppCode());
      params.put("ext_entity_type", extCtx.getEntityType());

      sql.append(" LEFT JOIN ext_data_reserved ext")
          .append(" ON ext.entity_id = m.id")
          .append(" AND ext.tenant_id = :ext_tenant_id")
          .append(" AND ext.app_code = :ext_app_code")
          .append(" AND ext.entity_type = :ext_entity_type")
          .append(" AND ext.deleted = false");
    }

    // 4) WHERE 子句：分开主表和扩展表
    List<String> where = new ArrayList<>();

    // 4.1 主表条件（Condition.toSql() 本身输出 “column OP :column”）
    where.addAll(c.getMainConditions().stream().map(Condition::toSql).toList());

    // 4.2 扩展表条件：用物理列名替换逻辑名
    if (c.requiresExtJoin()) {
      for (Condition cond : c.getExtConditions()) {
        FieldMetadata meta = logicalToMeta.get(cond.getColumn());
        // 添加空值检查
        if (meta == null) {
          throw new IllegalStateException(
              "扩展字段 '"
                  + cond.getColumn()
                  + "' 的元数据未找到。"
                  + "可用字段: "
                  + logicalToMeta.keySet()
                  + "，查询字段: "
                  + logicalToMeta);
        }
        String phys = meta.getColumnName();
        String op = cond.getOperator().getSymbol();
        // 参数名仍然是 logical snake_case
        String param = cond.getParamName();
        where.add("ext." + phys + " " + op + " :" + param);
      }
    }
    // 4.3 软删除
    if (tbl.isSoftDeletable() && !ctx.isIncludeDeleted()) {
      where.add("m.deleted = false");
    }

    if (!where.isEmpty()) {
      sql.append(" WHERE ").append(String.join(" AND ", where));
    }

    // 5) ORDER BY
    if (!c.getSortItems().isEmpty()) {
      sql.append(" ORDER BY ").append(String.join(", ", c.getSortItems()));
    }

    // 6) 分页
    String pageSql = dialect.buildPagination(c.getPageSize(), c.getOffset());
    if (!pageSql.isBlank()) {
      sql.append(" ").append(pageSql);
    }

    return new CompiledQuery(sql.toString(), params);
  }
}
