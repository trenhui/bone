package com.bone.metadata.sdk.query;

import com.bone.metadata.sdk.domain.enums.QueryType;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.BatchCompiledQuery;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.domain.spec.TableMetadataResolver;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.query.builder.*;
import com.bone.metadata.sdk.query.context.*;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.criteria.Condition;
import com.bone.metadata.sdk.sql.dialect.DatabaseDialect;
import com.bone.metadata.sdk.domain.exception.SqlInjectionRiskException;
import com.bone.metadata.sdk.support.config.MetadataSdkContext;
import com.bone.metadata.sdk.support.util.RepositoryClassUtils;
import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class SqlBuilder {

    private static final Logger log = LoggerFactory.getLogger(SqlBuilder.class);
    private final MetadataService metadataService;
    private final DatabaseDialect dialect;
    private final Map<QueryType, Object> builders = new EnumMap<>(QueryType.class);

    public SqlBuilder(MetadataService metadataService,
                      DatabaseDialect dialect) {
        this.metadataService = metadataService;
        this.dialect = dialect;
    }

    @PostConstruct
    public void init() {
        builders.put(QueryType.SELECT, new SelectSqlBuilder(metadataService, dialect));
        builders.put(QueryType.COUNT, new CountSqlBuilder(metadataService));
        builders.put(QueryType.BATCH_INSERT, new BatchInsertBuilder());
        builders.put(QueryType.BATCH_UPDATE, new BatchUpdateBuilder());
        builders.put(QueryType.DYNAMIC_UPDATE, new DynamicUpdateSqlBuilder());
        builders.put(QueryType.CONDITIONAL_UPDATE, new ConditionalUpdateSqlBuilder());
        builders.put(QueryType.DELETE, new DeleteSqlBuilder());
        builders.put(QueryType.AGGREGATION, new AggregationSqlBuilder());
        builders.put(QueryType.UPSERT, new UpsertSqlBuilder());
        builders.put(QueryType.COUNT_AGGREGATION, new CountAggregationSqlBuilder()); // 新增计数聚合构建器
    }

    @SuppressWarnings("unchecked")
    public CompiledQuery buildSelect(Class<?> cls, Criteria<?> c, boolean includeDeleted) {
        TableMetadata t = TableMetadataResolver.load(cls);
        // 验证用户输入的条件是否安全
        validateCriteriaSafety(c);
        return ((SqlQueryBuilder<SelectContext>) builders.get(QueryType.SELECT))
                .build(new SelectContext(t, c, includeDeleted));
    }

    @SuppressWarnings("unchecked")
    public CompiledQuery buildSelect(Class<?> cls, Criteria<?> c) {
        TableMetadata t = TableMetadataResolver.load(cls);
        // 验证用户输入的条件是否安全
        validateCriteriaSafety(c);
        return ((SqlQueryBuilder<SelectContext>) builders.get(QueryType.SELECT))
                .build(new SelectContext(t, c, false));
    }

    @SuppressWarnings("unchecked")
    public CompiledQuery buildSelect(Class<?> cls, Criteria<?> c, AllocationContext extContext) {
        TableMetadata t = TableMetadataResolver.load(cls);
        // 验证用户输入的条件是否安全
        validateCriteriaSafety(c);
        return ((SqlQueryBuilder<SelectContext>) builders.get(QueryType.SELECT))
                .build(new SelectContext(t, c, extContext, false));
    }

    @SuppressWarnings("unchecked")
    public CompiledQuery buildCount(Class<?> cls, Criteria<?> c, AllocationContext extContext) {
        TableMetadata t = TableMetadataResolver.load(cls);
        // 验证用户输入的条件是否安全
        validateCriteriaSafety(c);
        return ((SqlQueryBuilder<CountContext>) builders.get(QueryType.COUNT))
                .build(new CountContext(t, c, extContext, false));
    }

    @SuppressWarnings("unchecked")
    public BatchCompiledQuery buildBatchInsert(Class<?> cls, List<?> list) {
        TableMetadata t = TableMetadataResolver.load(cls);
        // 批量插入不需要条件验证，但可以验证列表大小防止过大批量操作
        if (list != null && list.size() > 1000) {
            throw new SqlInjectionRiskException("Batch insert size exceeds maximum allowed: 1000");
        }
        return ((BatchQueryBuilder<BatchInsertContext>) builders.get(QueryType.BATCH_INSERT))
                .build(new BatchInsertContext(t, list));
    }

    @SuppressWarnings("unchecked")
    public BatchCompiledQuery buildBatchUpdate(Class<?> cls, List<?> list) {
        TableMetadata t = TableMetadataResolver.load(cls);
        // 批量更新不需要条件验证，但可以验证列表大小防止过大批量操作
        if (list != null && list.size() > 1000) {
            throw new SqlInjectionRiskException("Batch update size exceeds maximum allowed: 1000");
        }
        return ((BatchQueryBuilder<BatchUpdateContext>) builders.get(QueryType.BATCH_UPDATE))
                .build(new BatchUpdateContext(t, list));
    }

    @SuppressWarnings("unchecked")
    public CompiledQuery buildDynamicUpdate(Class<?> cls, Object e) {
        TableMetadata t = TableMetadataResolver.load(cls);
        // 动态更新不需要条件验证，但可以验证对象不为空
        if (e == null) {
            throw new IllegalArgumentException("Entity cannot be null for dynamic update");
        }
        return ((SqlQueryBuilder<DynamicUpdateContext>) builders.get(QueryType.DYNAMIC_UPDATE))
                .build(new DynamicUpdateContext(t, e));
    }

    @SuppressWarnings("unchecked")
    public CompiledQuery buildConditionalUpdate(Class<?> cls, Object e, Criteria<?> c) {
        TableMetadata t = TableMetadataResolver.load(cls);
        // 条件更新需要验证条件安全性
        validateCriteriaSafety(c);
        // 验证对象不为空
        if (e == null) {
            throw new IllegalArgumentException("Entity cannot be null for conditional update");
        }
        return ((SqlQueryBuilder<ConditionalUpdateContext>) builders.get(QueryType.CONDITIONAL_UPDATE))
                .build(new ConditionalUpdateContext(t, e, c));
    }

    @SuppressWarnings("unchecked")
    public CompiledQuery buildDelete(Class<?> cls, Criteria<?> c, AllocationContext extContext) {
        TableMetadata t = TableMetadataResolver.load(cls);
        // 验证用户输入的条件是否安全
        validateCriteriaSafety(c);
        return ((SqlQueryBuilder<DeleteContext>) builders.get(QueryType.DELETE))
                .build(new DeleteContext(t, c, extContext));
    }

    @SuppressWarnings("unchecked")
    public CompiledQuery buildUpsert(Class<?> cls, Object e) {
        TableMetadata t = TableMetadataResolver.load(cls);
        // Upsert不需要条件验证，但可以验证对象不为空
        if (e == null) {
            throw new IllegalArgumentException("Entity cannot be null for upsert");
        }
        return ((SqlQueryBuilder<UpsertContext>) builders.get(QueryType.UPSERT))
                .build(new UpsertContext(t, e, MetadataSdkContext.getDatabaseType()));
    }

    @SuppressWarnings("unchecked")
    public CompiledQuery buildAggregation(Class<?> cls, List<String> aggregations, Criteria<?> criteria,
                                          List<String> groupBy, List<String> having) {
        TableMetadata t = TableMetadataResolver.load(cls);
        // 验证用户输入的条件是否安全
        validateCriteriaSafety(criteria);
        // 验证聚合函数和分组字段
        validateAggregationFields(aggregations, groupBy, having);
        return ((SqlQueryBuilder<AggregationContext>) builders.get(QueryType.AGGREGATION))
                .build(new AggregationContext(t, aggregations, criteria, groupBy, having));
    }

    // 新增方法：构建带HAVING条件的聚合计数查询
    @SuppressWarnings("unchecked")
    public CompiledQuery buildCountAggregation(Class<?> cls, Criteria<?> criteria,
                                               List<String> groupBy, List<String> having) {
        TableMetadata t = TableMetadataResolver.load(cls);
        // 验证用户输入的条件是否安全
        validateCriteriaSafety(criteria);
        // 验证分组字段
        validateAggregationFields(Collections.emptyList(), groupBy, having);
        return ((SqlQueryBuilder<AggregationContext>) builders.get(QueryType.COUNT_AGGREGATION))
                .build(new AggregationContext(t, Collections.emptyList(), criteria, groupBy, having));
    }

    /**
     * 验证查询条件的安全性
     * @param criteria 查询条件对象
     */
    private void validateCriteriaSafety(Criteria<?> criteria) {
        if (criteria == null) {
            return;
        }
        
        // 验证主表条件
        if (criteria.getMainConditions() != null) {
            for (Condition condition : criteria.getMainConditions()) {
                validateCondition(condition);
            }
        }
        
        // 验证扩展表条件
        if (criteria.getExtConditions() != null) {
            for (Condition condition : criteria.getExtConditions()) {
                validateCondition(condition);
            }
        }
        
        // 检查子条件组（使用反射安全地检查是否支持）
        try {
            if (RepositoryClassUtils.hasMethod(criteria, "getGroups")) {
                java.lang.reflect.Method method = criteria.getClass().getMethod("getGroups");
                Object result = method.invoke(criteria);
                if (result instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Criteria<?>> groups = (List<Criteria<?>>) result;
                    for (Criteria<?> group : groups) {
                        validateCriteriaSafety(group);
                    }
                }
            }
        } catch (Exception e) {
            // 如果方法不存在或调用失败，静默忽略
            log.debug("Criteria does not support groups feature");
        }
        
        // 检查连接条件（使用反射安全地检查是否支持）
        try {
            if (RepositoryClassUtils.hasMethod(criteria, "getJoins")) {
                java.lang.reflect.Method method = criteria.getClass().getMethod("getJoins");
                Object result = method.invoke(criteria);
                if (result instanceof List) {
                    validateJoinConditions((List<?>) result);
                }
            }
        } catch (Exception e) {
            // 如果方法不存在或调用失败，静默忽略
            log.debug("Criteria does not support joins feature");
        }
        
        // 检查排序条件（使用反射安全地检查是否支持）
        try {
            if (RepositoryClassUtils.hasMethod(criteria, "getOrders")) {
                java.lang.reflect.Method method = criteria.getClass().getMethod("getOrders");
                Object result = method.invoke(criteria);
                if (result instanceof List) {
                    validateOrderConditions((List<?>) result);
                }
            }
        } catch (Exception e) {
            // 如果方法不存在或调用失败，静默忽略
            log.debug("Criteria does not support orders feature");
        }
        
        // 检查分页参数（使用反射安全地检查是否支持）
        validatePaginationParameters(criteria);
    }
    

    
    /**
     * 验证连接条件
     */
    private void validateJoinConditions(List<?> joins) {
        for (Object join : joins) {
            try {
                if (RepositoryClassUtils.hasMethod(join, "getCriteria")) {
                    java.lang.reflect.Method criteriaMethod = join.getClass().getMethod("getCriteria");
                    Object criteriaObj = criteriaMethod.invoke(join);
                    if (criteriaObj instanceof Criteria) {
                        @SuppressWarnings("unchecked")
                        Criteria<?> criteria = (Criteria<?>) criteriaObj;
                        validateCriteriaSafety(criteria);
                    }
                }
                
                // 检查join类型
                if (RepositoryClassUtils.hasMethod(join, "getType")) {
                    java.lang.reflect.Method typeMethod = join.getClass().getMethod("getType");
                    Object typeObj = typeMethod.invoke(join);
                    if (typeObj instanceof String) {
                        String type = (String) typeObj;
                        // 只允许特定类型的join
                        if (!isValidJoinType(type)) {
                            throw new SqlInjectionRiskException("Invalid join type: " + type);
                        }
                    }
                }
            } catch (SqlInjectionRiskException e) {
                throw e;
            } catch (Exception e) {
                // 忽略反射调用错误
                log.debug("Error validating join condition", e);
            }
        }
    }
    
    /**
     * 验证排序条件
     */
    private void validateOrderConditions(List<?> orders) {
        for (Object order : orders) {
            try {
                if (RepositoryClassUtils.hasMethod(order, "getField")) {
                    java.lang.reflect.Method fieldMethod = order.getClass().getMethod("getField");
                    Object fieldObj = fieldMethod.invoke(order);
                    if (fieldObj instanceof String) {
                        String field = (String) fieldObj;
                        validateOrderField(field);
                    }
                }
                
                // 检查排序方向
                if (RepositoryClassUtils.hasMethod(order, "getDirection")) {
                    java.lang.reflect.Method dirMethod = order.getClass().getMethod("getDirection");
                    Object dirObj = dirMethod.invoke(order);
                    if (dirObj instanceof String) {
                        String direction = (String) dirObj;
                        // 只允许特定的排序方向
                        if (!isValidSortDirection(direction)) {
                            throw new SqlInjectionRiskException("Invalid sort direction: " + direction);
                        }
                    }
                }
            } catch (SqlInjectionRiskException e) {
                throw e;
            } catch (Exception e) {
                // 忽略反射调用错误
                log.debug("Error validating order condition", e);
            }
        }
    }
    
    /**
     * 验证分页参数
     */
    private void validatePaginationParameters(Criteria<?> criteria) {
        try {
            // 检查分页大小
            if (RepositoryClassUtils.hasMethod(criteria, "getPageSize")) {
                java.lang.reflect.Method method = criteria.getClass().getMethod("getPageSize");
                Object result = method.invoke(criteria);
                if (result instanceof Integer) {
                    int pageSize = (Integer) result;
                    // 限制最大分页大小，防止内存溢出攻击
                    int maxPageSize = 1000; // 可以配置化
                    if (pageSize > maxPageSize) {
                        throw new SqlInjectionRiskException("Page size exceeds maximum allowed: " + maxPageSize);
                    }
                }
            }
        } catch (SqlInjectionRiskException e) {
            throw e;
        } catch (Exception e) {
            // 忽略反射调用错误
            log.debug("Error validating pagination parameters", e);
        }
    }

    /**
     * 验证单个条件的安全性
     * @param condition 查询条件
     */
    private void validateCondition(Condition condition) {
        if (condition == null) {
            return;
        }
        
        // 验证列名
        String column = condition.getColumn();
        if (column != null) {
            // 确保列名只包含字母、数字和下划线
            if (!isValidColumnName(column)) {
                throw new SqlInjectionRiskException("Invalid column name: " + column);
            }
            // 检查危险字符
            if (containsDangerousCharacters(column)) {
                throw new SqlInjectionRiskException("Potential SQL injection detected in column name: " + column);
            }
        }
        
        // 检查扩展字段名（使用反射安全地检查）
        try {
            if (RepositoryClassUtils.hasMethod(condition, "getExtFieldName")) {
                java.lang.reflect.Method method = condition.getClass().getMethod("getExtFieldName");
                Object result = method.invoke(condition);
                if (result instanceof String) {
                    String extFieldName = (String) result;
                    if (extFieldName != null && containsDangerousCharacters(extFieldName)) {
                        throw new SqlInjectionRiskException("Potential SQL injection detected in extension field name: " + extFieldName);
                    }
                }
            }
        } catch (SqlInjectionRiskException e) {
            throw e;
        } catch (Exception e) {
            // 忽略反射调用错误
            log.debug("Condition does not support extFieldName feature");
        }
        
        // 检查操作符（使用反射安全地检查）
        try {
            if (RepositoryClassUtils.hasMethod(condition, "getOperator")) {
                java.lang.reflect.Method method = condition.getClass().getMethod("getOperator");
                Object result = method.invoke(condition);
                if (result instanceof String) {
                    String operator = (String) result;
                    if (!isValidOperator(operator)) {
                        throw new SqlInjectionRiskException("Invalid operator: " + operator);
                    }
                }
            }
        } catch (SqlInjectionRiskException e) {
            throw e;
        } catch (Exception e) {
            // 忽略反射调用错误
            log.debug("Error validating condition operator", e);
        }
    }
    
    /**
     * 验证排序字段的安全性
     * @param fieldName 排序字段名
     */
    private void validateOrderField(String fieldName) {
        if (fieldName != null) {
            // 确保排序字段名只包含字母、数字和下划线
            if (!isValidColumnName(fieldName)) {
                throw new SqlInjectionRiskException("Invalid order field name: " + fieldName);
            }
            // 检查危险字符
            if (containsDangerousCharacters(fieldName)) {
                throw new SqlInjectionRiskException("Potential SQL injection detected in order field: " + fieldName);
            }
        }
    }
    
    /**
     * 验证列名是否有效
     * @param columnName 列名
     * @return 是否有效
     */
    private boolean isValidColumnName(String columnName) {
        // 只允许字母、数字、下划线、点号（用于表别名）和方括号（用于特殊命名）
        // 方括号处理是为了支持SQL Server等数据库的带空格列名
        return columnName.matches("^[a-zA-Z0-9_\\[\\]`]+(\\.[a-zA-Z0-9_\\[\\]`]+)?$");
    }
    
    /**
     * 验证操作符是否有效
     * @param operator 操作符
     * @return 是否有效
     */
    private boolean isValidOperator(String operator) {
        // 只允许特定的操作符
        String[] validOperators = {
            "=", "!=", ">", ">=", "<", "<=", 
            "LIKE", "NOT LIKE", "IN", "NOT IN",
            "IS NULL", "IS NOT NULL", "BETWEEN", "NOT BETWEEN"
        };
        for (String validOp : validOperators) {
            if (validOp.equalsIgnoreCase(operator)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 验证join类型是否有效
     * @param joinType join类型
     * @return 是否有效
     */
    private boolean isValidJoinType(String joinType) {
        String[] validJoinTypes = {"INNER", "LEFT", "RIGHT", "FULL", "CROSS", "LEFT OUTER", "RIGHT OUTER", "FULL OUTER"};
        for (String validType : validJoinTypes) {
            if (validType.equalsIgnoreCase(joinType)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 验证排序方向是否有效
     * @param direction 排序方向
     * @return 是否有效
     */
    private boolean isValidSortDirection(String direction) {
        return "ASC".equalsIgnoreCase(direction) || "DESC".equalsIgnoreCase(direction);
    }

    /**
     * 验证聚合函数和分组字段的安全性
     */
    private void validateAggregationFields(List<String> aggregations, List<String> groupBy, List<String> having) {
        // 验证聚合函数
        if (aggregations != null) {
            for (String agg : aggregations) {
                if (!isValidAggregationFunction(agg)) {
                    throw new SqlInjectionRiskException("Invalid aggregation function: " + agg);
                }
            }
        }
        
        // 验证分组字段
        if (groupBy != null) {
            for (String field : groupBy) {
                validateOrderField(field); // 复用字段验证逻辑
            }
        }
        
        // 验证having条件
        if (having != null) {
            for (String condition : having) {
                if (containsDangerousCharacters(condition)) {
                    throw new SqlInjectionRiskException("Potential SQL injection detected in HAVING condition: " + condition);
                }
            }
        }
    }
    
    /**
     * 验证聚合函数是否有效
     */
    private boolean isValidAggregationFunction(String func) {
        // 基本聚合函数验证
        String[] validAggregations = {"COUNT", "SUM", "AVG", "MIN", "MAX", "COUNT(DISTINCT"};
        
        // 转换为大写进行不区分大小写的匹配
        String upperFunc = func.toUpperCase().trim();
        
        // 检查是否以有效的聚合函数开头
        for (String validAgg : validAggregations) {
            if (upperFunc.startsWith(validAgg)) {
                // 确保函数有正确的括号匹配
                int openBracketCount = 0;
                int closeBracketCount = 0;
                for (char c : upperFunc.toCharArray()) {
                    if (c == '(') openBracketCount++;
                    if (c == ')') closeBracketCount++;
                }
                
                // 确保括号匹配且至少有一对括号
                if (openBracketCount == closeBracketCount && openBracketCount > 0) {
                    // 提取函数内部的内容并验证
                    String innerContent = upperFunc.substring(upperFunc.indexOf('(') + 1, upperFunc.lastIndexOf(')'));
                    if (!containsDangerousCharacters(innerContent)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    /**
     * 检查字符串是否包含危险字符
     * @param input 输入字符串
     * @return 是否包含危险字符
     */
    private boolean containsDangerousCharacters(String input) {
        // 检查SQL注入的危险字符
        return input.contains(";" ) || // 语句终止符
               input.contains("--") || // 单行注释
               input.contains("/*") || // 多行注释开始
               input.contains("*/") || // 多行注释结束
               input.contains("'") || // 单引号
               input.contains("\"") || // 双引号
               input.contains("/") || // 可能的路径遍历
               input.matches("(?i)OR\\s+.*=.*") || // OR 1=1 类型注入
               input.matches("(?i)UNION\\s+SELECT"); // UNION查询注入
    }
}