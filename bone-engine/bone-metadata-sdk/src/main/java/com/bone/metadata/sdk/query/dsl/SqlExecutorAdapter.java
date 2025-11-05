//package com.bone.metadata.sdk.query.dsl;
//
//import com.bone.core.model.PageResult;
//import com.bone.metadata.sdk.domain.query.CompiledQuery;
//import com.bone.metadata.sdk.sql.executor.SqlExecutor;
//import org.springframework.dao.DataAccessException;
//import org.springframework.dao.EmptyResultDataAccessException;
//
//import java.util.*;
//import java.util.function.Function;
//import java.util.stream.Collectors;
//
///**
// * 增强的SqlExecutor适配器
// * 提供类型安全的API、批量操作和更好的错误处理
// */
//public class SqlExecutorAdapter {
//
//    private final SqlExecutor sqlExecutor;
//
//    public SqlExecutorAdapter(SqlExecutor sqlExecutor) {
//        this.sqlExecutor = Objects.requireNonNull(sqlExecutor, "SqlExecutor cannot be null");
//    }
//
//    // ===== 查询操作 =====
//
//    /**
//     * 执行查询返回列表
//     */
//    public <T> List<T> query(CompiledQuery query, Class<T> entityClass) {
//        try {
//            return sqlExecutor.executeQuery(query, entityClass);
//        } catch (DataAccessException e) {
//            throw new QueryExecutionException("Failed to execute query", e, query);
//        }
//    }
//
//    /**
//     * 从SQL字符串执行查询
//     */
//    public <T> List<T> query(String sql, Class<T> entityClass, Object... params) {
//        CompiledQuery query = buildQuery(sql, params);
//        return query(query, entityClass);
//    }
//
//    /**
//     * 执行查询返回单个对象
//     */
//    public <T> Optional<T> querySingle(CompiledQuery query, Class<T> entityClass) {
//        try {
//            List<T> results = sqlExecutor.executeQuery(query, entityClass);
//            return switch (results.size()) {
//                case 0 -> Optional.empty();
//                case 1 -> Optional.of(results.get(0));
//                default -> {
//                    System.err.printf("Warning: Query returned %d results but expected single%n", results.size());
//                    yield Optional.of(results.get(0));
//                }
//            };
//        } catch (EmptyResultDataAccessException e) {
//            return Optional.empty();
//        } catch (DataAccessException e) {
//            throw new QueryExecutionException("Failed to execute single query", e, query);
//        }
//    }
//
//    /**
//     * 执行查询并映射结果
//     */
//    public <T, R> List<R> queryAndMap(CompiledQuery query, Class<T> entityClass, Function<T, R> mapper) {
//        List<T> results = query(query, entityClass);
//        return results.stream().map(mapper).collect(Collectors.toList());
//    }
//
//    /**
//     * 执行查询返回Map列表
//     */
//    public List<Map<String, Object>> queryForList(CompiledQuery query) {
//        try {
//            // 修复类型转换问题
//            List<Map<String, Object>> results = new ArrayList<>();
//            List<?> rawResults = sqlExecutor.executeQuery(query, Map.class);
//
//            for (Object result : rawResults) {
//                if (result instanceof Map) {
//                    @SuppressWarnings("unchecked")
//                    Map<String, Object> mapResult = (Map<String, Object>) result;
//                    results.add(mapResult);
//                }
//            }
//            return results;
//        } catch (DataAccessException e) {
//            throw new QueryExecutionException("Failed to execute map query", e, query);
//        }
//    }
//
//    /**
//     * 执行查询返回单个值
//     */
//    public <T> Optional<T> queryForObject(CompiledQuery query, Class<T> requiredType) {
//        try {
//            T result = sqlExecutor.queryForObject(query, requiredType);
//            return Optional.ofNullable(result);
//        } catch (EmptyResultDataAccessException e) {
//            return Optional.empty();
//        } catch (DataAccessException e) {
//            throw new QueryExecutionException("Failed to execute object query", e, query);
//        }
//    }
//
//    // ===== 聚合操作 =====
//
//    /**
//     * 执行计数查询
//     */
//    public long count(CompiledQuery query) {
//        return queryForObject(query, Long.class).orElse(0L);
//    }
//
//    /**
//     * 检查是否存在记录
//     */
//    public boolean exists(CompiledQuery query) {
//        return count(query) > 0;
//    }
//
//    /**
//     * 执行求和聚合
//     */
//    public <T extends Number> Optional<T> sum(CompiledQuery query, Class<T> numberType) {
//        return queryForObject(query, numberType);
//    }
//
//    /**
//     * 执行平均值聚合
//     */
//    public Optional<Double> avg(CompiledQuery query) {
//        return queryForObject(query, Double.class);
//    }
//
//    /**
//     * 执行最大值查询
//     */
//    public <T> Optional<T> max(CompiledQuery query, Class<T> resultType) {
//        return queryForObject(query, resultType);
//    }
//
//    /**
//     * 执行最小值查询
//     */
//    public <T> Optional<T> min(CompiledQuery query, Class<T> resultType) {
//        return queryForObject(query, resultType);
//    }
//
//    // ===== 更新操作 =====
//
//    /**
//     * 执行更新操作
//     */
//    public int update(CompiledQuery query) {
//        try {
//            return sqlExecutor.executeUpdate(query);
//        } catch (DataAccessException e) {
//            throw new QueryExecutionException("Failed to execute update", e, query);
//        }
//    }
//
//    /**
//     * 执行插入操作
//     */
//    public int insert(CompiledQuery query) {
//        return update(query);
//    }
//
//    /**
//     * 执行删除操作
//     */
//    public int delete(CompiledQuery query) {
//        return update(query);
//    }
//
//    /**
//     * 从SQL字符串执行更新
//     */
//    public int execute(String sql, Object... params) {
//        CompiledQuery query = buildQuery(sql, params);
//        return update(query);
//    }
//
//    /**
//     * 批量执行操作
//     */
//    public int[] batchUpdate(List<CompiledQuery> queries) {
//        if (queries == null || queries.isEmpty()) {
//            return new int[0];
//        }
//
//        int[] results = new int[queries.size()];
//        for (int i = 0; i < queries.size(); i++) {
//            results[i] = update(queries.get(i));
//        }
//        return results;
//    }
//
//    /**
//     * 批量执行相同的SQL（不同参数）
//     */
//    public int[] batchExecute(String sql, List<Object[]> paramList) {
//        if (paramList == null || paramList.isEmpty()) {
//            return new int[0];
//        }
//
//        List<CompiledQuery> queries = paramList.stream()
//                .map(params -> buildQuery(sql, params))
//                .collect(Collectors.toList());
//
//        return batchUpdate(queries);
//    }
//
//    // ===== 分页操作 =====
//
//    /**
//     * 执行分页查询
//     */
//    public <T> PageResult<T> queryPage(CompiledQuery dataQuery, CompiledQuery countQuery,
//                                       Class<T> entityClass, int pageNum, int pageSize) {
//        List<T> content = query(dataQuery, entityClass);
//        long total = count(countQuery);
//        return PageResult.of(content, total, pageNum, pageSize);
//    }
//
//    // ===== 事务操作 =====
//
//    /**
//     * 在事务中执行操作
//     */
//    public <T> T executeInTransaction(TransactionalOperation<T> operation) {
//        // 这里假设SqlExecutor已经配置了事务管理
//        // 实际实现可能需要依赖Spring的@Transactional或手动事务管理
//        return operation.execute();
//    }
//
//    /**
//     * 事务操作接口
//     */
//    @FunctionalInterface
//    public interface TransactionalOperation<T> {
//        T execute();
//    }
//
//    // ===== 工具方法 =====
//
//    /**
//     * 构建编译查询
//     */
//    public CompiledQuery buildQuery(String sql, Object... params) {
//        Map<String, Object> paramMap = convertParamsToMap(params);
//        return new CompiledQuery(sql, paramMap);
//    }
//
//    /**
//     * 检查SQL是否有效（简单的语法检查）
//     */
//    public boolean isValidSql(String sql) {
//        if (sql == null || sql.trim().isEmpty()) {
//            return false;
//        }
//
//        String upperSql = sql.toUpperCase().trim();
//        // 简单的关键字检查
//        return upperSql.startsWith("SELECT") || upperSql.startsWith("INSERT") ||
//                upperSql.startsWith("UPDATE") || upperSql.startsWith("DELETE");
//    }
//
//    /**
//     * 将参数数组转换为Map
//     */
//    private Map<String, Object> convertParamsToMap(Object[] params) {
//        Map<String, Object> paramMap = new HashMap<>();
//
//        if (params == null || params.length == 0) {
//            return paramMap;
//        }
//
//        // 处理单个Map参数的情况
//        if (params.length == 1 && params[0] instanceof Map) {
//            @SuppressWarnings("unchecked")
//            Map<String, Object> mapParam = (Map<String, Object>) params[0];
//            paramMap.putAll(mapParam);
//        } else {
//            // 处理普通参数数组
//            for (int i = 0; i < params.length; i++) {
//                paramMap.put("p" + i, params[i]);
//            }
//        }
//
//        return paramMap;
//    }
//
//    /**
//     * 获取底层SqlExecutor
//     */
//    public SqlExecutor getSqlExecutor() {
//        return sqlExecutor;
//    }
//
//    /**
//     * 查询执行异常
//     */
//    public static class QueryExecutionException extends RuntimeException {
//        private final CompiledQuery query;
//
//        public QueryExecutionException(String message, Throwable cause, CompiledQuery query) {
//            super(message, cause);
//            this.query = query;
//        }
//
//        public CompiledQuery getQuery() {
//            return query;
//        }
//
//        @Override
//        public String getMessage() {
//            return super.getMessage() + " [SQL: " + query.getSql() + "]";
//        }
//    }
//}