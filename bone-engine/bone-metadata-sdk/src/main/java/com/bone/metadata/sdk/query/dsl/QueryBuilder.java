package com.bone.metadata.sdk.query.dsl;

import com.bone.core.enums.Operator;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.domain.spec.TableMetadataResolver;
import com.bone.metadata.sdk.query.criteria.Condition;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.metadata.sdk.support.config.MetadataSdkContext;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 查询构建异常，用于SQL构建过程中的错误处理
 */
@Getter
@Setter
@Accessors(chain = true)
class QueryBuildException extends RuntimeException {
    private String sql;
    private Map<String, Object> parameters;
    
    public QueryBuildException(String message) {
        super(message);
    }
    
    public QueryBuildException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public QueryBuildException(String message, String sql, Map<String, Object> parameters) {
        super(message);
        this.sql = sql;
        this.parameters = parameters;
    }
    
    public QueryBuildException(String message, Throwable cause, String sql, Map<String, Object> parameters) {
        super(message, cause);
        this.sql = sql;
        this.parameters = parameters;
    }
}

/**
 * 查询执行异常，用于SQL执行过程中的错误处理
 */
@Getter
@Setter
@Accessors(chain = true)
class QueryExecutionException extends RuntimeException {
    private String sql;
    private Map<String, Object> parameters;
    
    public QueryExecutionException(String message) {
        super(message);
    }
    
    public QueryExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public QueryExecutionException(String message, String sql, Map<String, Object> parameters) {
        super(message);
        this.sql = sql;
        this.parameters = parameters;
    }
    
    public QueryExecutionException(String message, Throwable cause, String sql, Map<String, Object> parameters) {
        super(message, cause);
        this.sql = sql;
        this.parameters = parameters;
    }
}

/**
 * 非唯一结果异常，当查询期望唯一结果但返回多个结果时抛出
 */
@Getter
@Setter
@Accessors(chain = true)
class NonUniqueResultException extends RuntimeException {
    private String sql;
    private Map<String, Object> parameters;
    
    public NonUniqueResultException(String message) {
        super(message);
    }
    
    public NonUniqueResultException(String message, String sql, Map<String, Object> parameters) {
        super(message);
        this.sql = sql;
        this.parameters = parameters;
    }
}

/**
 * SQL构建器主入口类，提供类型安全的SQL查询DSL
 * 支持基于方法引用的流式API设计，实现真正的参数化查询
 */
@Slf4j
public class QueryBuilder {

    // 用于生成唯一参数名的原子计数器
    private static final AtomicInteger PARAM_COUNTER = new AtomicInteger(0);
    
    // 方法引用解析正则表达式
    private static final Pattern METHOD_REFERENCE_PATTERN = Pattern.compile("get(\\p{javaUpperCase}\\w*)");
    private static final Pattern LAMBDA_EXPRESSION_PATTERN = Pattern.compile("\\$(\\d+)\\.([a-zA-Z0-9_]+)");
    
    // 内部SQL工具方法
    private static String toSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
    
    // 私有构造函数，防止实例化
    private QueryBuilder() {
        throw new AssertionError("Cannot instantiate QueryBuilder");
    }

    /**
     * 从指定实体类创建查询构建器
     * @param entityClass 实体类
     * @param <T> 实体类型
     * @return EntitySqlBuilder实例
     */
    public static <T> EntitySqlBuilder<T> from(Class<T> entityClass) {
        return new EntitySqlBuilderImpl<>(entityClass);
    }

    /**
     * 从指定实体类创建查询构建器（别名方法）
     * @param entityClass 实体类
     * @param <T> 实体类型
     * @return EntitySqlBuilder实例
     */
    public static <T> EntitySqlBuilder<T> selectFrom(Class<T> entityClass) {
        return from(entityClass);
    }
    
