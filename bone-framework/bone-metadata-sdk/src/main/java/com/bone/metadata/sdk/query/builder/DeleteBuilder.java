package com.bone.metadata.sdk.query.builder;

import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.domain.query.CompositeQuery;
import com.bone.metadata.sdk.query.context.DeleteContext;
import com.bone.metadata.sdk.query.criteria.Condition;
import com.bone.metadata.sdk.query.criteria.Criteria;

import java.util.*;

public class DeleteBuilder implements QueryBuilder<DeleteContext> {

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

        // 3. 构造 WHERE 片段（修正：直接使用表的实际列名）
        String rawWhere = criteria.whereSql().trim();
        // 关键修复：如果 WHERE 子句包含别名（如 m.id），替换为实际列名（如 id）
        String where = rawWhere.replaceAll("(?i)m\\.id", "id"); // 忽略大小写替换 m.id 为 id
        where = rawWhere.toUpperCase().startsWith("WHERE")
                ? where
                : "WHERE " + where;

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
            if (idParamName == null) {
                throw new IllegalArgumentException("Delete criteria must contain 'id' condition");
            }

            Object idValue = mainParams.get(idParamName);
            boolean multi = idValue instanceof Collection<?>;
            String idCond = multi ? "entity_id IN (:" + idParamName + ")" : "entity_id = (:" + idParamName + ")";

            String op = table.isSoftDeletable()
                    ? "UPDATE ext_data_reserved SET deleted = true"
                    : "DELETE FROM ext_data_reserved";

            String extSql = op
                    + " WHERE tenant_id = :ext_tenant_id"
                    + "   AND app_code = :ext_app_code"
                    + "   AND biz_identity_code = :ext_biz_identity_code"
                    + "   AND entity_type = :ext_entity_type"
                    + "   AND " + idCond; // 直接使用 entity_id（扩展表的主键列）

            segments.add(new CompiledQuery(extSql, extParams));
        }

        // 4b. 主表删除／软删除（修正：直接使用实际列名 id）
        String mainSql;
        if (table.isSoftDeletable()) {
            mainSql = "UPDATE " + table.getName()
                    + " SET deleted = true "
                    + where; // WHERE 已修正为实际列名
        } else {
            mainSql = "DELETE FROM " + table.getName()
                    + " " + where; // WHERE 已修正为实际列名
        }
        segments.add(new CompiledQuery(mainSql, mainParams));

        // 5. 返回组合查询
        return new CompositeQuery(segments);
    }
}