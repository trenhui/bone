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
import com.bone.metadata.sdk.support.config.MetadataSdkContext;
import com.bone.metadata.sdk.support.util.DistributedLockUtil;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ReservedColumnsHandler implements ExtensionStorageHandler {
    private static final Logger LOGGER = Logger.getLogger(ReservedColumnsHandler.class.getName());
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
                        sqlExecutor.executeUpdate(new CompiledQuery(sql, buildUpsertParams(ctx, fields)));
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
        List<Map<String, Object>> rows = sqlExecutor.executeQueryForMap(cq);
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

        List<FieldMetadata> defs = new ArrayList<>();
        for (String n : missing) {
            DataType dataType = determineType(ctx.getExtraProperties().get(n));
            FieldMetadata field = new FieldMetadata();
            field.setTenantId(ctx.getTenantId());
            field.setAppCode(ctx.getAppCode());
            field.setBizIdentityCode(ctx.getBizIdentityCode());
            field.setEntityType(ctx.getEntityType());
            field.setName(n);
            field.setDataType(dataType.name());
            defs.add(field);
        }

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
            return TypeConverter.convert(rawValue, DataType.valueOf(dataType));
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Unsupported data type conversion: " + dataType);
            return rawValue;
        }
    }

    private DataType determineType(Object value) {
        return TypeDetector.detect(value, TEXT_LENGTH_THRESHOLD);
    }

    private static class TypeConverter {
        static Object convert(Object value, DataType type) {
            return switch (type) {
                case INTEGER -> convertToLong(value);
                case NUMBER -> convertToBigDecimal(value);
                case BOOLEAN -> convertToBoolean(value);
                case DATE -> convertToDateTime(value);
                default -> value;
            };
        }

        private static Long convertToLong(Object value) {
            if (value instanceof Number num) return num.longValue();
            if (value instanceof String str) {
                try {
                    return Long.parseLong(str);
                } catch (NumberFormatException e) {
                    LOGGER.warning("Long conversion failed for value: " + str);
                    return null;
                }
            }
            return null;
        }

        private static BigDecimal convertToBigDecimal(Object value) {
            if (value instanceof BigDecimal bd) return bd.stripTrailingZeros();
            if (value instanceof Number num) return new BigDecimal(num.toString());
            if (value instanceof String str) {
                try {
                    return new BigDecimal(str);
                } catch (NumberFormatException e) {
                    LOGGER.warning("BigDecimal conversion failed for value: " + str);
                    return null;
                }
            }
            return null;
        }

        private static Boolean convertToBoolean(Object value) {
            if (value instanceof Boolean bool) return bool;
            if (value instanceof Number num) return num.intValue() != 0;
            if (value instanceof String str) {
                return Boolean.parseBoolean(str) || "1".equals(str);
            }
            return null;
        }

        private static LocalDateTime convertToDateTime(Object value) {
            if (value instanceof LocalDateTime ldt) return ldt;
            if (value instanceof Date date) {
                return date.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
            }
            if (value instanceof String str) {
                try {
                    return LocalDateTime.parse(str);
                } catch (DateTimeParseException e) {
                    LOGGER.warning("DateTime conversion failed for value: " + str);
                    return null;
                }
            }
            return null;
        }
    }

    private static class TypeDetector {
        private static final Map<Class<?>, DataType> TYPE_MAPPINGS = createTypeMappings();

        static DataType detect(Object value, int textThreshold) {
            if (value == null) return DataType.STRING;

            DataType type = TYPE_MAPPINGS.entrySet().stream()
                    .filter(entry -> entry.getKey().isInstance(value))
                    .findFirst()
                    .map(Map.Entry::getValue)
                    .orElse(DataType.STRING);

            if (type == DataType.STRING && value instanceof String str) {
                return str.length() > textThreshold
                        ? DataType.TEXT
                        : DataType.STRING;
            }
            return type;
        }

        private static Map<Class<?>, DataType> createTypeMappings() {
            Map<Class<?>, DataType> map = new HashMap<>();
            map.put(Boolean.class, DataType.BOOLEAN);
            map.put(Number.class, DataType.NUMBER);
            map.put(java.util.Date.class, DataType.DATE);
            map.put(java.time.temporal.Temporal.class, DataType.DATE);
            map.put(Map.class, DataType.JSON);
            map.put(com.fasterxml.jackson.databind.JsonNode.class, DataType.JSON);
            return Collections.unmodifiableMap(map);
        }
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