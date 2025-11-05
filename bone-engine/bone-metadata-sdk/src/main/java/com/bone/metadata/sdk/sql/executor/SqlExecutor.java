package com.bone.metadata.sdk.sql.executor;

import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.domain.id.IdGenerator;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.BatchCompiledQuery;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.domain.query.CompositeQuery;
import com.bone.metadata.sdk.domain.spec.TableMetadataResolver;
import com.bone.metadata.sdk.sql.processor.SqlSecurityGuard;
import com.bone.metadata.sdk.support.config.SqlConfigProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
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
    private final IdGenerator idGenerator;
    private final SqlConfigProperties properties;

    @Autowired
    public SqlExecutor(NamedParameterJdbcOperations jdbc,
                       SqlConfigProperties properties) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC operations must not be null");
        this.properties = properties;
        this.idGenerator = new DefaultIdGenerator(jdbc);

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


    public <T> List<T> queryList(CompiledQuery query, Class<T> entityClass) {
        SqlSecurityGuard.scanForInjectionKeywords(query.getSql());
        // 仅对非简单类型进行字段校验
        if (!isSimpleType(entityClass)) {
            //SqlSecurityGuard.validateQueryParameters(sql, entityClass);
            //todo more
        }
        return jdbc.query(query.getSql(), query.getParameters(), new SmartRowMapper<>(entityClass));
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
    public <T> List<T> query(CompiledQuery query, Class<T> resultType) {
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
                Date.class.isAssignableFrom(type) ||
                java.time.temporal.Temporal.class.isAssignableFrom(type) ||
                type == Object.class;
    }

    /**
     * 执行预编译查询并返回Map列表
     */
    public List<Map<String, Object>> queryForMap(CompiledQuery query) {
        SqlSecurityGuard.scanForInjectionKeywords(query.getSql());
        return jdbc.queryForList(query.getSql(), query.getParameters());
    }

    /**
     * 执行预编译查询并返回单个对象
     */
    public <T> T querySingle(CompiledQuery query, Class<T> resultType) {
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
    public int update(CompiledQuery compiledQuery) {
        if (compiledQuery instanceof CompositeQuery) {
            return compositeUpdate((CompositeQuery) compiledQuery);
        }
        return jdbc.update(compiledQuery.getSql(), compiledQuery.getParameters());
    }

    /**
     * 执行批量更新
     */
    public int[] batchUpdate(BatchCompiledQuery query) {
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
    public <R> R insert(CompiledQuery query, Class<?> entityClass) {
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
     * 执行复合更新（多个SQL语句）
     */
    private int compositeUpdate(CompositeQuery compositeQuery) {
        int totalRows = 0;
        for (CompiledQuery segment : compositeQuery.getSegments()) {
            totalRows += jdbc.update(segment.getSql(), segment.getParameters());
        }
        return totalRows;
    }

    // 添加缺失的 count 方法
    public long count(CompiledQuery query) {
        try {
            Long result = queryForObject(query, Long.class);
            return result != null ? result : 0L;
        } catch (Exception e) {
            log.error("Count query failed: {}", query.getSql(), e);
            return 0L;
        }
    }


    // 参数转换工具方法
    private Map<String, Object> convertToParamMap(Object[] params) {
        Map<String, Object> paramMap = new HashMap<>();
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                paramMap.put("p" + i, params[i]);
            }
        }
        return paramMap;
    }

    // 构建 CompiledQuery 的便捷方法
    public CompiledQuery buildQuery(String sql, Object... params) {
        return new CompiledQuery(sql, convertToParamMap(params));
    }

    public int execute(String sql, Object... params) {
        CompiledQuery query = buildQuery(sql, params);
        return update(query);
    }
}