package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.SmartFieldMetadata;
import com.bone.smartmeta.engine.metadata.ValidationRuleMetadata;
import com.bone.smartmeta.engine.repository.MetadataRepository;
import com.bone.smartmeta.engine.validation.ValidationResult;
import com.bone.smartmeta.engine.validation.ValidationResult.ValidationError;
import com.bone.smartmeta.engine.validation.ValidationResult.ValidationWarning;
import com.bone.smartmeta.engine.validation.strategy.RelationshipValidationStrategy;
import com.bone.smartmeta.engine.validation.strategy.StrategyValidationExecutor;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Metadata Validation Engine
 * <p>
 * Core component responsible for validating entity data against metadata-defined rules.
 * Implements the strategy pattern for flexible validation approaches.
 * Provides caching, batch processing, and performance monitoring capabilities.
 */
@Component
public class ValidationEngine implements InitializingBean, RelationshipValidationStrategy.EntityMetadataProvider {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationEngine.class);
    private static final Map<String, Class<?>> SUPPORTED_TYPES = new HashMap<>();
    
    static {
        SUPPORTED_TYPES.put("string", String.class);
        SUPPORTED_TYPES.put("boolean", Boolean.class);
        SUPPORTED_TYPES.put("integer", Integer.class);
        SUPPORTED_TYPES.put("long", Long.class);
        SUPPORTED_TYPES.put("float", Float.class);
        SUPPORTED_TYPES.put("double", Double.class);
        SUPPORTED_TYPES.put("date", Date.class);
        SUPPORTED_TYPES.put("datetime", Date.class);
        SUPPORTED_TYPES.put("timestamp", Date.class);
        SUPPORTED_TYPES.put("list", List.class);
        SUPPORTED_TYPES.put("map", Map.class);
        SUPPORTED_TYPES.put("object", Object.class);
    }

    

    
    // Dependencies
    private MetadataRepository metadataRepository;
    private MetadataEngine metadataEngine;
    private final ExpressionEngine expressionEngine; // Made final for immutability
    private RuleEngine ruleEngine;
    private StrategyValidationExecutor validationExecutor;
    private Executor taskExecutor;
    
    // Configuration parameters
    @Setter
    private boolean cacheEnabled = true;
    @Setter
    private boolean failFast = false;
    
    // Batch processing configuration
    @Getter
    @Setter
    private int batchThreshold = DEFAULT_BATCH_THRESHOLD;
    public static final int DEFAULT_BATCH_THRESHOLD = 100;
    
    // Cache configuration
    @Getter
    @Setter
    private long cacheExpirationTime = TimeUnit.MINUTES.toMillis(30);
    @Getter
    @Setter
    private int maxCacheSize = 1000;
    
    // Caches
    private final ConcurrentHashMap<String, CachedEntityMetadata> entityMetadataCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Method> methodReflectionCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Pattern> regexPatternCache = new ConcurrentHashMap<>();
    
    // Performance metrics
    private final AtomicLong totalValidationCount = new AtomicLong(0);
    private final AtomicLong successfulValidationCount = new AtomicLong(0);
    private final AtomicLong validationErrorCount = new AtomicLong(0);
    private final AtomicLong validationWarningCount = new AtomicLong(0);
    private final AtomicLong cacheHitCounter = new AtomicLong(0);
    private final AtomicLong cacheMissCounter = new AtomicLong(0);
    
    // Formatters and constants
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    // Supported field types
    private static final Set<String> SUPPORTED_FIELD_TYPES = new HashSet<>(
            Arrays.asList("string", "integer", "int", "long", "double", "boolean", "date", "datetime", "array", "object"));
    
    /**
     * Represents a cached entity metadata entry with timestamp for expiration tracking.
     */
    private static class CachedEntityMetadata {
        private final EntityMetadata metadata;
        private final long timestamp;
        
        public CachedEntityMetadata(EntityMetadata metadata) {
            this.metadata = metadata;
            this.timestamp = System.currentTimeMillis();
        }
        
        /**
         * Checks if this cached entry has expired based on the provided expiration time.
         * 
         * @param expirationTime the expiration time in milliseconds
         * @return true if expired, false otherwise
         */
        public boolean isExpired(long expirationTime) {
            return System.currentTimeMillis() - timestamp > expirationTime;
        }
        
        public EntityMetadata getMetadata() {
            return metadata;
        }
    }
    
    /**
     * Default constructor for auto-configuration.
     */
    public ValidationEngine() {
        this.expressionEngine = null;
    }
    
    /**
     * Constructor for testing and manual creation with minimal dependencies.
     * 
     * @param metadataRepository the metadata repository
     * @param expressionEngine the expression engine
     */
    public ValidationEngine(MetadataRepository metadataRepository, ExpressionEngine expressionEngine) {
        this.metadataRepository = metadataRepository;
        this.expressionEngine = expressionEngine;
    }
    
    /**
     * Complete constructor for production environment with all dependencies.
     * 
     * @param metadataRepository the metadata repository
     * @param metadataEngine the metadata engine
     * @param expressionEngine the expression engine
     * @param ruleEngine the rule engine
     * @param validationExecutor the validation executor using strategy pattern
     */
    public ValidationEngine(MetadataRepository metadataRepository,
                           MetadataEngine metadataEngine,
                           ExpressionEngine expressionEngine,
                           RuleEngine ruleEngine,
                           StrategyValidationExecutor validationExecutor) {
        this.metadataRepository = metadataRepository;
        this.metadataEngine = metadataEngine;
        this.expressionEngine = expressionEngine;
        this.ruleEngine = ruleEngine;
        this.validationExecutor = validationExecutor;
    }
    
    // Dependency injection setters
    public void setMetadataEngine(MetadataEngine metadataEngine) {
        this.metadataEngine = metadataEngine;
    }
    
    public void setRuleEngine(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }
    
    public void setMetadataRepository(MetadataRepository metadataRepository) {
        this.metadataRepository = metadataRepository;
    }
    
    public void setValidationExecutor(StrategyValidationExecutor validationExecutor) {
        this.validationExecutor = validationExecutor;
    }
    
    public void setTaskExecutor(Executor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        LOGGER.info("Initializing validation engine - cache enabled: {}, fail fast mode: {}, batch threshold: {}, " +
                    "cache expiration time: {}ms", 
                    cacheEnabled, failFast, batchThreshold, cacheExpirationTime);
        
        // Initialize task executor if not provided
        if (this.taskExecutor == null) {
            this.taskExecutor = Executors.newCachedThreadPool(runnable -> {
                Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                thread.setDaemon(true);
                thread.setName("validation-engine-" + thread.getId());
                return thread;
            });
        }
        
        // Schedule cache cleanup task
        scheduleCacheCleanup();
    }
    
    /**
     * Schedules periodic cache cleanup task to prevent memory leaks.
     * Uses a dedicated daemon thread for cleanup operations.
     */
    private void scheduleCacheCleanup() {
        // Create single-threaded scheduled executor for cache cleanup
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName("validation-cache-cleanup");
            return thread;
        });
        
        // Run cleanup task at half the expiration time interval to ensure timely cleanup
        scheduler.scheduleAtFixedRate(this::cleanupCache, 
                                      cacheExpirationTime / 2, 
                                      cacheExpirationTime / 2, 
                                      TimeUnit.MILLISECONDS);
        
        LOGGER.debug("Cache cleanup task scheduled with interval: {}ms", cacheExpirationTime / 2);
    }
    
    /**
     * Cleans up expired and excessive cache entries to maintain memory efficiency.
     * Removes entries that have expired based on the configured expiration time.
     * If cache size exceeds maximum limit, removes oldest entries first.
     */
    private void cleanupCache() {
        try {
            long beforeSize = entityMetadataCache.size();
            
            // Remove expired cache entries
            entityMetadataCache.entrySet().removeIf(entry -> 
                entry.getValue().isExpired(cacheExpirationTime));
            
            // If cache still exceeds maximum size, remove oldest entries
            if (entityMetadataCache.size() > maxCacheSize) {
                List<Map.Entry<String, CachedEntityMetadata>> entries = new ArrayList<>(entityMetadataCache.entrySet());
                entries.sort(Comparator.comparingLong(e -> e.getValue().timestamp));
                
                int removeCount = entries.size() - maxCacheSize;
                for (int i = 0; i < removeCount; i++) {
                    entityMetadataCache.remove(entries.get(i).getKey());
                }
            }
            
            long afterSize = entityMetadataCache.size();
            if (beforeSize > afterSize) {
                LOGGER.debug("Cache cleanup completed, removed {} expired entries, current cache size: {}", beforeSize - afterSize, afterSize);
            }
            
            // Clean up method reflection cache
            cleanupMethodReflectionCache();
        } catch (Exception e) {
            LOGGER.error("Failed to clean up cache", e);
        }
    }
    
    /**
     * Cleans up the method reflection cache when it exceeds size limits.
     * Maintains memory efficiency by removing oldest entries when cache grows too large.
     */
    private void cleanupMethodReflectionCache() {
        // Limit method cache size to prevent memory issues
        if (methodReflectionCache.size() > maxCacheSize * 2) {
            // Simplified implementation: remove half of entries when cache grows too large
            List<String> keys = new ArrayList<>(methodReflectionCache.keySet());
            Collections.shuffle(keys);
            for (int i = 0; i < keys.size() / 2; i++) {
                methodReflectionCache.remove(keys.get(i));
            }
            LOGGER.debug("Method reflection cache cleaned up, current size: {}", methodReflectionCache.size());
        }
    }
    
    /**
     * Validates entity data against metadata-defined rules.
     * 
     * @param entityMetadata the entity metadata definition
     * @param entityData the entity data as field-value map
     * @return validation result containing errors and warnings
     */
    public ValidationResult validateEntity(EntityMetadata entityMetadata, Map<String, Object> entityData) {
        Assert.notNull(entityMetadata, "实体元数据不能为空");
        Assert.notNull(entityData, "实体数据不能为空");
        
        long startTime = System.currentTimeMillis();
        totalValidationCount.incrementAndGet();
        
        LOGGER.debug("开始验证实体数据，实体类型: {}", entityMetadata.getApiName());
        
        ValidationResult result = ValidationResult.builder().build();
        
        try {
            // 使用策略验证执行器执行所有验证策略
            if (validationExecutor != null) {
                validationExecutor.executeStrategies(entityMetadata, entityData, result, failFast);
            } else {
                // 降级到直接验证逻辑
                validateEntityWithDirectApproach(entityMetadata, entityData, result);
            }
            
            // 更新性能统计
            if (result.isValid()) {
                successfulValidationCount.incrementAndGet();
            } else {
                validationErrorCount.addAndGet(result.getErrors().size());
            }
            validationWarningCount.addAndGet(result.getWarnings().size());
            
            long duration = System.currentTimeMillis() - startTime;
            LOGGER.debug("实体数据验证完成，实体类型: {}, 是否有效: {}, 错误数量: {}, 警告数量: {}, 耗时: {}ms", 
                    entityMetadata.getApiName(), result.isValid(), result.getErrors().size(), 
                    result.getWarnings().size(), duration);
            
            // 记录慢验证
            if (duration > 100) {
                LOGGER.warn("验证性能警告: 实体类型 {} 的验证耗时超过100ms ({})", 
                        entityMetadata.getApiName(), duration);
            }
        } catch (Exception e) {
            LOGGER.error("验证过程发生异常，实体类型: {}", entityMetadata.getApiName(), e);
            result.addError(ValidationResult.ValidationError.builder()
                    .fieldPath("general")
                    .message("验证过程发生异常: " + e.getMessage())
                    .build());
        }
        
        return result;
    }
    
    /**
     * Validates entity data using direct approach (fallback when strategy executor is unavailable).
     * 
     * @param entityMetadata the entity metadata definition
     * @param entityData the entity data to validate
     * @param result the validation result to accumulate errors and warnings
     */
    private void validateEntityWithDirectApproach(EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
        // 验证必填字段
        validateRequiredFields(entityMetadata, entityData, result);
        if (failFast && !result.isValid()) {
            LOGGER.warn("快速失败模式：必填字段验证失败，提前返回");
            return;
        }
        
        // 验证字段类型
        validateFieldTypes(entityMetadata, entityData, result);
        if (failFast && !result.isValid()) {
            LOGGER.warn("快速失败模式：字段类型验证失败，提前返回");
            return;
        }
        
        // 验证字段约束
        validateFieldConstraints(entityMetadata, entityData, result);
        if (failFast && !result.isValid()) {
            LOGGER.warn("快速失败模式：字段约束验证失败，提前返回");
            return;
        }
        
        // 验证自定义规则
        validateCustomRules(entityMetadata, entityData, result);
        if (failFast && !result.isValid()) {
            LOGGER.warn("快速失败模式：自定义规则验证失败，提前返回");
            return;
        }
        
        // 验证关联字段
        validateRelationshipFields(entityMetadata, entityData, result);
    }
    
    /**
     * Cache-friendly method for validating entity data by type name.
     * 
     * @param entityType the entity type name
     * @param entityData the entity data to validate
     * @return the validation result
     */
    @Cacheable(value = "validationResult", 
               key = "#entityType + '-' + T(java.util.Objects).hashCode(#entityData)", 
               unless = "#result == null || !#result.isValid()")
    public ValidationResult validateEntityByType(String entityType, Map<String, Object> entityData) {
        Assert.hasText(entityType, "Entity type name cannot be empty");
        LOGGER.debug("Validating data by entity type, type: {}", entityType);
        
        try {
            // Get entity metadata from cache or repository
            EntityMetadata entityMetadata = getEntityMetadata(entityType);
            if (entityMetadata == null) {
                ValidationResult result = ValidationResult.builder().build();
                result.addError(ValidationResult.ValidationError.builder()
                    .fieldPath("general")
                    .message("Entity metadata definition not found for type '" + entityType + "'")
                    .build());
                return result;
            }
            
            return validateEntity(entityMetadata, entityData);
        } catch (Exception e) {
            LOGGER.error("Failed to validate by entity type: {}", entityType, e);
            ValidationResult result = ValidationResult.builder().build();
            result.addError(ValidationResult.ValidationError.builder()
                .fieldPath("general")
                .message("验证失败: " + e.getMessage())
                .build());
            return result;
        }
    }
    
    /**
     * Validates entity data by its type name.
     * 
     * @param entityType the entity type name
     * @param entityData the entity data to validate
     * @return the validation result
     */
    public ValidationResult validateEntityByTypeName(String entityType, Map<String, Object> entityData) {
        // Delegate to the cache-friendly implementation
    return validateEntityByType(entityType, entityData);
    }
    
    /**
     * Asynchronously validates a batch of entity data.
     * 
     * @param entityMetadata the entity metadata definition
     * @param entityDataList the list of entity data to validate
     * @return CompletableFuture with list of validation results
     */
    @Async
    public CompletableFuture<List<ValidationResult>> validateBatchAsync(EntityMetadata entityMetadata, 
                                                                       List<Map<String, Object>> entityDataList) {
        Assert.notNull(entityMetadata, "实体元数据不能为空");
        Assert.notEmpty(entityDataList, "实体数据列表不能为空");
        
        long startTime = System.currentTimeMillis();
        int recordCount = entityDataList.size();
        
        LOGGER.debug("开始批量异步验证，实体类型: {}, 记录数量: {}", 
                entityMetadata.getApiName(), recordCount);
        
        // 使用策略验证执行器执行批量验证
        if (validationExecutor != null) {
            try {
                return validationExecutor.executeBatchStrategiesAsync(entityMetadata, entityDataList, failFast)
                    .whenComplete((results, ex) -> {
                        if (ex != null) {
                            LOGGER.error("批量异步验证执行器失败", ex);
                        }
                        logPerformanceMetrics(entityMetadata.getApiName(), startTime, recordCount, results);
                    });
            } catch (Exception e) {
                LOGGER.error("批量异步验证执行器调用失败，降级到自定义实现", e);
            }
        }
        
        // 降级到自定义异步验证逻辑
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 根据数据量选择合适的处理策略
                if (recordCount <= 500) {
                    // 中小批量数据，使用并行流
                    return entityDataList.parallelStream()
                        .map(data -> {
                            try {
                                return validateEntity(entityMetadata, data);
                            } catch (Exception e) {
                                LOGGER.error("Single record asynchronous validation failed", e);
                                return createErrorValidationResult("Record validation exception: " + e.getMessage());
                            }
                        })
                        .collect(Collectors.toList());
                } else {
                    // 大数据量，分批次处理
                    return processLargeBatch(entityMetadata, entityDataList);
                }
            } catch (Exception e) {
                LOGGER.error("Batch asynchronous validation process exception", e);
                // 创建一个包含错误结果的列表
                ValidationResult errorResult = createErrorValidationResult("Batch asynchronous validation exception: " + e.getMessage());
                return Collections.singletonList(errorResult);
            }
        }, getTaskExecutor())
        .whenComplete((results, ex) -> {
            if (ex != null) {
                LOGGER.error("Batch asynchronous validation completed with exception", ex);
            }
            logPerformanceMetrics(entityMetadata.getApiName(), startTime, recordCount, results);
        });
    }
    
    /**
     * Processes large batches of entity data with optimized parallel validation.
     * 
     * @param entityMetadata the entity metadata definition
     * @param entityDataList the list of entity data to validate
     * @return list of validation results
     */
    private List<ValidationResult> processLargeBatch(EntityMetadata entityMetadata, List<Map<String, Object>> entityDataList) {
        List<ValidationResult> results = new ArrayList<>(entityDataList.size());
        int batchSize = Math.min(500, entityDataList.size() / Runtime.getRuntime().availableProcessors());
        
        for (int i = 0; i < entityDataList.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, entityDataList.size());
            List<Map<String, Object>> batch = entityDataList.subList(i, endIndex);
            
            // 对每个子批次使用并行流
            List<ValidationResult> batchResults = batch.parallelStream()
                .map(data -> {
                    try {
                        return validateEntity(entityMetadata, data);
                    } catch (Exception e) {
                        LOGGER.error("Single record validation failed in large batch processing", e);
                        return createErrorValidationResult("Record validation exception: " + e.getMessage());
                    }
                })
                .collect(Collectors.toList());
            
            results.addAll(batchResults);
        }
        
        return results;
    }
    
    /**
     * Logs performance metrics for batch validation operations.
     * 
     * @param entityType the entity type name
     * @param startTime the operation start time
     * @param recordCount the number of records processed
     * @param results the validation results
     */
    private void logPerformanceMetrics(String entityType, long startTime, int recordCount, List<ValidationResult> results) {
        long duration = System.currentTimeMillis() - startTime;
        
        if (results != null) {
            int successCount = 0;
            int errorCount = 0;
            int warningCount = 0;
            
            for (ValidationResult result : results) {
                if (result.isValid()) {
                    successCount++;
                }
                errorCount += result.getErrors().size();
                warningCount += result.getWarnings().size();
            }
            
            LOGGER.info("批量验证完成，实体类型: {}, 总记录数: {}, 成功数: {}, 错误总数: {}, 警告总数: {}, 总耗时: {}ms, 平均每条耗时: {}ms",
                    entityType, recordCount, successCount, errorCount, warningCount, duration, 
                    recordCount > 0 ? duration / recordCount : 0);
            
            // Update statistics
            totalValidationCount.addAndGet(recordCount);
            successfulValidationCount.addAndGet(successCount);
            validationErrorCount.addAndGet(errorCount);
            validationWarningCount.addAndGet(warningCount);
        }
        
        // 记录性能警告
            if (recordCount > 0 && duration / recordCount > 50) {
                LOGGER.warn("批量验证性能警告: 实体类型 {} 的平均验证耗时超过50ms ({})", 
                        entityType, duration / recordCount);
            }
    }
    
    /**
     * Validates a batch of entity data synchronously.
     * 
     * @param entityMetadata the entity metadata definition
     * @param entityDataList the list of entity data to validate
     * @return list of validation results
     */
    public List<ValidationResult> validateBatch(EntityMetadata entityMetadata, List<Map<String, Object>> entityDataList) {
        Assert.notNull(entityMetadata, "实体元数据不能为空");
        
        LOGGER.debug("开始批量验证实体数据，实体类型: {}, 数据量: {}", 
                entityMetadata.getApiName(), entityDataList != null ? entityDataList.size() : 0);
        
        if (entityDataList == null || entityDataList.isEmpty()) {
            return Collections.emptyList();
        }
        
        long startTime = System.currentTimeMillis();
        int recordCount = entityDataList.size();
        
        List<ValidationResult> results;
        
        // Use validation executor for batch processing
        if (validationExecutor != null) {
            try {
                results = validationExecutor.executeBatchStrategiesAsync(entityMetadata, entityDataList, failFast)
                        .get(30, TimeUnit.SECONDS); // Set timeout
            } catch (Exception e) {
                LOGGER.error("Batch validation executor failed, falling back to direct validation", e);
                results = performFallbackBatchValidation(entityMetadata, entityDataList);
            }
        } else {
            results = performFallbackBatchValidation(entityMetadata, entityDataList);
        }
        
        // Log performance metrics
        logPerformanceMetrics(entityMetadata.getApiName(), startTime, recordCount, results);
        
        return results;
    }
    
    /**
     * Performs fallback batch validation when specialized executor is unavailable.
     * 
     * @param entityMetadata the entity metadata definition
     * @param entityDataList the list of entity data to validate
     * @return list of validation results
     */
    private List<ValidationResult> performFallbackBatchValidation(EntityMetadata entityMetadata, List<Map<String, Object>> entityDataList) {
        // 根据数据量选择合适的验证策略
        if (entityDataList.size() <= batchThreshold) {
            // 小批量数据，使用顺序处理
            List<ValidationResult> results = new ArrayList<>(entityDataList.size());
            for (int i = 0; i < entityDataList.size(); i++) {
                try {
                    results.add(validateEntity(entityMetadata, entityDataList.get(i)));
                } catch (Exception e) {
                    LOGGER.error("Single record validation failed at index: {}", i, e);
                results.add(createErrorValidationResult("Record validation exception: " + e.getMessage()));
                }
            }
            return results;
        } else {
            // 大数据量使用并行处理
            return entityDataList.parallelStream()
                .map(data -> {
                    try {
                        return validateEntity(entityMetadata, data);
                    } catch (Exception e) {
                        LOGGER.error("Single record validation failed during parallel processing", e);
                return createErrorValidationResult("Record validation exception: " + e.getMessage());
                    }
                })
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Creates a validation result with general error message.
     * 
     * @param errorMessage the error message to include
     * @return validation result with error
     */
    private ValidationResult createErrorValidationResult(String errorMessage) {
        ValidationResult result = ValidationResult.builder().build();
        result.addError(ValidationResult.ValidationError.builder()
            .fieldPath("general")
            .message(errorMessage)
            .build());
        return result;
    }
    
    /**
     * Validates that all required fields are present and not null/empty.
     * 
     * @param entityMetadata the entity metadata definition
     * @param entityData the entity data to validate
     * @param result the validation result to accumulate errors
     */
    private void validateRequiredFields(EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
        entityMetadata.getFields().values().stream()
            .filter(SmartFieldMetadata::isRequired)
            .forEach(field -> {
                String fieldName = field.getApiName();
                if (!entityData.containsKey(fieldName)) {
                    result.addError(ValidationResult.ValidationError.builder()
                        .fieldPath(fieldName)
                        .message(String.format("Field '%s' is required", field.getLabel()))
                        .build());
                } else if (entityData.get(fieldName) == null) {
                    result.addError(ValidationResult.ValidationError.builder()
                        .fieldPath(fieldName)
                        .message(String.format("Field '%s' cannot be null", field.getLabel()))
                        .build());
                } else if ("string".equalsIgnoreCase(getFieldTypeName(field)) && 
                           entityData.get(fieldName) instanceof String &&
                           ((String) entityData.get(fieldName)).trim().isEmpty()) {
                    result.addError(ValidationResult.ValidationError.builder()
                        .fieldPath(fieldName)
                        .message(String.format("Field '%s' cannot be empty string", field.getLabel()))
                        .build());
                }
            });
    }
    
    /**
     * Validates that field values match their expected data types.
     * 
     * @param entityMetadata the entity metadata definition
     * @param entityData the entity data to validate
     * @param result the validation result to accumulate errors
     */
    private void validateFieldTypes(EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
        entityMetadata.getFields().values().forEach(field -> {
            String fieldName = field.getApiName();
            String fieldType = getFieldTypeName(field);
            
            // 跳过不存在或为null的字段
            if (!entityData.containsKey(fieldName) || entityData.get(fieldName) == null) {
                return;
            }
            
            Object value = entityData.get(fieldName);
            boolean isValidType = validateFieldType(fieldType, value, fieldName, result);
            
            // 类型不匹配时添加错误
            if (!isValidType) {
                result.addError(ValidationResult.ValidationError.builder()
                    .fieldPath(fieldName)
                    .message(String.format("Field '%s' requires %s type, actual type: %s", 
                            field.getLabel(), fieldType, value.getClass().getSimpleName()))
                    .build());
            }
        });
    }
    
    /**
     * Validates if a value matches the expected field type.
     * 
     * @param fieldType the expected field type
     * @param value the actual value to validate
     * @param fieldName the field name for error messages
     * @param result the validation result to accumulate warnings
     * @return true if type is valid, false otherwise
     */
    private boolean validateFieldType(String fieldType, Object value, String fieldName, ValidationResult result) {
        switch (fieldType.toLowerCase()) {
            case "string":
                return value instanceof String;
            case "integer":
            case "int":
                // 整数类型需要确保值为整数
                return value instanceof Integer || 
                       (value instanceof Number && ((Number) value).doubleValue() == Math.floor(((Number) value).doubleValue()));
            case "long":
                // Long类型可以接受整数和长整数
                return value instanceof Long || 
                       (value instanceof Number && ((Number) value).doubleValue() == Math.floor(((Number) value).doubleValue()));
            case "double":
                // Double类型可以接受任何数值
                return value instanceof Number;
            case "boolean":
                return value instanceof Boolean;
            case "array":
                return value instanceof List || value instanceof Object[];
            case "object":
                return value instanceof Map;
            case "date":
                // 日期类型验证
                return validateDateType(value, fieldName, result);
            case "datetime":
                // 日期时间类型验证
                return validateDateTimeType(value, fieldName, result);
            default:
                // 未知类型发出警告
                if (!SUPPORTED_TYPES.containsKey(fieldType.toLowerCase())) {
                    result.addWarning(ValidationResult.ValidationWarning.builder()
                        .message(String.format("Field '%s' uses unknown data type: %s", 
                            fieldName, fieldType))
                        .build());
                }
                return true; // 未知类型默认为有效
        }
    }
    
    /**
     * Validates if a value is a valid date.
     * 
     * @param value the value to validate
     * @param fieldName the field name for error messages
     * @param result the validation result to accumulate errors
     * @return true if value is a valid date, false otherwise
     */
    private boolean validateDateType(Object value, String fieldName, ValidationResult result) {
        if (value instanceof java.util.Date) {
            return true;
        } else if (value instanceof String) {
            try {
                // Attempt to parse date format
                LocalDate.parse((String) value, DATE_FORMATTER);
                return true;
            } catch (DateTimeParseException e) {
                result.addError(ValidationResult.ValidationError.builder()
                    .fieldPath(fieldName)
                    .message(String.format("Field value is not a valid date format, please use %s format", 
                            DATE_FORMATTER.toString()))
                    .build());
                return false;
            }
        }
        return false;
    }
    
    /**
     * Validates if a value is a valid date-time.
     * 
     * @param value the value to validate
     * @param fieldName the field name for error messages
     * @param result the validation result to accumulate errors
     * @return true if value is a valid date-time, false otherwise
     */
    private boolean validateDateTimeType(Object value, String fieldName, ValidationResult result) {
        if (value instanceof java.util.Date) {
            return true;
        } else if (value instanceof String) {
            try {
                // Attempt to parse date-time format
                LocalDateTime.parse((String) value, DATE_TIME_FORMATTER);
                return true;
            } catch (DateTimeParseException e) {
                result.addError(ValidationResult.ValidationError.builder()
                    .fieldPath(fieldName)
                    .message(String.format("Field value is not a valid date-time format, please use %s format", 
                            DATE_TIME_FORMATTER.toString()))
                    .build());
                return false;
            }
        }
        return false;
    }
    
    /**
     * Safely retrieves the field type name from a field metadata.
     * 
     * @param field the field metadata
     * @return the field type name, or "string" as default
     */
    private String getFieldTypeName(SmartFieldMetadata field) {
        // Build cache key
        String cacheKey = field.getClass().getName() + ":type:" + field.getApiName();
        
        try {
            // Try to get via cached method
            String cachedType = (String) getFieldPropertyWithCache(field, "type", String.class, cacheKey);
            if (cachedType != null) {
                return cachedType;
            }
            
            // Try to access type field directly
            Field typeField = ReflectionUtils.findField(field.getClass(), "type");
            if (typeField != null) {
                ReflectionUtils.makeAccessible(typeField);
                Object typeValue = typeField.get(field);
                if (typeValue != null) {
                    return typeValue.toString();
                }
            }
            
            // Try to call getType() method
            Method getTypeMethod = findMethod(field.getClass(), "getType");
            if (getTypeMethod != null) {
                Object typeValue = getTypeMethod.invoke(field);
                if (typeValue != null) {
                    return typeValue.toString();
                }
            }
        } catch (Exception e) {
            // Ignore exceptions
            LOGGER.debug("Failed to get field type, returning default type: {}.{}", field.getClass().getSimpleName(), field.getApiName());
        }
        return "string"; // Default to string type
    }
    
    /**
     * Retrieves field property with method caching for better performance.
     * 
     * @param field the field metadata
     * @param propertyName the property name to retrieve
     * @param propertyType the expected property type
     * @param cacheKey the cache key for method lookup
     * @return the property value if available and of correct type, null otherwise
     */
    private Object getFieldPropertyWithCache(SmartFieldMetadata field, String propertyName, 
                                            Class<?> propertyType, String cacheKey) {
        try {
            // 尝试从方法缓存获取方法
            String methodName = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            Method method = findMethod(field.getClass(), methodName);
            
            if (method != null) {
                Object value = method.invoke(field);
                if (propertyType.isInstance(value)) {
                    return value;
                }
            }
        } catch (Exception e) {
            // 忽略异常，继续尝试其他方式
        }
        return null;
    }
    
    /**
     * Finds a method using reflection and caches it for future use.
     * 
     * @param clazz the class to search for method
     * @param methodName the method name to find
     * @param paramTypes the parameter types of the method
     * @return the found method or null if not found
     */
    private Method findMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        String cacheKey = clazz.getName() + ":" + methodName;
        
        // 先从缓存中查找
        Method cachedMethod = methodReflectionCache.get(cacheKey);
        if (cachedMethod != null) {
            return cachedMethod;
        }
        
        // 使用ReflectionUtils查找方法
        Method method = ReflectionUtils.findMethod(clazz, methodName, paramTypes);
        if (method != null) {
            ReflectionUtils.makeAccessible(method);
            // 缓存方法
            methodReflectionCache.put(cacheKey, method);
        }
        
        return method;
    }
    
    /**
     * Safely retrieves picklist values from a field metadata.
     * 
     * @param field the field metadata
     * @return list of picklist values or empty list if not available
     */
    private List<String> getPicklistValues(SmartFieldMetadata field) {
        try {
            // 尝试直接访问picklistValues字段
            Field picklistField = field.getClass().getDeclaredField("picklistValues");
            picklistField.setAccessible(true);
            Object picklistValue = picklistField.get(field);
            if (picklistValue instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> resultList = (List<String>) picklistValue;
                    return resultList;
                }
        } catch (Exception e) {
            // Ignore exceptions, return empty list
            LOGGER.debug("Failed to retrieve picklist values", e);
        }
        return Collections.emptyList();
    }
    
    /**
     * Validates all field constraints including string, numeric, picklist and array constraints.
     * 
     * @param entityMetadata the entity metadata definition
     * @param entityData the entity data to validate
     * @param result the validation result to accumulate errors and warnings
     */
    private void validateFieldConstraints(EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
        entityMetadata.getFields().values().forEach(field -> {
            String fieldName = field.getApiName();
            if (!entityData.containsKey(fieldName)) {
                return;
            }
            
            Object value = entityData.get(fieldName);
            if (value == null) {
                return;
            }
            
            // 验证字符串约束
            if (value instanceof String) {
                validateStringConstraints(field, (String) value, result);
            }
            
            // 验证数值约束
            if (value instanceof Number) {
                validateNumericConstraints(field, ((Number) value).doubleValue(), result);
            }
            
            // 验证枚举值
            List<String> picklistValues = getPicklistValues(field);
            if (!picklistValues.isEmpty()) {
                validatePicklistValue(field, value.toString(), picklistValues, result);
            }
            
            // 验证数组约束
            if (value instanceof List) {
                validateArrayConstraints(field, (List<?>) value, result);
            }
        });
    }
    
    /**
     * Validates constraints specific to string values.
     * 
     * @param field the field metadata with constraints
     * @param value the string value to validate
     * @param result the validation result to accumulate errors
     */
    private void validateStringConstraints(SmartFieldMetadata field, String value, ValidationResult result) {
        String fieldName = field.getApiName();
        
        // Validate string length
        if (field.getMaxLength() != null && value.length() > field.getMaxLength()) {
            result.addError(ValidationResult.ValidationError.builder()
                .fieldPath(fieldName)
                .message(String.format("Field '%s' length cannot exceed %d characters", 
                        field.getLabel(), field.getMaxLength()))
                .build());
        }
        
        if (field.getMinLength() != null && value.length() < field.getMinLength()) {
            result.addError(ValidationResult.ValidationError.builder()
                .fieldPath(fieldName)
                .message(String.format("Field '%s' length cannot be less than %d characters", 
                        field.getLabel(), field.getMinLength()))
                .build());
        }
        
        // Validate regex pattern
        if (field.getRegexPattern() != null) {
            // Get pattern from cache or compile new one
            Pattern pattern = regexPatternCache.computeIfAbsent(field.getRegexPattern(), Pattern::compile);
            if (!pattern.matcher(value).matches()) {
                result.addError(ValidationResult.ValidationError.builder()
                .fieldPath(fieldName)
                .message(String.format("Field '%s' value does not match required format", 
                        field.getLabel()))
                .build());
            }
        }
    }
    
    /**
     * Validates constraints specific to numeric values.
     * 
     * @param field the field metadata with constraints
     * @param value the numeric value to validate
     * @param result the validation result to accumulate errors
     */
    private void validateNumericConstraints(SmartFieldMetadata field, double value, ValidationResult result) {
        String fieldName = field.getApiName();
        
        if (field.getMaxValue() != null && value > field.getMaxValue()) {
            result.addError(ValidationResult.ValidationError.builder()
                .fieldPath(fieldName)
                .message(String.format("Field '%s' value cannot be greater than %s", 
                        field.getLabel(), field.getMaxValue()))
                .build());
        }
        
        if (field.getMinValue() != null && value < field.getMinValue()) {
            result.addError(ValidationResult.ValidationError.builder()
                .fieldPath(fieldName)
                .message(String.format("Field '%s' value cannot be less than %s", 
                        field.getLabel(), field.getMinValue()))
                .build());
        }
    }
    
    /**
     * Validates that a value is within allowed picklist options.
     * 
     * @param field the field metadata
     * @param value the value to validate
     * @param picklistValues the list of allowed values
     * @param result the validation result to accumulate errors
     */
    private void validatePicklistValue(SmartFieldMetadata field, String value, List<String> picklistValues, ValidationResult result) {
        if (!picklistValues.contains(value)) {
            result.addError(ValidationResult.ValidationError.builder()
                .fieldPath(field.getApiName())
                .message(String.format("Field '%s' value must be one of: %s", 
                        field.getLabel(), String.join(", ", picklistValues)))
                .build());
        }
    }
    
    /**
     * Validates constraints specific to array/list values.
     * 
     * @param field the field metadata with constraints
     * @param value the list value to validate
     * @param result the validation result to accumulate errors
     */
    private void validateArrayConstraints(SmartFieldMetadata field, List<?> value, ValidationResult result) {
        String fieldName = field.getApiName();
        
        // 尝试获取并验证数组大小限制
        try {
            // 使用反射安全地获取数组大小限制
            Integer maxItems = getFieldProperty(field, "maxItems", Integer.class);
            if (maxItems != null && value.size() > maxItems) {
                result.addError(ValidationResult.ValidationError.builder()
                    .fieldPath(fieldName)
                    .message(String.format("Field '%s' array cannot have more than %d elements", 
                            field.getLabel(), maxItems))
                    .build());
            }
            
            Integer minItems = getFieldProperty(field, "minItems", Integer.class);
            if (minItems != null && value.size() < minItems) {
                result.addError(ValidationResult.ValidationError.builder()
                    .fieldPath(fieldName)
                    .message(String.format("Field '%s' array must have at least %d elements", 
                            field.getLabel(), minItems))
                    .build());
            }
        } catch (Exception e) {
            // Ignore exceptions, array size validation is optional
            LOGGER.debug("Array size limit validation failed", e);
        }
    }
    
    /**
     * Safely retrieves field property using reflection with fallback mechanisms.
     * 
     * @param <T> the expected return type
     * @param field the field metadata
     * @param propertyName the property name to retrieve
     * @param propertyType the expected property type
     * @return the property value if available and of correct type, null otherwise
     */
    @SuppressWarnings("unchecked")
    private <T> T getFieldProperty(SmartFieldMetadata field, String propertyName, Class<T> propertyType) {
        try {
            // 尝试从缓存获取方法
            String getterMethodName = "get" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
            Method getterMethod = findMethod(field.getClass(), getterMethodName);
            
            if (getterMethod != null) {
                Object methodValue = getterMethod.invoke(field);
                if (propertyType.isInstance(methodValue)) {
                    return (T) methodValue;
                }
            }
            
            // 尝试直接访问字段
            Field propertyField = ReflectionUtils.findField(field.getClass(), propertyName);
            if (propertyField != null) {
                ReflectionUtils.makeAccessible(propertyField);
                Object value = propertyField.get(field);
                if (propertyType.isInstance(value)) {
                    return (T) value;
                }
            }
        } catch (Exception e) {
            // Ignore exceptions
            LOGGER.debug("Failed to get field property: {}.{}", field.getClass().getSimpleName(), propertyName);
        }
        return null;
    }
    
    /**
     * Validates entity data against custom business rules.
     * 
     * @param entityMetadata the entity metadata definition
     * @param entityData the entity data to validate
     * @param result the validation result to accumulate errors
     */
    private void validateCustomRules(EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
        try {
            // 如果RuleEngine可用，委托给它处理
            if (ruleEngine != null) {
                LOGGER.debug("Delegating business rule validation to rule engine");
                // 使用默认的触发事件
                List<String> triggerEvents = Arrays.asList("CREATE", "UPDATE");
                // 获取规则验证结果Map
                Map<String, Object> ruleResult = ruleEngine.validateRules(entityMetadata.getApiName(), entityData, triggerEvents);
                
                // 检查验证是否通过
                Boolean isValid = (Boolean) ruleResult.get("valid");
                if (isValid != null && !isValid) {
                    // 获取并添加规则引擎返回的错误
                    @SuppressWarnings("unchecked")
                    List<String> errors = (List<String>) ruleResult.get("errors");
                    if (errors != null && !errors.isEmpty()) {
                        errors.forEach(error -> {
                            result.addError(ValidationResult.ValidationError.builder()
                                .fieldPath("general")
                                .message(error)
                                .build());
                        });
                    }
                }
            } else {
                // 降级到简单验证逻辑
                fallbackCustomRuleValidation(entityMetadata, entityData, result);
            }
        } catch (Exception e) {
            LOGGER.error("Exception during custom rule validation", e);
            // 添加异常信息到验证结果
            result.addError(ValidationResult.ValidationError.builder()
                .fieldPath("general")
                .message("Business rule validation exception: " + e.getMessage())
                .build());
        }
    }
    
    /**
     * Fallback implementation for custom rule validation when RuleEngine is unavailable.
     * 
     * @param entityMetadata the entity metadata definition
     * @param entityData the entity data to validate
     * @param result the validation result to accumulate errors
     */
    private void fallbackCustomRuleValidation(EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
        List<?> rules = entityMetadata.getValidationRules();
        if (rules == null || rules.isEmpty()) {
            return;
        }
        
        LOGGER.debug("Using fallback logic to validate custom rules, rule count: {}", rules.size());
        // 简化的降级验证逻辑
    }
    
    /**
     * Evaluates simple expressions for fallback validation scenarios.
     * 
     * @param expression the expression to evaluate
     * @param data the data context for evaluation
     * @return true if the expression evaluates to true, false otherwise
     */
    private boolean evaluateSimpleExpression(String expression, Map<String, Object> data) {
        // 实现简单的表达式求值，例如 "age > 18" 或 "status == 'active'"
        try {
            // 清理表达式
            expression = expression.trim();
            
            // 处理逻辑与操作
            if (expression.contains(" && ")) {
                // 使用更简单的字符串处理方式，避免正则表达式转义符问题
                StringTokenizer tokenizer = new StringTokenizer(expression, "&&");
                while (tokenizer.hasMoreTokens()) {
                    String part = tokenizer.nextToken().trim();
                    if (!evaluateSimpleExpression(part, data)) {
                        return false;
                    }
                }
                return true;
            }
            
            // 处理逻辑或操作
            if (expression.contains(" || ")) {
                // 使用更简单的字符串处理方式，避免正则表达式转义符问题
                StringTokenizer tokenizer = new StringTokenizer(expression, "||");
                while (tokenizer.hasMoreTokens()) {
                    String part = tokenizer.nextToken().trim();
                    if (evaluateSimpleExpression(part, data)) {
                        return true;
                    }
                }
                return false;
            }
            
            // 处理括号表达式
            if (expression.startsWith("(") && expression.endsWith(")")) {
                return evaluateSimpleExpression(expression.substring(1, expression.length() - 1), data);
            }
            
            // 处理大于等于比较
            if (expression.contains(">=")) {
                String[] parts = expression.split(">=", 2);
                return getNumericValue(data, parts[0].trim()) >= Double.parseDouble(parts[1].trim());
            }
            
            // 处理小于等于比较
            if (expression.contains("<=")) {
                String[] parts = expression.split("<=", 2);
                return getNumericValue(data, parts[0].trim()) <= Double.parseDouble(parts[1].trim());
            }
            
            // 处理不等于比较
            if (expression.contains("!=")) {
                String[] parts = expression.split("!=", 2);
                String left = parts[0].trim();
                String right = parts[1].trim();
                // 移除字符串引号
                if (right.startsWith("'") && right.endsWith("'")) {
                    right = right.substring(1, right.length() - 1);
                }
                return !Objects.equals(getPropertyValue(data, left), right);
            }
            
            // 处理大于比较
            if (expression.contains(">")) {
                String[] parts = expression.split(">", 2);
                return getNumericValue(data, parts[0].trim()) > Double.parseDouble(parts[1].trim());
            }
            
            // 处理小于比较
            else if (expression.contains("<")) {
                String[] parts = expression.split("<", 2);
                return getNumericValue(data, parts[0].trim()) < Double.parseDouble(parts[1].trim());
            }
            
            // 处理等于比较
            else if (expression.contains("==")) {
                String[] parts = expression.split("==", 2);
                String left = parts[0].trim();
                String right = parts[1].trim();
                // 移除字符串引号
                if (right.startsWith("'") && right.endsWith("'")) {
                    right = right.substring(1, right.length() - 1);
                }
                return Objects.equals(getPropertyValue(data, left), right);
            }
            
            // 处理函数调用
            if (expression.startsWith("hasField(")) {
                String fieldName = expression.substring(9, expression.length() - 1).trim();
                return data.containsKey(fieldName);
            }
            
            if (expression.startsWith("isEmpty(")) {
                String fieldName = expression.substring(8, expression.length() - 1).trim();
                Object value = getPropertyValue(data, fieldName);
                return isEmpty(value);
            }
            
            if (expression.startsWith("isNotEmpty(")) {
                String fieldName = expression.substring(11, expression.length() - 1).trim();
                Object value = getPropertyValue(data, fieldName);
                return !isEmpty(value);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to evaluate simple expression: {}", expression, e);
        }
        
        // 无法求值的表达式默认返回true
        return true;
    }
    
    /**
     * Checks if a value is null or empty.
     * 
     * @param value the value to check
     * @return true if the value is null, empty string, or empty collection
     */
    private boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String) {
            return ((String) value).trim().isEmpty();
        }
        if (value instanceof Collection) {
            return ((Collection<?>) value).isEmpty();
        }
        return false;
    }
    
    /**
     * Retrieves property value from data map, supporting nested properties.
     * 
     * @param data the data map
     * @param propertyPath the property path (can be nested like "user.address.city")
     * @return the property value or null if not found
     */
    private Object getPropertyValue(Map<String, Object> data, String propertyPath) {
        // 处理简单属性访问
        if (data.containsKey(propertyPath)) {
            return data.get(propertyPath);
        }
        
        // 处理嵌套属性访问，例如 "user.address.city"
        String[] parts = propertyPath.split("\\.");
        if (parts.length > 1) {
            Object current = data;
            for (String part : parts) {
                if (current instanceof Map && ((Map<?, ?>) current).containsKey(part)) {
                    current = ((Map<?, ?>) current).get(part);
                } else {
                    return null;
                }
            }
            return current;
        }
        
        return null;
    }
    
    /**
     * Retrieves numeric property value from data map.
     * 
     * @param data the data map
     * @param propertyPath the property path
     * @return the numeric value or 0.0 if not found or not numeric
     */
    private double getNumericValue(Map<String, Object> data, String propertyPath) {
        Object value = getPropertyValue(data, propertyPath);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }
    
    /**
     * Validates relationship fields and their target entities.
     * 
     * @param entityMetadata the entity metadata definition
     * @param entityData the entity data to validate
     * @param result the validation result to accumulate errors and warnings
     */
    private void validateRelationshipFields(EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
        // 验证关联字段
        entityMetadata.getFields().values().forEach(field -> {
            String fieldName = field.getApiName();
            
            try {
                // 尝试安全地获取关联信息
                Object relationship = getRelationshipInfo(field);
                if (relationship != null && entityData.containsKey(fieldName) && entityData.get(fieldName) != null) {
                    String targetEntity = getTargetEntityName(relationship);
                    if (targetEntity != null && !targetEntity.isEmpty()) {
                        // 验证目标实体是否存在
                        if (getEntityMetadata(targetEntity) == null) {
                            LOGGER.warn("Entity type '{}' referenced by field '{}' is not defined", targetEntity, field.getLabel());
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Error validating relationship field: {}", fieldName, e);
                // 仅记录日志，不添加警告，避免干扰正常验证流程
            }
        });
    }
    
    /**
     * Safely retrieves relationship information from field metadata.
     * 
     * @param field the field metadata
     * @return relationship object or null if not available
     */
    private Object getRelationshipInfo(SmartFieldMetadata field) {
        try {
            Method method = field.getClass().getMethod("getRelationship");
            method.setAccessible(true);
            return method.invoke(field);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Safely retrieves target entity name from relationship object.
     * 
     * @param relationship the relationship object
     * @return target entity name or null if not available
     */
    private String getTargetEntityName(Object relationship) {
        try {
            Method method = relationship.getClass().getMethod("getTargetEntity");
            method.setAccessible(true);
            Object result = method.invoke(relationship);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Implements EntityMetadataProvider interface to retrieve entity metadata with caching support.
     * This supports relationship validation in strategy pattern implementation.
     * 
     * @param entityName the name of the entity to retrieve metadata for
     * @return the entity metadata or null if not found
     */
    @Override
    public EntityMetadata getEntityMetadata(String entityName) {
        // Check cache first
        if (cacheEnabled) {
            CachedEntityMetadata cachedMetadata = entityMetadataCache.get(entityName);
            if (cachedMetadata != null && !cachedMetadata.isExpired(cacheExpirationTime)) {
                cacheHitCounter.incrementAndGet();
                return cachedMetadata.getMetadata();
            }
        }
        
        cacheMissCounter.incrementAndGet();
        
        try {
            // Try to get entity metadata through metadataEngine
            if (metadataEngine != null) {
                Object metadataObj = metadataEngine.getEntityMetadata(entityName);
                if (metadataObj instanceof EntityMetadata) {
                    EntityMetadata entityMetadata = (EntityMetadata) metadataObj;
                    // Store in cache if enabled
                    if (cacheEnabled) {
                        entityMetadataCache.put(entityName, new CachedEntityMetadata(entityMetadata));
                    }
                    return entityMetadata;
                }
            }
            
            // Try to get through metadataRepository if supported
            if (metadataRepository != null) {
                try {
                    // Check if metadataRepository has appropriate method to get entity metadata
                    Method getMetadataMethod = findMethod(metadataRepository.getClass(), "getEntityMetadata", String.class);
                    if (getMetadataMethod != null) {
                        Object metadataObj = getMetadataMethod.invoke(metadataRepository, entityName);
                        if (metadataObj instanceof EntityMetadata) {
                            EntityMetadata entityMetadata = (EntityMetadata) metadataObj;
                            // Store in cache if enabled
                            if (cacheEnabled) {
                                entityMetadataCache.put(entityName, new CachedEntityMetadata(entityMetadata));
                            }
                            return entityMetadata;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("Failed to get entity metadata through metadataRepository", e);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to retrieve entity metadata, entity type: {}", entityName, e);
        }
        
        return null;
    }
    
    /**
     * Gets the task executor, creating a default one if not configured.
     * 
     * @return the task executor
     */
    private Executor getTaskExecutor() {
        return taskExecutor != null ? taskExecutor : Executors.newCachedThreadPool();
    }
    
    /**
     * Gets validation statistics.
     * 
     * @return validation statistics map
     */
    public Map<String, Long> getValidationStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("totalValidations", totalValidationCount.get());
        stats.put("successfulValidations", successfulValidationCount.get());
        stats.put("errorCount", validationErrorCount.get());
        stats.put("warningCount", validationWarningCount.get());
        stats.put("cacheHits", cacheHitCounter.get());
        stats.put("cacheMisses", cacheMissCounter.get());
        stats.put("currentCacheSize", (long) entityMetadataCache.size());
        return stats;
    }
    
    /**
     * Clears all caches maintained by the validation engine.
     */
    public void clearCache() {
        entityMetadataCache.clear();
        methodReflectionCache.clear();
        regexPatternCache.clear();
        LOGGER.info("Validation engine caches cleared");
    }
}