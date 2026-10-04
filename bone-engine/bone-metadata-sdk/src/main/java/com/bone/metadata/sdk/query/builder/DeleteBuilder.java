package com.bone.metadata.sdk.query.builder;

import com.bone.core.tenant.context.TenantContext;
import com.bone.metadata.sdk.domain.exception.MissingTenantContextException;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.domain.query.CompositeQuery;
import com.bone.metadata.sdk.query.context.DeleteContext;
import com.bone.metadata.sdk.query.criteria.Condition;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.*;

public class DeleteBuilder implements SqlQueryBuilder<DeleteContext> {

  @Override
  public CompiledQuery build(DeleteContext ctx) {
    TableMetadata table = ctx.getTable();
    Criteria<?> criteria = ctx.getCriteria();
    AllocationContext ext = ctx.getExtContext();

    // 1. 主表参数
    Map<String, Object> mainParams = new LinkedHashMap<>(criteria.getParameters());

    // 2. 扩展表参数（包含主表的 id 参数）
    Map<String, Object> extParams = new LinkedHashMap<>();
    if (ext != null) {
      extParams.put("ext_tenant_id", ext.getTenantId());
      extParams.put("ext_app_code", ext.getAppCode());
      extParams.put("ext_biz_identity_code", ext.getBizIdentityCode());
      extParams.put("ext_entity_type", ext.getEntityType());
      extParams.putAll(mainParams); // 继承主表参数（如 :id）
    }

    // 3. 构造 WHERE 片段（移除 SELECT 查询中的表别名前缀 m. / ext.）
    String rawWhere = criteria.whereSql().trim();
    // DELETE/UPDATE 语句不使用表别名，需要移除 Condition.toSql() 生成的 m. / ext. 前缀
    String where = rawWhere.replaceAll("(?i)\\bm\\.", "").replaceAll("(?i)\\bext\\.", "");
    where = rawWhere.toUpperCase().startsWith("WHERE") ? where : "WHERE " + where;

    // 3.0 空条件护栏（2026-10-04 补齐，对齐同 SDK 的 ConditionalUpdateBuilder:64-66）：
    // 条件为空时 `where` 恒为 "WHERE"，旧实现会走进 :49 的
    // `where.strip().equalsIgnoreCase("WHERE")` 分支拼出 `WHERE tenant_id = :tid`，
    // 语义变成「删除当前租户的全部数据」—— 比全表删除只差一个租户维度，实质等价。
    // 同 SDK 内 ConditionalUpdateBuilder 早有对称护栏（「缺少更新条件，避免全表更新」），
    // 两条写/删路径护栏不对称，会让后来者按更新侧的经验推断删除侧也安全而踩坑。
    // 判据取 main conditions 而非 whereSql 字符串：前者是结构化、确定性的，
    // 后者要等拼完串才能判，且被 whereSql 的别名剥离逻辑二次加工过。
    if (criteria.getMainConditions().isEmpty()) {
      throw new IllegalArgumentException("缺少删除条件，避免全表删除或清空当前租户数据");
    }

    // 3.1 租户过滤（可信源 TenantContext；ADR-0029）
    if (table.isTenantScoped() && !criteria.isTenantFilterDisabled()) {
      Long tid = TenantContext.getTenantIdAsLong();
      if (tid == null) {
        throw new MissingTenantContextException(table.getName());
      }
      String tenantClause =
          table.getTenantIdColumn().getName() + " = :" + TenantFilterInjector.PARAM;
      // 空条件已在 3.0 被拒，这里不可能出现 "WHERE" 裸串，直接 AND 拼接即可
      // （保留不可达分支只会让人误判"空条件是被这里兜住的"）。
      where = where + " AND " + tenantClause;
      mainParams.put(TenantFilterInjector.PARAM, tid);
    }

    // 4. 按顺序收集 SQL 片段
    List<CompiledQuery> segments = new ArrayList<>();

    // 4a. 扩展表清理（修正：使用实际列名 id）
    if (ext != null) {
      String idParamName = null;
      for (Condition condition : criteria.getMainConditions()) {
        if ("id".equals(condition.getColumn())) { // 假设主键列名为id
          idParamName = condition.getParamName();
          break;
        }
      }
      // 仅当 criteria 包含 id 条件时才清理扩展表，否则跳过（如按 accountId 删除关联记录）
      if (idParamName != null) {
        Object idValue = mainParams.get(idParamName);
        boolean multi = idValue instanceof Collection<?>;
        String idCond =
            multi ? "entity_id IN (:" + idParamName + ")" : "entity_id = (:" + idParamName + ")";

        String op =
            table.isSoftDeletable()
                ? "UPDATE ext_data_reserved SET deleted = true"
                : "DELETE FROM ext_data_reserved";

        String extSql =
            op
                + " WHERE tenant_id = :ext_tenant_id"
                + "   AND app_code = :ext_app_code"
                + "   AND biz_identity_code = :ext_biz_identity_code"
                + "   AND entity_type = :ext_entity_type"
                + "   AND "
                + idCond; // 直接使用 entity_id（扩展表的主键列）

        segments.add(new CompiledQuery(extSql, extParams));
      }
    }

    // 4b. 主表删除／软删除（修正：直接使用实际列名 id）
    String mainSql;
    if (table.isSoftDeletable()) {
      mainSql = "UPDATE " + table.getName() + " SET deleted = true " + where; // WHERE 已修正为实际列名
    } else {
      mainSql = "DELETE FROM " + table.getName() + " " + where; // WHERE 已修正为实际列名
    }
    segments.add(new CompiledQuery(mainSql, mainParams));

    // 5. 返回组合查询
    return new CompositeQuery(segments);
  }
}
