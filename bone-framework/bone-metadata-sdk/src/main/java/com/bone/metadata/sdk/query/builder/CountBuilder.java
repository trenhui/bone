package com.bone.metadata.sdk.query.builder;

import com.bone.metadata.sdk.query.criteria.Condition;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.query.context.CountContext;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 动态构建 COUNT 查询，支持按需 JOIN 扩展表 ext_data_reserved。
 */
public class CountBuilder implements QueryBuilder<CountContext> {

    private final MetadataService metadataService;

    public CountBuilder(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Override
    public CompiledQuery build(CountContext ctx) {
        TableMetadata tbl = ctx.getTable();
        Criteria<?> c = ctx.getCriteria();
        AllocationContext extCtx = ctx.getExtContext();

        Map<String, Object> params = new LinkedHashMap<>(c.getParameters());
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(tbl.getName()).append(" m");

        Map<String, FieldMetadata> logicalToMeta = Collections.emptyMap();
        if (c.requiresExtJoin()) {
            // 获取扩展字段元数据
            List<String> logicals = c.getExtConditions().stream()
                    .map(Condition::getColumn)
                    .distinct()
                    .toList();

            List<FieldMetadata> metas = metadataService.findExtensionFieldsByNames(extCtx, logicals);
            logicalToMeta = metas.stream()
                    .collect(Collectors.toMap(FieldMetadata::getName, m -> m));

            // 构建 JOIN 子句
            sql.append(" LEFT JOIN ext_data_reserved ext")
                    .append(" ON ext.entity_id = m.id")
                    .append(" AND ext.tenant_id = :ext_tenant_id")
                    .append(" AND ext.app_code = :ext_app_code")
                    .append(" AND ext.entity_type = :ext_entity_type")
                    .append(" AND ext.deleted = false");

            // 绑定 JOIN 参数
            params.put("ext_tenant_id", extCtx.getTenantId());
            params.put("ext_app_code", extCtx.getAppCode());
            params.put("ext_entity_type", extCtx.getEntityType());
        }

        // 构建 WHERE 条件
        List<String> where = new ArrayList<>();

        // 主表条件
//        for (Condition cond : c.getMainConditions()) {
//            String col = cond.getColumn();
//            String op = cond.getOperator().getSymbol();
//            where.add("m." + col + " " + op + " :" + col);
//        }
        where.addAll(c.getMainConditions().stream()
                .map(Condition::toSql)
                .toList()
        );

        // 扩展表条件
        if (c.requiresExtJoin()) {
            for (Condition cond : c.getExtConditions()) {
                FieldMetadata meta = logicalToMeta.get(cond.getColumn());
                if (meta == null) {
                    throw new IllegalArgumentException("Unknown extension field: " + cond.getColumn());
                }
                String physCol = meta.getColumnName();
                String op = cond.getOperator().getSymbol();
                String param = cond.getParamName();
                where.add("ext." + physCol + " " + op + " :" + param);
            }
        }

        // 软删除控制
        if (tbl.isSoftDeletable() && !ctx.isIncludeDeleted()) {
            where.add("m.deleted = false");
        }

        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", where));
        }

        return new CompiledQuery(sql.toString(), params);
    }
}