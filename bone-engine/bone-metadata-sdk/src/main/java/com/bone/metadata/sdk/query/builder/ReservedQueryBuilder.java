package com.bone.metadata.sdk.query.builder;

import com.bone.metadata.sdk.domain.enums.DatabaseType;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.extension.ExtensionContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public final class ReservedQueryBuilder {
    private static final Cache<String, String> SELECT_CACHE = Caffeine.newBuilder()
            .maximumSize(2048)
            .expireAfterWrite(Duration.ofHours(4))
            .recordStats()
            .build();

    private static final Cache<String, String> UPSERT_CACHE = Caffeine.newBuilder()
            .maximumSize(2048)
            .expireAfterWrite(Duration.ofHours(4))
            .recordStats()
            .build();

    private ReservedQueryBuilder() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    public static String buildSelectSQL(ExtensionContext ctx, List<FieldMetadata> fields) {
        String cacheKey = buildCacheKey(ctx, "SELECT", fields);
        return SELECT_CACHE.get(cacheKey, k -> generateSelectSQL(fields));
    }

    public static String buildUpsertSQL(ExtensionContext ctx, List<FieldMetadata> fields, DatabaseType dbType) {
        String cacheKey = buildCacheKey(ctx, "UPSERT", fields) + "|" + dbType;
        return UPSERT_CACHE.get(cacheKey, k -> generateUpsertSQL(fields, dbType));
    }

    private static String buildCacheKey(ExtensionContext ctx, String type, List<FieldMetadata> fields) {
        return ctx.getTenantId() + "|" + ctx.getAppCode() + "|" +
                ctx.getBizIdentityCode() + "|" + ctx.getEntityType() + "|" +
                type + "|" + getSortedColumnNames(fields);
    }

    private static String generateSelectSQL(List<FieldMetadata> fields) {
        String columns = fields.stream()
                .map(FieldMetadata::getColumnName)
                .sorted()
                .collect(Collectors.joining(", "));
        return "SELECT " + columns + " FROM ext_data_reserved WHERE " +
                "tenant_id = :tenant_id AND app_code = :app_code AND " +
                "biz_identity_code = :biz_identity_code AND entity_type = :entity_type " +
                "AND entity_id = :entity_id";
    }

    private static String generateUpsertSQL(List<FieldMetadata> fields, DatabaseType dbType) {
        List<String> keyColumns = Arrays.asList("tenant_id", "app_code", "biz_identity_code", "entity_type", "entity_id");
        // Use LinkedHashSet to prevent duplicates
        Set<String> allColumnsSet = new LinkedHashSet<>(keyColumns);
        fields.stream().map(FieldMetadata::getColumnName).forEach(allColumnsSet::add);
        List<String> allColumns = new ArrayList<>(allColumnsSet);
        // 新增调试日志
        log.debug("Generating UPSERT SQL for {} with columns: {}", dbType, allColumns);
        log.debug("Key columns: {}", keyColumns);
        log.debug("All columns count: {}", allColumns.size());
        log.debug("Generating UPSERT SQL with {} columns: {}", allColumns.size(), allColumns);

        String sql = switch (dbType) {
            case MYSQL -> buildMySQLUpsert(allColumns, keyColumns);
            case POSTGRESQL -> buildPostgresUpsert(allColumns, keyColumns);
            case ORACLE -> buildOracleUpsert(allColumns, keyColumns);
            case H2 -> buildH2Upsert(allColumns, keyColumns);
            default -> throw new UnsupportedOperationException("Unsupported database: " + dbType);
        };

        log.info("Generated SQL: {}", sql);
        return sql;
    }

    private static String buildMySQLUpsert(List<String> columns, List<String> keys) {
        String updates = columns.stream()
                .filter(c -> !keys.contains(c))
                .map(c -> c + " = :" + c)
                .collect(Collectors.joining(", "));
        return String.format(
                "INSERT INTO ext_data_reserved (%s) VALUES (%s) ON DUPLICATE KEY UPDATE %s",
                String.join(", ", columns),
                getNamedParameters(columns),
                updates);
    }

    private static String buildPostgresUpsert(List<String> columns, List<String> keys) {
        String updates = columns.stream()
                .filter(c -> !keys.contains(c))
                .map(c -> c + " = EXCLUDED." + c)
                .collect(Collectors.joining(", "));
        return String.format(
                "INSERT INTO ext_data_reserved (%s) VALUES (%s) ON CONFLICT (%s) DO UPDATE SET %s",
                String.join(", ", columns),
                getNamedParameters(columns),
                String.join(", ", keys),
                updates);
    }

    /** H2 单语句 MERGE（避免分号触发 SqlSecurityGuard） */
    private static String buildH2Upsert(List<String> columns, List<String> keys) {
        return String.format(
                "MERGE INTO ext_data_reserved (%s) KEY (%s) VALUES (%s)",
                String.join(", ", columns),
                String.join(", ", keys),
                getNamedParameters(columns));
    }



    private static String buildOracleUpsert(List<String> columns, List<String> keys) {
        String selectColumns = columns.stream()
                .map(c -> ":" + c + " AS " + c)
                .collect(Collectors.joining(", "));
        String usingClause = "USING (SELECT " + selectColumns + " FROM DUAL) s";
        String onClause = keys.stream()
                .map(k -> "t." + k + " = s." + k)
                .collect(Collectors.joining(" AND "));
        String updates = columns.stream()
                .filter(c -> !keys.contains(c))
                .map(c -> "t." + c + " = s." + c)
                .collect(Collectors.joining(", "));
        String insertColumns = String.join(", ", columns);
        String insertValues = columns.stream().map(c -> "s." + c).collect(Collectors.joining(", "));
        return "MERGE INTO ext_data_reserved t " + usingClause + " ON (" + onClause + ") " +
                "WHEN MATCHED THEN UPDATE SET " + updates + " " +
                "WHEN NOT MATCHED THEN INSERT (" + insertColumns + ") VALUES (" + insertValues + ")";
    }

    private static String getSortedColumnNames(List<FieldMetadata> fields) {
        return fields.stream()
                .map(FieldMetadata::getColumnName)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static String getNamedParameters(List<String> columns) {
        return columns.stream().map(c -> ":" + c).collect(Collectors.joining(", "));
    }
}