package com.bone.metadata.sdk.sql.executor;

import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.domain.id.IdGenerator;
import com.bone.core.result.PageResult;
import com.bone.metadata.sdk.domain.exception.QueryExecutionException;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.BatchCompiledQuery;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.domain.query.CompositeQuery;
import com.bone.metadata.sdk.domain.spec.TableMetadataResolver;
import com.bone.metadata.sdk.sql.processor.*;
import com.bone.metadata.sdk.sql.template.SqlTemplate;
import com.bone.metadata.sdk.sql.template.SqlTemplateLoader;
import com.bone.metadata.sdk.support.config.SqlConfigProperties;
import com.bone.metadata.sdk.support.util.ParamConvertUtil;
import com.bone.metadata.sdk.support.util.SqlUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于模板的高性能 SQL 执行器
 * 支持动态 SQL 处理、多数据库方言、分页查询、批量操作、缓存和监控
 */
@Slf4j
@Transactional
public class SqlExecutor {

    private final NamedParameterJdbcOperations jdbc;
    private final SqlTemplateLoader sqlTemplateLoader;
    private final SqlProcessorFactory sqlProcessorFactory;
    private final IdGenerator idGenerator;
    private final SqlConfigProperties properties;
    private final Cache<String, ProcessedSql> sqlCache;

    @Autowired
    public SqlExecutor(NamedParameterJdbcOperations jdbc,
                       SqlTemplateLoader sqlTemplateLoader,
                       SqlProcessorFactory sqlProcessorFactory,
                       SqlConfigProperties properties) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC operations must not be null");
        this.sqlTemplateLoader = Objects.requireNonNull(sqlTemplateLoader, "SQL template loader must not be null");
        this.sqlProcessorFactory = Objects.requireNonNull(sqlProcessorFactory, "SQL processor factory must not be null");
        this.properties = properties;
        this.idGenerator = new DefaultIdGenerator(jdbc);

        // 配置 SQL 处理结果缓存
        this.sqlCache = Caffeine.newBuilder()
                .maximumSize(properties.getTemplate().getCacheSize())
                .expireAfterWrite(properties.getCache().getExpireHours(), TimeUnit.HOURS)
                .recordStats()
                .build();

