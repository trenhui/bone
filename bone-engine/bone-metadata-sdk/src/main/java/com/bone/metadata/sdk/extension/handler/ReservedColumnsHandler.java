package com.bone.metadata.sdk.extension.handler;

import com.bone.metadata.sdk.domain.enums.DataType;
import com.bone.metadata.sdk.domain.enums.ExtensionMode;
import com.bone.metadata.sdk.domain.exception.FieldAllocationException;
import com.bone.metadata.sdk.domain.exception.MetadataException;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.extension.ExtensionContext;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.query.builder.ReservedQueryBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.metadata.sdk.sql.executor.TypeConverter;
import com.bone.metadata.sdk.support.config.MetadataSdkContext;
import com.bone.metadata.sdk.support.util.DistributedLockUtil;
import com.bone.metadata.sdk.support.util.TypeDetector;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class ReservedColumnsHandler implements ExtensionStorageHandler {
    // Configuration constants
    private static final String LOCK_KEY_PREFIX = "meta:reserved:";
    private static final int LOCK_ACQUIRE_TIMEOUT = 3;
    private static final int LOCK_LEASE_TIME = 10;
    private static final int TEXT_LENGTH_THRESHOLD = 3000;

    private final SqlExecutor sqlExecutor;
    private final MetadataService metadataService;
    private final DistributedLockUtil distributedLockUtil;


    public ReservedColumnsHandler(SqlExecutor sqlExecutor, MetadataService metadataService, DistributedLockUtil distributedLockUtil) {
        this.sqlExecutor = sqlExecutor;
        this.metadataService = metadataService;
        this.distributedLockUtil = distributedLockUtil;
    }

    @Override
    public ExtensionMode getMode() {
        return ExtensionMode.RESERVED_COLUMNS;
    }

    @Override
    public void save(ExtensionContext ctx) {
        ensureValidContext(ctx);
        String dimKey = getDimensionKey(ctx);
        String lockKey = LOCK_KEY_PREFIX + dimKey;

        try {
            // 使用 DistributedLockUtil 获取锁并执行业务逻辑
            distributedLockUtil.executeWithLock(
                    lockKey,
                    LOCK_ACQUIRE_TIMEOUT,
                    LOCK_LEASE_TIME,
                    TimeUnit.SECONDS,
                    () -> {
                        List<FieldMetadata> fields = loadOrCreateFields(ctx);
                        String sql = ReservedQueryBuilder.buildUpsertSQL(ctx, fields, MetadataSdkContext.getDatabaseType());
                        sqlExecutor.update(new CompiledQuery(sql, buildUpsertParams(ctx, fields)));
                        return null;
                    }
            );
        } catch (FieldAllocationException e) {
            throw new FieldAllocationException("Failed to save fields for " + dimKey, e);
        } catch (Exception e) {
            throw new MetadataException("Failed to save fields for " + dimKey, e);
        }
    }

    @Override
    public Map<String, Object> load(ExtensionContext ctx) {
        List<FieldMetadata> fields = metadataService.findExtensionFields(AllocationContext.from(ctx));
        if (fields.isEmpty()) {
            return Collections.emptyMap();
        }

        CompiledQuery cq = new CompiledQuery(ReservedQueryBuilder.buildSelectSQL(ctx, fields), buildSelectParams(ctx));
        List<Map<String, Object>> rows = sqlExecutor.queryForMap(cq);
        if (rows.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> row = rows.get(0);
        Map<String, Object> result = new LinkedHashMap<>(fields.size());
        fields.forEach(field -> result.put(field.getName(), convertFieldValue(row.get(field.getColumnName()), field.getDataType())));
        return result;
    }

    private List<FieldMetadata> loadOrCreateFields(ExtensionContext ctx) {
        Set<String> names = ctx.getExtraProperties().keySet();
        List<FieldMetadata> existing = metadataService.findExtensionFieldsByNames(AllocationContext.from(ctx), new ArrayList<>(names));
        Set<String> present = existing.stream().map(FieldMetadata::getName).collect(Collectors.toSet());
        List<String> missing = names.stream().filter(n -> !present.contains(n)).toList();
        if (missing.isEmpty()) return existing;

        ensureValidContext(ctx);

        List<FieldMetadata> defs = missing.stream().map(n -> FieldMetadata.builder()
                .tenantId(ctx.getTenantId())
                .appCode(ctx.getAppCode())
                .bizIdentityCode(ctx.getBizIdentityCode())
                .entityType(ctx.getEntityType())
                .name(n)
                .dataType(determineType(ctx.getExtraProperties().get(n)).name())
                .build()
        ).collect(Collectors.toList());

        List<FieldMetadata> created = metadataService.allocateAndPersistFields(defs);
        existing.addAll(created);
        return existing;
    }

    private void ensureValidContext(ExtensionContext ctx) {
        Map<String, Object> extras = ctx.getExtraProperties();
        if (extras == null || extras.isEmpty()) {
            throw new IllegalArgumentException("No extra properties");
        }
        if (!ctx.isNewEntity() && ctx.getEntityId() == null) {
            throw new IllegalArgumentException("Entity ID missing");
        }
    }

    private Object convertFieldValue(Object rawValue, String dataType) {
        try {
            DataType type = DataType.valueOf(dataType);
            // 根据DataType获取对应的Java类型
            Class<?> targetClass = getTargetClass(type);
            return TypeConverter.convert(rawValue, targetClass);
        } catch (IllegalArgumentException e) {
            log.warn("Unsupported data type conversion: {}", dataType);
            return rawValue;
        }
    }

    private DataType determineType(Object value) {
        return TypeDetector.detect(value, TEXT_LENGTH_THRESHOLD);
    }

    // 移除内部TypeConverter类，统一使用SDK的TypeConverter类

    private Class<?> getTargetClass(DataType type) {
        return switch (type) {
            case INTEGER -> Long.class;
            case NUMBER -> BigDecimal.class;
            case BOOLEAN -> Boolean.class;
            case DATE -> LocalDateTime.class;
            default -> String.class;
        };
    }



    private String getDimensionKey(ExtensionContext ctx) {
        return String.join("|",
                ctx.getTenantId().toString(),
                ctx.getAppCode(),
                ctx.getBizIdentityCode(),
                ctx.getEntityType());
    }

    private Map<String, Object> buildSelectParams(ExtensionContext ctx) {
        return Map.of(
                "tenant_id", ctx.getTenantId(),
                "app_code", ctx.getAppCode(),
                "biz_identity_code", ctx.getBizIdentityCode(),
                "entity_type", ctx.getEntityType(),
                "entity_id", ctx.getEntityId()
        );
    }

    private Map<String, Object> buildUpsertParams(ExtensionContext ctx, List<FieldMetadata> fields) {
        Map<String, Object> params = new HashMap<>(Math.max(32, fields.size() + 5));
        params.put("tenant_id", ctx.getTenantId());
        params.put("app_code", ctx.getAppCode());
        params.put("biz_identity_code", ctx.getBizIdentityCode());
        params.put("entity_type", ctx.getEntityType());
        params.put("entity_id", ctx.getEntityId());

        fields.forEach(f -> params.put(f.getColumnName(), ctx.getExtraProperties().get(f.getName())));
        return params;
    }
}