    /**
     * 查询上下文，用于存储查询构建过程中的所有信息
     * @param <T> 实体类型
     */
    private static class QueryContext<T> {
        @Getter
        private final Class<T> entityClass;
        @Getter
        private final TableMetadata tableMetadata;
        @Getter
        private final List<Condition> conditions = new ArrayList<>();
        @Getter
        private final List<String> conditionOperators = new ArrayList<>(); // 存储AND/OR操作符
        @Getter
        private final List<String> groupByFields = new ArrayList<>();
        @Getter
        private final List<String> havingConditions = new ArrayList<>();
        @Getter
        private final List<String> havingOperators = new ArrayList<>(); // 存储HAVING子句的AND/OR操作符
        @Getter
        private final Map<String, String> orderByFields = new LinkedHashMap<>(); // 字段名 -> 排序方向
        @Getter
        private long limit = -1;
        @Getter
        private long offset = 0;
        @Getter
        private final Map<String, Object> parameters = new HashMap<>();
        
        public QueryContext(Class<T> entityClass) {
            this.entityClass = entityClass;
            this.tableMetadata = TableMetadataResolver.load(entityClass);
        }
        
        public void setLimit(long limit) {
            if (limit < 0) {
                throw new IllegalArgumentException("Limit must be non-negative");
            }
            this.limit = limit;
        }
        
        public void setOffset(long offset) {
            if (offset < 0) {
                throw new IllegalArgumentException("Offset must be non-negative");
            }
            this.offset = offset;
        }
        
        public void addCondition(Condition condition) {
            if (condition == null) {
                throw new IllegalArgumentException("Condition cannot be null");
            }
            this.conditions.add(condition);
        }
        
        public void addConditionOperator(String operator) {
            if (operator == null || (!"AND".equals(operator) && !"OR".equals(operator))) {
                throw new IllegalArgumentException("Invalid condition operator: " + operator);
            }
            this.conditionOperators.add(operator);
        }
        
        public void addGroupByField(String fieldName) {
            if (fieldName == null || fieldName.trim().isEmpty()) {
                throw new IllegalArgumentException("Group by field name cannot be null or empty");
            }
            this.groupByFields.add(fieldName);
        }
        
        public void addHavingCondition(String condition) {
            if (condition == null || condition.trim().isEmpty()) {
                throw new IllegalArgumentException("Having condition cannot be null or empty");
            }
            this.havingConditions.add(condition);
        }
        
        public void addHavingOperator(String operator) {
            if (operator == null || (!"AND".equals(operator) && !"OR".equals(operator))) {
                throw new IllegalArgumentException("Invalid having operator: " + operator);
            }
            this.havingOperators.add(operator);
        }
        
        public void addOrderByField(String fieldName, String direction) {
            if (fieldName == null || fieldName.trim().isEmpty()) {
                throw new IllegalArgumentException("Order by field name cannot be null or empty");
            }
            if (direction == null || (!"ASC".equals(direction) && !"DESC".equals(direction))) {
                throw new IllegalArgumentException("Invalid order direction: " + direction);
            }
            this.orderByFields.put(fieldName, direction);
        }
        