        // 设置默认获取大小以优化大结果集 (仅当底层是 JdbcTemplate 时才设置)
        if (jdbc.getJdbcOperations() instanceof JdbcTemplate) {
            ((JdbcTemplate) jdbc.getJdbcOperations()).setFetchSize(100);
        }
    }

    /**
     * 生成ID
     */
    public Object generateId(GenerationStrategy strategy, Object entity) {
        return idGenerator.generateId(strategy, entity);
    }


    /**
     * 执行查询并返回对象列表（带自定义RowMapper）
     */
    @Cacheable(cacheNames = "sqlQueries", key = "#templateId + #parameters.toString() + #entityClass.getName()")
    public <T, R> List<R> execute(String templateId, Map<String, Object> parameters,
                                  Class<T> entityClass, RowMapper<R> rowMapper) {
        return executeInternal(templateId, parameters, entityClass, processedSql -> {
            String sql = processedSql.getSql();
            if (!isSelectQuery(sql)) {
                throw new QueryExecutionException("Only SELECT operations are supported with custom RowMapper");
            }
            return jdbc.query(sql, processParameters(processedSql.getEffectiveParams()), rowMapper);
        });
    }

    /**
     * 执行分页查询（基于实体类自动映射）
     */
    @Transactional(readOnly = true)
    public <T> PageResult<T> executePaged(
            String templateId,
            Map<String, Object> parameters,
            Class<T> entityClass,
            Integer pageNumber,
            Integer pageSize
    ) {
        validatePaginationParams(pageNumber, pageSize);
        String tableName = SqlUtil.toSnakeCase(entityClass.getSimpleName());
        SqlTemplate template = loadSqlTemplate(tableName + "/" + templateId);
        Map<String, Object> safeParameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();

        ProcessedSql processedSql = processSqlByTemplate(templateId, template, safeParameters);
        String originalSql = processedSql.getSql();
        Map<String, Object> effectiveParams = processedSql.getEffectiveParams();

        int offset = (pageNumber - 1) * pageSize;
        String pagedSql = buildPagedSql(originalSql, pageSize, offset);

        RowMapper<T> rowMapper = new SmartRowMapper<>(entityClass);
        List<T> content = jdbc.query(pagedSql, effectiveParams, rowMapper);
        Long total = executeCountQuery(templateId, template, safeParameters);

        return new PageResult<>(content, pageNumber, pageSize, total);
    }

    /**
     * 执行分页查询（基于自定义RowMapper）
     */
    @Transactional(readOnly = true)
    public <T> PageResult<T> executePaged(
            String templateId,
            Map<String, Object> parameters,
            RowMapper<T> rowMapper,
            int pageNumber,
            int pageSize
    ) {
        validatePaginationParams(pageNumber, pageSize);
        String tableName = "Default";
        SqlTemplate template = loadSqlTemplate(tableName + "/" + templateId);
        Map<String, Object> safeParameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();

        ProcessedSql processedSql = processSqlByTemplate(templateId, template, safeParameters);
        String originalSql = processedSql.getSql();
        Map<String, Object> effectiveParams = processedSql.getEffectiveParams();

        int offset = (pageNumber - 1) * pageSize;
        String pagedSql = buildPagedSql(originalSql, pageSize, offset);

        List<T> content = jdbc.query(pagedSql, effectiveParams, rowMapper);
        Long total = executeCountQuery(templateId, template, safeParameters);

        return new PageResult<>(content, pageNumber, pageSize, total);
    }

    /**
     * 执行分页查询（实体类+自定义RowMapper）
     */
    @Transactional(readOnly = true)
    public <T, R> PageResult<R> executePaged(
            String templateId,
            Map<String, Object> parameters,
            Class<T> entityClass,
            RowMapper<R> rowMapper,
            int pageNumber,
            int pageSize
    ) {
        validatePaginationParams(pageNumber, pageSize);
        String tableName = SqlUtil.toSnakeCase(entityClass.getSimpleName());
        SqlTemplate template = loadSqlTemplate(tableName + "/" + templateId);
        Map<String, Object> safeParameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();

        ProcessedSql processedSql = processSqlByTemplate(templateId, template, safeParameters);
        String originalSql = processedSql.getSql();
        Map<String, Object> effectiveParams = processedSql.getEffectiveParams();

        int offset = (pageNumber - 1) * pageSize;
        String pagedSql = buildPagedSql(originalSql, pageSize, offset);

        List<R> content = jdbc.query(pagedSql, effectiveParams, rowMapper);
        Long total = executeCountQuery(templateId, template, safeParameters);

        return new PageResult<>(content, pageNumber, pageSize, total);
    }

    /**
     * 执行SQL并自动判断返回类型（查询返回列表，DML返回影响行数）
     */
    @SuppressWarnings("unchecked")
    public <R, T> R execute(String templateId, Map<String, Object> parameters, Class<T> entityClass) {
        return executeInternal(templateId, parameters, entityClass, processedSql -> {
            Map<String, Object> params = processedSql.getEffectiveParams();
            SqlParameterSource parameterSource = processParameters(params);
            String finalSql = processedSql.getSql();

            if (isSelectQuery(finalSql)) {
                List<T> result = jdbc.query(finalSql, parameterSource, new SmartRowMapper<>(entityClass));
                return (R) result;
            } else if (isDmlQuery(finalSql)) {
                int affectedRows = jdbc.update(finalSql, parameterSource);
                return (R) Integer.valueOf(affectedRows);
            } else {
                throw new QueryExecutionException("Unsupported SQL operation or DML not allowed: " + finalSql);
            }
        });
    }

    /**
     * 执行查询并返回Map列表
     */
    public List<Map<String, Object>> executeForMap(String templateId, Map<String, Object> parameters, Class<?> entityClass) {
        return executeInternal(templateId, parameters, entityClass, processedSql -> {
            Map<String, Object> params = processedSql.getEffectiveParams();
            SqlParameterSource parameterSource = processParameters(params);
            String finalSql = processedSql.getSql();

            if (isSelectQuery(finalSql)) {
                return jdbc.queryForList(finalSql, parameterSource);
            } else {
                throw new QueryExecutionException("Unsupported SQL operation or DML not allowed: " + finalSql);
            }
        });
    }

    public <T> List<T> queryForList(String sql, Map<String, Object> parameters, Class<T> entityClass) {
        SqlSecurityGuard.scanForInjectionKeywords(sql);
        // 仅对非简单类型进行字段校验
        if (!isSimpleType(entityClass)) {
            //SqlSecurityGuard.validateQueryParameters(sql, entityClass);
            //todo more
        }
        return jdbc.query(sql, parameters, new SmartRowMapper<>(entityClass));
    }

    private Set<String> extractUsedParameters(String sql) {
        Set<String> usedParams = new HashSet<>();
        Pattern pattern = Pattern.compile(":#\\{([^}]+)}");
        Matcher matcher = pattern.matcher(sql);

        while (matcher.find()) {
            usedParams.add(matcher.group(1));
        }

        return usedParams;
    }

    /**
     * 执行预编译查询
     */
    public <T> List<T> executeQuery(CompiledQuery query, Class<T> resultType) {
        // 只验证SQL中实际使用的参数，而不是所有参数
        Set<String> usedParams = extractUsedParameters(query.getSql());
        Map<String, Object> filteredParams = new HashMap<>();

        for (String usedParam : usedParams) {
            if (query.getParameters().containsKey(usedParam)) {
                filteredParams.put(usedParam, query.getParameters().get(usedParam));
            }
        }

        // 使用过滤后的参数进行验证
        SqlSecurityGuard.scanForInjectionKeywords(query.getSql());
        // 仅对非简单类型进行字段校验
        SqlSecurityGuard.validateQueryParameters(filteredParams, resultType);
        SqlParameterSource parameterSource = new NestedMapSqlParameterSource(query.getParameters());
        return jdbc.query(query.getSql(), parameterSource, new SmartRowMapper<>(resultType));
        //return jdbc.query(query.getSql(), query.getParameters(), new SmartRowMapper<>(resultType));
    }

    // 简单类型判断（与MethodHandler保持一致）
    private boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() ||
                Number.class.isAssignableFrom(type) ||
                CharSequence.class.isAssignableFrom(type) ||
                Boolean.class.equals(type) ||
                java.util.Date.class.isAssignableFrom(type) ||
                java.time.temporal.Temporal.class.isAssignableFrom(type) ||
                type == Object.class;
    }

    /**
     * 执行预编译查询并返回Map列表
     */
    public List<Map<String, Object>> executeQueryForMap(CompiledQuery query) {
        SqlSecurityGuard.scanForInjectionKeywords(query.getSql());
        return jdbc.queryForList(query.getSql(), query.getParameters());
    }

    /**
     * 执行预编译查询并返回单个对象
     */
    public <T> T executeSingleQuery(CompiledQuery query, Class<T> resultType) {
        try {
            SqlSecurityGuard.scanForInjectionKeywords(query.getSql());
            // 仅对非简单类型进行字段校验
            if (!isSimpleType(resultType)) {
                SqlSecurityGuard.validateQueryParameters(query, resultType);
            }
            return jdbc.queryForObject(
                    query.getSql(),
                    query.getParameters(),
                    new SmartRowMapper<>(resultType)
            );
        } catch (EmptyResultDataAccessException e) {
            log.warn("No result found for query: {}", query.getSql());
            return null;
        }
    }

    /**
     * 执行更新操作
     */
    public int executeUpdate(CompiledQuery compiledQuery) {
        if (compiledQuery instanceof CompositeQuery) {
            return executeCompositeUpdate((CompositeQuery) compiledQuery);
        }
        return jdbc.update(compiledQuery.getSql(), compiledQuery.getParameters());
    }

    /**
     * 执行批量更新
     */
    public int[] executeBatchUpdate(BatchCompiledQuery query) {
        SqlSecurityGuard.scanForInjectionKeywords(query.getSql());
        return jdbc.batchUpdate(query.getSql(),
                SqlParameterSourceUtils.createBatch(query.getBatchParameters()));
    }

    /**
     * 执行查询并返回指定类型的单个对象
     */
    public <R> R queryForObject(CompiledQuery query, Class<R> requiredType) {
        try {
            SqlSecurityGuard.scanForInjectionKeywords(query.getSql());
            return jdbc.queryForObject(query.getSql(), query.getParameters(), requiredType);
        } catch (EmptyResultDataAccessException e) {
            log.warn("No result found for query: {}", query.getSql());
            return null;
        }
    }

    /**
     * 执行插入操作并返回生成的主键
     */
    public <R> R executeInsert(CompiledQuery query, Class<?> entityClass) {
        SqlSecurityGuard.scanForInjectionKeywords(query.getSql());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        TableMetadata tableMetadata = TableMetadataResolver.load(entityClass);
        jdbc.update(query.getSql(),
                new MapSqlParameterSource(query.getParameters()),
                keyHolder,
                new String[]{tableMetadata.getPrimaryKey().getName()});
        @SuppressWarnings("unchecked")
        R key = (R) keyHolder.getKey();
        return key;
    }

    /**
     * 执行原始SQL（数组参数）
     */
    public List<Map<String, Object>> executeRawQueryForMap(String sql, Object[] params) {
        try {
            SqlSecurityGuard.scanForInjectionKeywords(sql);
            Map<String, Object> paramMap = new HashMap<>();
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    paramMap.put("param" + i, params[i]);
                }
            }
            return jdbc.queryForList(sql, paramMap);
        } catch (Exception e) {
            log.error("Failed to execute raw SQL: {}", sql, e);
            throw new QueryExecutionException("Failed to execute raw SQL: " + sql, e);
        }
    }

    /**
     * 执行原始SQL（命名参数）
     */
    public List<Map<String, Object>> executeRawQueryForMap(String sql, Map<String, Object> paramMap) {
        try {
            SqlSecurityGuard.scanForInjectionKeywords(sql);
            return jdbc.queryForList(sql, paramMap);
        } catch (Exception e) {
            log.error("Failed to execute raw SQL: {}", sql, e);
            throw new QueryExecutionException("Failed to execute raw SQL: " + sql, e);
        }
    }

    /**
     * 执行批量更新（基于模板和实体列表）
     */
    @Transactional
    public <T> int[] executeBatchUpdate(String templateId, List<T> entities) {
        validateInputs(templateId, Map.of(), Void.class);
        try {
            SqlTemplate template = loadSqlTemplate(templateId);
            List<Map<String, Object>> batchParams = entities.stream()
                    .map(ParamConvertUtil::toParamMap)
                    .toList();

            ProcessedSql processedSql = processSqlByTemplate(templateId, template, batchParams.get(0));
            log.debug("Executing batch update: {}", processedSql.getSql());

            int batchSize = properties.getDatabase().getBatchSize();

            return batchProcess(batchParams, batchSize, batch ->
                    jdbc.batchUpdate(processedSql.getSql(), batch.toArray(new Map[0]))
            );
        } catch (Exception e) {
            log.error("Batch update failed, template: {}, error: {}", templateId, e.getMessage(), e);
            throw new QueryExecutionException("Batch update failed: " + templateId, e);
        }
    }

    /**
     * 清空SQL缓存
     */
    public void clearCache() {
        sqlCache.invalidateAll();
        log.info("SQL cache cleared");
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStats getCacheStats() {
        return sqlCache.stats();
    }

    // ========== 私有方法 ==========

    /**
     * 内部执行方法（统一处理模板加载、SQL处理和异常处理）
     */
    private <R, T> R executeInternal(String templateId, Map<String, Object> parameters, Class<T> entityClass,
                                     Function<ProcessedSql, R> executorFunction) {
        validateInputs(templateId, parameters, entityClass);

        try {
            String tableName = SqlUtil.toSnakeCase(entityClass.getSimpleName());
            SqlTemplate template = loadSqlTemplate(tableName + "/" + templateId);
            Map<String, Object> safeParameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();

            ProcessedSql processedSql = getProcessedSqlWithCache(templateId, template, safeParameters);
            SqlSecurityGuard.scanForInjectionKeywords(processedSql.getSql());

            return executorFunction.apply(processedSql);

        } catch (Exception e) {
            throw new QueryExecutionException("Failed to execute query for template: " + templateId, e);
        }
    }

    /**
     * 从缓存获取或处理SQL
     */
    private ProcessedSql getProcessedSqlWithCache(String templateId, SqlTemplate template, Map<String, Object> params) {
        String cacheKey = templateId + ":" + template.getSqlTemplateType() + ":" + params.hashCode();
        return sqlCache.get(cacheKey, key -> processSqlByTemplate(templateId, template, params));
    }

    /**
     * 根据模板类型处理SQL
     */
    private ProcessedSql processSqlByTemplate(String templateId, SqlTemplate template, Map<String, Object> params) {
        if (!template.isDynamic()) {
            return new ProcessedSql(template.getSql(), params);
        }
        SqlProcessor processor = sqlProcessorFactory.getProcessor(template.getSqlTemplateType());
        return processor.process(templateId, template.getSql(), params);
    }

    /**
     * 构建分页SQL（支持多种数据库方言）
     */
    private String buildPagedSql(String sql, int pageSize, int offset) {
        String dialect = properties.getDatabase().getType();

        switch (dialect.toLowerCase()) {
            case "mysql":
            case "mariadb":
            case "sqlite":
                return String.format("%s LIMIT %d OFFSET %d", sql, pageSize, offset);
            case "postgresql":
                return String.format("%s OFFSET %d LIMIT %d", sql, offset, pageSize);
            case "oracle":
                return String.format(
                        "SELECT * FROM (SELECT t.*, ROWNUM rn FROM (%s) t WHERE ROWNUM <= %d) WHERE rn > %d",
                        sql, (offset + pageSize), offset);
            case "sqlserver":
                return String.format("%s ORDER BY 1 OFFSET %d ROWS FETCH NEXT %d ROWS ONLY", sql, offset, pageSize);
            default:
                return String.format("%s LIMIT %d OFFSET %d", sql, pageSize, offset);
        }
    }

    /**
     * 执行计数查询
     */
    private Long executeCountQuery(String templateId, SqlTemplate template, Map<String, Object> params) {
        try {
            String countSqlId = templateId + "_count";
            SqlTemplate countTemplate = loadSqlTemplate(countSqlId);
            ProcessedSql processedCountSql = processSqlByTemplate(countSqlId, countTemplate, params);
            return jdbc.queryForObject(processedCountSql.getSql(), processedCountSql.getEffectiveParams(), Long.class);
        } catch (Exception e) {
            log.debug("Count template not found, using fallback count query for template: {}", templateId);
            ProcessedSql processedSql = processSqlByTemplate(templateId, template, params);
            String countQuery = "SELECT COUNT(*) FROM (" + processedSql.getSql() + ") count_table";
            return jdbc.queryForObject(countQuery, processedSql.getEffectiveParams(), Long.class);
        }
    }

    /**
     * 处理参数并检查SQL注入
     */
    private SqlParameterSource processParameters(Map<String, Object> usedParams) {
        MapSqlParameterSource parameterSource = new MapSqlParameterSource();
        if (usedParams != null) {
            usedParams.forEach((key, value) -> {
                if (value instanceof String str) {
                    SqlSecurityGuard.scanForInjectionKeywords(str);
                }
                parameterSource.addValue(key, value);
            });
        }
        return parameterSource;
    }

    /**
     * 加载SQL模板
     */
    private SqlTemplate loadSqlTemplate(String templateId) {
        try {
            return sqlTemplateLoader.loadTemplate(templateId);
        } catch (Exception e) {
            throw new QueryExecutionException("Failed to load template: " + templateId, e);
        }
    }

    /**
     * 执行复合更新（多个SQL语句）
     */
    private int executeCompositeUpdate(CompositeQuery compositeQuery) {
        int totalRows = 0;
        for (CompiledQuery segment : compositeQuery.getSegments()) {
            totalRows += jdbc.update(segment.getSql(), segment.getParameters());
        }
        return totalRows;
    }

    /**
     * 批量处理
     */
    private <T> int[] batchProcess(List<Map<String, Object>> params, int batchSize,
                                   Function<List<Map<String, Object>>, int[]> processor) {
        int[] results = new int[params.size()];
        for (int i = 0; i < params.size(); i += batchSize) {
            List<Map<String, Object>> batch = params.subList(i, Math.min(i + batchSize, params.size()));
            int[] batchResults = processor.apply(batch);
            System.arraycopy(batchResults, 0, results, i, batchResults.length);
        }
        return results;
    }


    /**
     * 验证输入参数
     */
    private void validateInputs(String templateId, Map<String, Object> params, Class<?> entityClass) {
        Assert.hasText(templateId, "Template ID must not be null or empty");
        Assert.notNull(params, "Parameters must not be null");
        Assert.notNull(entityClass, "Entity class must not be null");
    }

    /**
     * 验证分页参数
     */
    private void validatePaginationParams(int pageNumber, int pageSize) {
        Assert.isTrue(pageNumber >= 1, "Page number must be >= 1");
        Assert.isTrue(pageSize >= 1 && pageSize <= 1000, "Page size must be between 1 and 1000");
    }

    /**
     * 判断是否为SELECT查询
     */
    private boolean isSelectQuery(String sql) {
        String normalized = sql.trim().toUpperCase().replaceAll("\\s+", " ");
        return normalized.startsWith("SELECT ") || normalized.startsWith("WITH ");
    }

    /**
     * 判断是否为DML操作
     */
    private boolean isDmlQuery(String sql) {
        String normalized = sql.trim().toUpperCase().replaceAll("\\s+", " ");
        return normalized.startsWith("INSERT ") || normalized.startsWith("UPDATE ") || normalized.startsWith("DELETE ");
    }
}