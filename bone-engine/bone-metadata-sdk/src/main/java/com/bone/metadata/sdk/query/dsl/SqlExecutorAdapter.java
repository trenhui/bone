package com.bone.metadata.sdk.query.dsl;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SqlExecutor适配器
 * 100%复用现有SqlExecutor，零新增执行逻辑
 */
public class SqlExecutorAdapter {

    private final SqlExecutor sqlExecutor;

    public SqlExecutorAdapter(SqlExecutor sqlExecutor) {
        this.sqlExecutor = sqlExecutor;
    }

    /**
     * 执行查询返回列表
     */
    public <T> List<T> execute(CompiledQuery query, Class<T> entityClass) {
        return sqlExecutor.executeQuery(query, entityClass);
    }

    /**
     * 执行计数查询
     */
    public long executeCount(CompiledQuery query) {
        Long count = sqlExecutor.queryForObject(query, Long.class);
        return count != null ? count : 0L;
    }

    /**
     * 执行聚合查询
     */
    public <R> R executeAggregate(CompiledQuery query, Class<R> resultType) {
        return sqlExecutor.queryForObject(query, resultType);
    }
    
    /**
     * 执行更新操作
     */
    public int executeUpdate(CompiledQuery query) {
        return sqlExecutor.executeUpdate(query);
    }

    /**
     * 执行分页查询
     */
    public <T> PageResult<T> page(CompiledQuery query, Class<T> entityClass,
                                         int pageNum, int pageSize) {
        // 查询当前页
        List<T> content = sqlExecutor.executeQuery(query, entityClass);
       //todo  content.forEach(this::loadExtensionFields);
        Long total= sqlExecutor.queryForObject(query, Long.class);
        return  PageResult.of(content, total, pageNum, pageSize);
    }
    
    /**
     * 兼容旧版API的update方法，用于测试
     */
    public int update(String sql, Object... params) {
        CompiledQuery query = new CompiledQuery(sql, convertParamsToMap(params));
        return executeUpdate(query);
    }
    
    /**
     * 将参数数组或列表转换为Map
     */
    private Map<String, Object> convertParamsToMap(Object params) {
        Map<String, Object> paramMap = new java.util.HashMap<>();
        
        if (params == null) {
            return paramMap;
        }
        
        if (params instanceof Object[]) {
            Object[] paramArray = (Object[]) params;
            for (int i = 0; i < paramArray.length; i++) {
                paramMap.put("param" + i, paramArray[i]);
            }
        } else if (params instanceof List) {
            List<?> paramList = (List<?>) params;
            for (int i = 0; i < paramList.size(); i++) {
                paramMap.put("param" + i, paramList.get(i));
            }
        }
        
        return paramMap;
    }

    /**
     * 获取底层SqlExecutor（用于特殊场景）
     */
    public SqlExecutor getSqlExecutor() {
        return sqlExecutor;
    }
}