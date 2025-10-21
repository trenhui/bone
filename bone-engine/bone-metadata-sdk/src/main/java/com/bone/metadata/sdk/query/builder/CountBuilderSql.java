package com.bone.metadata.sdk.query.builder;

import com.bone.metadata.sdk.query.criteria.Condition;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.query.context.CountContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * 动态构建 COUNT 查询，支持按需 JOIN 扩展表 ext_data_reserved。
 */
public class CountBuilderSql implements SqlQueryBuilder<CountContext> {
    private static final Logger log = LoggerFactory.getLogger(CountBuilderSql.class);

    private final MetadataService metadataService;

    public CountBuilderSql(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Override
    public CompiledQuery build(CountContext ctx) {
        // 使用getter方法访问字段
        TableMetadata tbl = ctx.getTable();
        Criteria<?> c = ctx.getCriteria();
        AllocationContext extCtx = ctx.getExtContext();

        Map<String, Object> params = new LinkedHashMap<>(c.getParameters());
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(tbl.getName()).append(" m");

        Map<String, FieldMetadata> logicalToMeta = Collections.emptyMap();
        boolean requiresExtJoin = c.requiresExtJoin();

        if (requiresExtJoin) {
            // 获取扩展字段元数据
            List<String> logicals = c.getExtConditions().stream()
                    .map(Condition::getColumn)
                    .distinct()
                    .toList();

            List<FieldMetadata> metas = metadataService.findExtensionFieldsByNames(extCtx, logicals);
            logicalToMeta = new HashMap<String, FieldMetadata>();
            for (FieldMetadata m : metas) {
                try {
                    // 使用反射访问私有字段
                    java.lang.reflect.Field nameField = FieldMetadata.class.getDeclaredField("name");
                    nameField.setAccessible(true);
                    String name = (String) nameField.get(m);
                    logicalToMeta.put(name, m);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to access name field", e);
                }
            }

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

        // 主表条件 - 使用优化后的方式
        where.addAll(buildMainTableConditions(c));

        // 扩展表条件
        if (requiresExtJoin) {
            where.addAll(buildExtensionTableConditions(c, logicalToMeta));
        }

        // 软删除控制 - 修复的关键部分
        if (shouldApplySoftDeleteFilter(tbl, ctx)) {
            where.add(buildSoftDeleteCondition(tbl));
        }

        // 组装完整的 SQL
        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", where));
        }

        return new CompiledQuery(sql.toString(), params);
    }

    /**
     * 构建主表查询条件
     */
    private List<String> buildMainTableConditions(Criteria<?> criteria) {
        return criteria.getMainConditions().stream()
                .map(condition -> condition.toSql())
                .collect(Collectors.toList());
    }

    /**
     * 构建扩展表查询条件
     */
    private List<String> buildExtensionTableConditions(Criteria<?> criteria,
                                                       Map<String, FieldMetadata> logicalToMeta) {
        return criteria.getExtConditions().stream()
                .map(condition -> {
                    FieldMetadata meta = logicalToMeta.get(condition.getColumn());
                    if (meta == null) {
                        throw new IllegalArgumentException("Unknown extension field: " + condition.getColumn());
                    }
                    try {
                        // 使用反射访问私有字段
                        java.lang.reflect.Field columnNameField = FieldMetadata.class.getDeclaredField("columnName");
                        columnNameField.setAccessible(true);
                        String columnName = (String) columnNameField.get(meta);
                        return "ext." + columnName + " " + condition.getOperator().getSymbol() + " :" + condition.getParamName();
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to access columnName field", e);
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * 判断是否需要应用软删除过滤
     */
    private boolean shouldApplySoftDeleteFilter(TableMetadata table, CountContext context) {
        return table.isSoftDeletable() && !context.getIncludeDeleted();
    }

    /**
     * 构建软删除条件
     */
    private String buildSoftDeleteCondition(TableMetadata table) {
        // 根据软删除列的实际类型构建条件
        String softDeleteColumn = table.getSoftDeleteColumn().getName();
        Class<?> columnType = table.getSoftDeleteColumn().getType();

        if (columnType == Boolean.class || columnType == boolean.class) {
            return "m." + softDeleteColumn + " = false";
        } else if (columnType == Integer.class || columnType == int.class) {
            return "m." + softDeleteColumn + " = 0";
        } else {
            // 字符串或其他类型
            return "m." + softDeleteColumn + " = '0'";
        }
    }

    /**
     * 调试方法：打印生成的SQL和参数（可选）
     */
    public void debugSql(CompiledQuery query) {
        if (log.isDebugEnabled()) {
            log.debug("Generated COUNT SQL: {}", query.getSql());
            log.debug("COUNT Parameters: {}", query.getParameters());
        }
    }
}