        public void addParameter(String name, Object value) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Parameter name cannot be null or empty");
            }
            this.parameters.put(name, value);
        }
    }

    // 静态SqlExecutor实例，由Spring注入
    private static volatile SqlExecutor sqlExecutor;
    
    /**
     * 设置SqlExecutor实例（由Spring框架调用注入）
     * @param executor SqlExecutor实例
     */
    public static void setSqlExecutor(SqlExecutor executor) {
        sqlExecutor = executor;
    }
    
    /**
     * 获取SqlExecutor实例（确保非空）
     * @return SqlExecutor实例
     */
    private static SqlExecutor getSqlExecutor() {
        if (sqlExecutor == null) {
            throw new IllegalStateException("SqlExecutor not initialized. Please ensure it's properly injected.");
        }
        return sqlExecutor;
    }
    
    /**
     * 内部实现类，提供具体的SQL构建功能
     * @param <T> 实体类型
     */
    private static class EntitySqlBuilderImpl<T> implements EntitySqlBuilder<T> {
        private final QueryContext<T> context;

        public EntitySqlBuilderImpl(Class<T> entityClass) {
            this.context = new QueryContext<>(entityClass);
        }

        /**
         * 从方法引用中提取字段名
         * @param fieldFunction 字段方法引用
         * @param <V> 字段值类型
         * @return 数据库列名
         */
        public <V> String extractFieldName(FieldFunction<T, V> fieldFunction) {
            try {
                // 获取方法引用的toString()结果，通常包含方法名信息
                String toString = fieldFunction.toString();
                log.debug("Extracting field name from: {}", toString);
                
                // 尝试匹配getter方法模式
                Matcher matcher = METHOD_REFERENCE_PATTERN.matcher(toString);
                if (matcher.find()) {
                    String propertyName = matcher.group(1);
                    // 转换为首字母小写的属性名
                    return Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
                }
                
                // 尝试匹配lambda表达式模式
                matcher = LAMBDA_EXPRESSION_PATTERN.matcher(toString);
                if (matcher.find()) {
                    return matcher.group(2);
                }
                
                // 如果无法从方法引用中提取，使用反射查找第一个getter方法
                // 这是一个回退机制，实际应用中应优先使用方法引用
                Method[] methods = context.getEntityClass().getDeclaredMethods();
                for (Method method : methods) {
                    if (method.getName().startsWith("get") && method.getParameterCount() == 0) {
                        PropertyDescriptor pd = new PropertyDescriptor(method.getName().substring(3), context.getEntityClass());
                        return pd.getName();
                    }
                }
                
                throw new IllegalArgumentException("Cannot extract field name from function: " + toString);
            } catch (Exception e) {
                log.error("Failed to extract field name", e);
                throw new IllegalArgumentException("Failed to extract field name", e);
            }
        }

        @Override
        public <V> WhereClause<T> where(FieldFunction<T, V> fieldFunction) {
            String fieldName = extractFieldName(fieldFunction);
            return new WhereClauseImpl<>(this, fieldName);
        }

        @Override
        public <V> GroupByClause<T> groupBy(FieldFunction<T, V> fieldFunction) {
            String fieldName = extractFieldName(fieldFunction);
            context.addGroupByField(fieldName);
            return new GroupByClauseImpl<>(this, fieldName);
        }

        @Override
        public <V> OrderByClause<T> orderBy(FieldFunction<T, V> fieldFunction) {
            String fieldName = extractFieldName(fieldFunction);
            return new OrderByClauseImpl<>(this, fieldName);
        }

        @Override
        public EntitySqlBuilder<T> limit(long limit) {
            context.setLimit(limit);
            return this;
        }

        @Override
        public EntitySqlBuilder<T> offset(long offset) {
            context.setOffset(offset);
            return this;
        }
        
        /**
         * 构建查询SQL
         * @return 编译后的查询
         */
        private CompiledQuery buildQuery() {
            try {
                StringBuilder sql = new StringBuilder();
                String tableAlias = "m";
                sql.append("SELECT * FROM ").append(context.getTableMetadata().getName()).append(" ").append(tableAlias);
                
                // 添加WHERE条件
                if (!context.getConditions().isEmpty()) {
                    sql.append(" WHERE ");
                    StringBuilder whereClause = new StringBuilder();
                    for (int i = 0; i < context.getConditions().size(); i++) {
                        if (i > 0) {
                            // 使用存储的操作符（AND/OR）连接条件
                            String operator = i <= context.getConditionOperators().size() ? 
                                             context.getConditionOperators().get(i - 1) : "AND";
                            whereClause.append(" ").append(operator).append(" ");
                        }
                        // 添加表别名前缀
                        String conditionSql = context.getConditions().get(i).toSql();
                        // 为字段添加表别名前缀
                        whereClause.append(addTableAliasToCondition(conditionSql, tableAlias));
                    }
                    sql.append(whereClause);
                }
                
                // 添加GROUP BY
                if (!context.getGroupByFields().isEmpty()) {
                    sql.append(" GROUP BY ");
                    String groupByClause = context.getGroupByFields().stream()
                        .map(field -> tableAlias + "." + toSnakeCase(field))
                        .collect(Collectors.joining(", "));
                    sql.append(groupByClause);
                }
                
                // 添加HAVING条件
                if (!context.getHavingConditions().isEmpty()) {
                    sql.append(" HAVING ");
                    StringBuilder havingClause = new StringBuilder();
                    for (int i = 0; i < context.getHavingConditions().size(); i++) {
                        if (i > 0) {
                            // 使用存储的操作符（AND/OR）连接条件
                            String operator = i <= context.getHavingOperators().size() ? 
                                             context.getHavingOperators().get(i - 1) : "AND";
                            havingClause.append(" ").append(operator).append(" ");
                        }
                        havingClause.append(context.getHavingConditions().get(i));
                    }
                    sql.append(havingClause);
                }
                
                // 添加ORDER BY
                if (!context.getOrderByFields().isEmpty()) {
                    sql.append(" ORDER BY ");
                    String orderByClause = context.getOrderByFields().entrySet().stream()
                        .map(entry -> tableAlias + "." + toSnakeCase(entry.getKey()) + " " + entry.getValue())
                        .collect(Collectors.joining(", "));
                    sql.append(orderByClause);
                }
                
                // 添加分页
                if (context.getLimit() > 0) {
                    sql.append(" LIMIT ").append(context.getLimit());
                    if (context.getOffset() > 0) {
                        sql.append(" OFFSET ").append(context.getOffset());
                    }
                }
                
                String finalSql = sql.toString();
                log.debug("Generated SQL: {}", finalSql);
                log.debug("SQL Parameters: {}", context.getParameters());
                
                return new CompiledQuery(finalSql, context.getParameters());
            } catch (Exception e) {
                log.error("Failed to build query SQL", e);
                throw new QueryBuildException("Error building SQL query", e);
            }
        }
        
        /**
         * 为条件中的字段添加表别名前缀
         * @param conditionSql 原始条件SQL
         * @param tableAlias 表别名
         * @return 添加表别名后的SQL
         */
        private String addTableAliasToCondition(String conditionSql, String tableAlias) {
            // 改进的实现，使用更简单可靠的方式处理所有条件类型，包括IS NULL和IS NOT NULL
            // 不再使用复杂的正则表达式，而是直接处理IS NULL和IS NOT NULL情况
            if (conditionSql.endsWith(" IS NULL") || conditionSql.endsWith(" IS NOT NULL")) {
                // 对于IS NULL和IS NOT NULL条件，只在列名前添加表别名
                int spaceIndex = conditionSql.lastIndexOf(" ");
                String columnName = conditionSql.substring(0, spaceIndex);
                String nullCondition = conditionSql.substring(spaceIndex).trim();
                return tableAlias + "." + columnName + " " + nullCondition;
            } else {
                // 对于其他条件，使用正则表达式添加表别名
                return conditionSql.replaceAll("(?<=^|[\\s(),])" + 
                                              "([a-zA-Z_][a-zA-Z0-9_]*)", 
                                              tableAlias + ".$1");
            }
        }
        
        /**
         * 构建计数查询SQL
         * @return 编译后的计数查询
         */
        private CompiledQuery buildCountQuery() {
            try {
                StringBuilder sql = new StringBuilder();
                sql.append("SELECT COUNT(*) FROM ").append(context.getTableMetadata().getName()).append(" m");
                
                // 添加WHERE条件
                if (!context.getConditions().isEmpty()) {
                    sql.append(" WHERE ");
                    StringBuilder whereClause = new StringBuilder();
                    for (int i = 0; i < context.getConditions().size(); i++) {
                        if (i > 0) {
                            // 使用存储的操作符（AND/OR）连接条件
                            String operator = i <= context.getConditionOperators().size() ? 
                                             context.getConditionOperators().get(i - 1) : "AND";
                            whereClause.append(" ").append(operator).append(" ");
                        }
                        whereClause.append(context.getConditions().get(i).toSql());
                    }
                    sql.append(whereClause);
                }
                
                // 添加GROUP BY
                if (!context.getGroupByFields().isEmpty()) {
                    sql.append(" GROUP BY ");
                    String groupByClause = context.getGroupByFields().stream()
                        .map(field -> "m." + toSnakeCase(field))
                        .collect(Collectors.joining(", "));
                    sql.append(groupByClause);
                }
                
                // 添加HAVING条件
                if (!context.getHavingConditions().isEmpty()) {
                    sql.append(" HAVING ");
                    StringBuilder havingClause = new StringBuilder();
                    for (int i = 0; i < context.getHavingConditions().size(); i++) {
                        if (i > 0) {
                            // 使用存储的操作符（AND/OR）连接条件
                            String operator = i <= context.getHavingOperators().size() ? 
                                             context.getHavingOperators().get(i - 1) : "AND";
                            havingClause.append(" ").append(operator).append(" ");
                        }
                        havingClause.append(context.getHavingConditions().get(i));
                    }
                    sql.append(havingClause);
                }
                
                String finalSql = sql.toString();
                log.debug("Generated COUNT SQL: {}", finalSql);
                log.debug("SQL Parameters: {}", context.getParameters());
                
                return new CompiledQuery(finalSql, context.getParameters());
            } catch (Exception e) {
                log.error("Failed to build count query SQL", e);
                throw new QueryBuildException("Error building count SQL query", e);
            }
        }

        @Override
        public List<T> list() {
            try {
                CompiledQuery query = buildQuery();
                log.debug("Executing list query: {}", query.getSql());
                List<T> results = getSqlExecutor().executeQuery(query, context.getEntityClass());
                log.debug("List query returned {} results", results.size());
                return results;
            } catch (QueryBuildException e) {
                // 直接抛出构建异常
                throw e;
            } catch (Exception e) {
                log.error("Failed to execute list query", e);
                throw new QueryExecutionException("List query execution failed", e);
            }
        }

        @Override
        public T single() {
            try {
                // 保存原始的limit值，以便恢复
                long originalLimit = context.getLimit();
                try {
                    // 限制结果为2条以便检测多条结果
                    context.setLimit(2);
                    CompiledQuery query = buildQuery();
                    log.debug("Executing single query: {}", query.getSql());
                    List<T> results = getSqlExecutor().executeQuery(query, context.getEntityClass());
                    
                    if (results.isEmpty()) {
                        log.debug("Single query returned no results");
                        return null;
                    } else if (results.size() > 1) {
                        throw new NonUniqueResultException("Expected single result, but found " + results.size(), 
                                                          query.getSql(), query.getParameters());
                    }
                    
                    log.debug("Single query returned exactly one result");
                    return results.get(0);
                } finally {
                    // 恢复原始的limit值
                    context.setLimit(originalLimit);
                }
            } catch (NonUniqueResultException e) {
                // 直接抛出非唯一结果异常
                throw e;
            } catch (Exception e) {
                log.error("Failed to execute single query", e);
                throw new QueryExecutionException("Single query execution failed", e);
            }
        }

        @Override
        public long count() {
            try {
                CompiledQuery query = buildCountQuery();
                log.debug("Executing count query: {}", query.getSql());
                Long result = getSqlExecutor().queryForObject(query, Long.class);
                long count = result != null ? result : 0;
                log.debug("Count query returned: {}", count);
                return count;
            } catch (QueryBuildException e) {
                // 直接抛出构建异常
                throw e;
            } catch (Exception e) {
                log.error("Failed to execute count query", e);
                throw new QueryExecutionException("Count query execution failed", e);
            }
        }
    }

    /**
     * 内部Where子句实现
     * @param <T> 实体类型
     */
    private static class WhereClauseImpl<T> implements WhereClause<T> {
        private final EntitySqlBuilderImpl<T> parent;
        private final String fieldName;

        public WhereClauseImpl(EntitySqlBuilderImpl<T> parent, String fieldName) {
            if (parent == null) {
                throw new IllegalArgumentException("Parent builder cannot be null");
            }
            if (fieldName == null || fieldName.trim().isEmpty()) {
                throw new IllegalArgumentException("Field name cannot be null or empty");
            }
            this.parent = parent;
            this.fieldName = fieldName;
        }

        /**
         * 创建参数名
         * @return 唯一的参数名
         */
        private String createParamName() {
            return "param_" + PARAM_COUNTER.incrementAndGet();
        }

        /**
         * 创建并添加条件的通用方法
         * @param operator 操作符
         * @param value 参数值
         * @return ConditionClause实例
         */
        private ConditionClause<T> addCondition(Operator operator, Object value) {
            String paramName = createParamName();
            String columnName = toSnakeCase(fieldName);
            Object[] values = value instanceof Collection ? ((Collection<?>)value).toArray() : new Object[]{value};
            Condition condition = new Condition(fieldName, columnName, paramName, operator, false, values);
            parent.context.addCondition(condition);
            parent.context.addParameter(paramName, value);
            return new ConditionClauseImpl<>(parent);
        }

        @Override
        public ConditionClause<T> eq(Object value) {
            return addCondition(Operator.EQ, value);
        }

        @Override
        public ConditionClause<T> like(String value) {
            if (value == null) {
                throw new IllegalArgumentException("Like value cannot be null");
            }
            return addCondition(Operator.LIKE, value);
        }

        @Override
        public <V> ConditionClause<T> gt(Comparable<V> value) {
            if (value == null) {
                throw new IllegalArgumentException("Greater than value cannot be null");
            }
            return addCondition(Operator.GT, value);
        }

        @Override
        public <V> ConditionClause<T> lt(Comparable<V> value) {
            if (value == null) {
                throw new IllegalArgumentException("Less than value cannot be null");
            }
            return addCondition(Operator.LT, value);
        }

        @Override
        public <V> ConditionClause<T> gte(Comparable<V> value) {
            if (value == null) {
                throw new IllegalArgumentException("Greater than or equal value cannot be null");
            }
            return addCondition(Operator.GTE, value);
        }

        @Override
        public <V> ConditionClause<T> lte(Comparable<V> value) {
            if (value == null) {
                throw new IllegalArgumentException("Less than or equal value cannot be null");
            }
            return addCondition(Operator.LTE, value);
        }

        @Override
        public ConditionClause<T> ne(Object value) {
            return addCondition(Operator.NE, value);
        }

        @Override
        public ConditionClause<T> in(Collection<?> values) {
            if (values == null) {
                throw new IllegalArgumentException("IN collection cannot be null");
            }
            if (values.isEmpty()) {
                throw new IllegalArgumentException("IN collection cannot be empty");
            }
            return addCondition(Operator.IN, values);
        }

        @Override
        public ConditionClause<T> isNull() {
            String columnName = toSnakeCase(fieldName);
            Condition condition = new Condition(fieldName, columnName, null, Operator.IS_NULL, new Object[0]);
            parent.context.addCondition(condition);
            return new ConditionClauseImpl<>(parent);
        }

        @Override
        public ConditionClause<T> isNotNull() {
            String columnName = toSnakeCase(fieldName);
            Condition condition = new Condition(fieldName, columnName, null, Operator.IS_NOT_NULL, new Object[0]);
            parent.context.addCondition(condition);
            return new ConditionClauseImpl<>(parent);
        }
    }

    /**
     * ConditionClause接口的实现类
     * @param <T> 实体类型
     */
    private static class ConditionClauseImpl<T> implements ConditionClause<T> {
        private final EntitySqlBuilderImpl<T> parent;

        public ConditionClauseImpl(EntitySqlBuilderImpl<T> parent) {
            if (parent == null) {
                throw new IllegalArgumentException("Parent builder cannot be null");
            }
            this.parent = parent;
        }

        @Override
        public <V> WhereClause<T> and(FieldFunction<T, V> fieldFunction) {
            // 记录AND操作符
            parent.context.addConditionOperator("AND");
            return parent.where(fieldFunction);
        }

        @Override
        public <V> WhereClause<T> or(FieldFunction<T, V> fieldFunction) {
            // 记录OR操作符
            parent.context.addConditionOperator("OR");
            return parent.where(fieldFunction);
        }

        @Override
        public List<T> list() {
            return parent.list();
        }

        @Override
        public T single() {
            return parent.single();
        }

        @Override
        public long count() {
            return parent.count();
        }

        @Override
        public <V> OrderByClause<T> orderBy(FieldFunction<T, V> fieldFunction) {
            return parent.orderBy(fieldFunction);
        }

        @Override
        public ConditionClause<T> limit(long limit) {
            parent.limit(limit);
            return this;
        }

        @Override
        public ConditionClause<T> offset(long offset) {
            parent.offset(offset);
            return this;
        }
    }

    /**
     * GroupByClause接口的实现类
     * @param <T> 实体类型
     */
    private static class GroupByClauseImpl<T> implements GroupByClause<T> {
        private final EntitySqlBuilderImpl<T> parent;
        private final String fieldName;

        public GroupByClauseImpl(EntitySqlBuilderImpl<T> parent, String fieldName) {
            this.parent = parent;
            this.fieldName = fieldName;
        }

        @Override
        public <V> GroupByClause<T> groupBy(FieldFunction<T, V> fieldFunction) {
            String nextFieldName = parent.extractFieldName(fieldFunction);
            parent.context.addGroupByField(nextFieldName);
            return new GroupByClauseImpl<>(parent, nextFieldName);
        }

        @Override
        public <V> HavingClause<T> having(FieldFunction<T, V> fieldFunction) {
            String havingFieldName = parent.extractFieldName(fieldFunction);
            return new HavingClauseImpl<>(this, havingFieldName);
        }

        @Override
        public <V> OrderByClause<T> orderBy(FieldFunction<T, V> fieldFunction) {
            return parent.orderBy(fieldFunction);
        }

        @Override
        public GroupByClause<T> limit(long limit) {
            parent.limit(limit);
            return this;
        }

        @Override
        public GroupByClause<T> offset(long offset) {
            parent.offset(offset);
            return this;
        }

        @Override
        public List<T> list() {
            return parent.list();
        }

        @Override
        public T single() {
            return parent.single();
        }

        @Override
        public long count() {
            return parent.count();
        }
    }

    /**
     * HavingClause接口的实现类
     * @param <T> 实体类型
     */
    private static class HavingClauseImpl<T> implements HavingClause<T> {
        private final GroupByClauseImpl<T> parent;
        private final String fieldName;

        public HavingClauseImpl(GroupByClauseImpl<T> parent, String fieldName) {
            if (parent == null) {
                throw new IllegalArgumentException("Parent group by clause cannot be null");
            }
            if (fieldName == null || fieldName.trim().isEmpty()) {
                throw new IllegalArgumentException("Field name cannot be null or empty");
            }
            this.parent = parent;
            this.fieldName = fieldName;
        }

        private String createParamName() {
            return "having_param_" + PARAM_COUNTER.incrementAndGet();
        }

        /**
         * 添加HAVING条件的通用方法
         * @param operator 操作符
         * @param value 参数值
         * @return GroupByClause实例
         */
        private GroupByClause<T> addHavingCondition(String operator, Object value) {
            String paramName = createParamName();
            String condition = fieldName + " " + operator + " :" + paramName;
            parent.parent.context.addHavingCondition(condition);
            parent.parent.context.addParameter(paramName, value);
            return parent;
        }

        @Override
        public GroupByClause<T> eq(Object value) {
            return addHavingCondition("=", value);
        }

        @Override
        public GroupByClause<T> like(String value) {
            if (value == null) {
                throw new IllegalArgumentException("Like value cannot be null");
            }
            return addHavingCondition("LIKE", value);
        }

        @Override
        public GroupByClause<T> in(Collection<?> values) {
            if (values == null) {
                throw new IllegalArgumentException("IN collection cannot be null");
            }
            if (values.isEmpty()) {
                throw new IllegalArgumentException("IN collection cannot be empty");
            }
            String paramName = createParamName();
            String condition = fieldName + " IN (:" + paramName + ")";
            parent.parent.context.addHavingCondition(condition);
            parent.parent.context.addParameter(paramName, values);
            return parent;
        }

        @Override
        public <V> GroupByClause<T> gt(Comparable<V> value) {
            if (value == null) {
                throw new IllegalArgumentException("Greater than value cannot be null");
            }
            return addHavingCondition(">", value);
        }

        @Override
        public <V> GroupByClause<T> lt(Comparable<V> value) {
            if (value == null) {
                throw new IllegalArgumentException("Less than value cannot be null");
            }
            return addHavingCondition("<", value);
        }

        @Override
        public <V> GroupByClause<T> gte(Comparable<V> value) {
            if (value == null) {
                throw new IllegalArgumentException("Greater than or equal value cannot be null");
            }
            return addHavingCondition(">=", value);
        }

        @Override
        public <V> GroupByClause<T> lte(Comparable<V> value) {
            if (value == null) {
                throw new IllegalArgumentException("Less than or equal value cannot be null");
            }
            return addHavingCondition("<=", value);
        }

        @Override
        public GroupByClause<T> ne(Object value) {
            return addHavingCondition("!=", value);
        }

        @Override
        public GroupByClause<T> isNull() {
            String condition = fieldName + " IS NULL";
            parent.parent.context.addHavingCondition(condition);
            return parent;
        }

        @Override
        public GroupByClause<T> isNotNull() {
            String condition = fieldName + " IS NOT NULL";
            parent.parent.context.addHavingCondition(condition);
            return parent;
        }

        @Override
        public <V> HavingClause<T> and(FieldFunction<T, V> fieldFunction) {
            if (fieldFunction == null) {
                throw new IllegalArgumentException("Field function cannot be null");
            }
            // 记录AND操作符
            parent.parent.context.addHavingOperator("AND");
            String nextFieldName = parent.parent.extractFieldName(fieldFunction);
            return new HavingClauseImpl<>(parent, nextFieldName);
        }

        @Override
        public <V> HavingClause<T> or(FieldFunction<T, V> fieldFunction) {
            if (fieldFunction == null) {
                throw new IllegalArgumentException("Field function cannot be null");
            }
            // 记录OR操作符
            parent.parent.context.addHavingOperator("OR");
            String nextFieldName = parent.parent.extractFieldName(fieldFunction);
            return new HavingClauseImpl<>(parent, nextFieldName);
        }
    }

    /**
     * OrderByClause接口的实现类
     * @param <T> 实体类型
     */
    private static class OrderByClauseImpl<T> implements OrderByClause<T> {
        private final EntitySqlBuilderImpl<T> parent;
        private final String fieldName;

        public OrderByClauseImpl(EntitySqlBuilderImpl<T> parent, String fieldName) {
            if (parent == null) {
                throw new IllegalArgumentException("Parent builder cannot be null");
            }
            if (fieldName == null || fieldName.trim().isEmpty()) {
                throw new IllegalArgumentException("Field name cannot be null or empty");
            }
            this.parent = parent;
            this.fieldName = fieldName;
        }

        /**
         * 添加排序条件的通用方法
         * @param direction 排序方向
         * @return 当前OrderByClause实例
         */
        private OrderByClause<T> addOrderBy(String direction) {
            parent.context.addOrderByField(fieldName, direction);
            return this;
        }

        @Override
        public EntitySqlBuilder<T> asc() {
            parent.context.addOrderByField(fieldName, "ASC");
            return parent;
        }

        @Override
        public EntitySqlBuilder<T> desc() {
            parent.context.addOrderByField(fieldName, "DESC");
            return parent;
        }

        @Override
        public <V> OrderByClause<T> thenBy(FieldFunction<T, V> fieldFunction) {
            if (fieldFunction == null) {
                throw new IllegalArgumentException("Field function cannot be null");
            }
            String nextFieldName = parent.extractFieldName(fieldFunction);
            return new OrderByClauseImpl<>(parent, nextFieldName);
        }

        @Override
        public <V> OrderByClause<T> thenAsc(FieldFunction<T, V> fieldFunction) {
            if (fieldFunction == null) {
                throw new IllegalArgumentException("Field function cannot be null");
            }
            String nextFieldName = parent.extractFieldName(fieldFunction);
            parent.context.addOrderByField(nextFieldName, "ASC");
            return new OrderByClauseImpl<>(parent, nextFieldName);
        }

        @Override
        public <V> OrderByClause<T> thenDesc(FieldFunction<T, V> fieldFunction) {
            if (fieldFunction == null) {
                throw new IllegalArgumentException("Field function cannot be null");
            }
            String nextFieldName = parent.extractFieldName(fieldFunction);
            parent.context.addOrderByField(nextFieldName, "DESC");
            return new OrderByClauseImpl<>(parent, nextFieldName);
        }
    }
}