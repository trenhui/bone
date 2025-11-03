package com.bone.metadata.sdk.query.dsl;

import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.query.dsl.builder.SqlBuilder;
import com.bone.metadata.sdk.query.dsl.context.QueryContext;
// 移除了对外部Condition接口的导入，使用QueryBuilder内部的Condition接口

import java.util.List;
import java.util.Map;
import com.bone.metadata.sdk.query.dsl.context.QueryContext.JoinType;
// 移除了对外部Join接口的导入，使用QueryBuilder内部的Join接口

import java.util.List;
import java.util.function.Function;

/**
 * FluentQuery接口的默认实现类 - 提供流畅的查询API实现
 */
public class DefaultFluentQuery<T> implements QueryBuilder.FluentQuery<T> {

    private final QueryContext<T> queryContext;
    private final SqlExecutorAdapter sqlExecutorAdapter;

    public DefaultFluentQuery(Class<T> entityClass, SqlExecutorAdapter sqlExecutorAdapter) {
        this.sqlExecutorAdapter = sqlExecutorAdapter;
        this.queryContext = new QueryContext<>(entityClass, "t", this);
    }

    @Override
    public <F> QueryBuilder.Condition<T, F> where(String fieldName) {
        // 使用内部类实现Condition接口
        return new QueryBuilder.Condition<T, F>() {
            @Override
            public QueryBuilder.FluentQuery<T> eq(F value) {
                QueryContext.Condition condition = new QueryContext.Condition();
                condition.setFieldName(fieldName);
                condition.setOperator("=");
                condition.setValue1(value);
                queryContext.addCondition(condition);
                return DefaultFluentQuery.this;
            }

            @Override
            public QueryBuilder.FluentQuery<T> neq(F value) {
                QueryContext.Condition condition = new QueryContext.Condition();
                condition.setFieldName(fieldName);
                condition.setOperator("<>");
                condition.setValue1(value);
                queryContext.addCondition(condition);
                return DefaultFluentQuery.this;
            }

            @Override
            public QueryBuilder.FluentQuery<T> gt(F value) {
                QueryContext.Condition condition = new QueryContext.Condition();
                condition.setFieldName(fieldName);
                condition.setOperator(">");
                
                condition.setValue1(value);
                queryContext.addCondition(condition);
                return DefaultFluentQuery.this;
            }

            @Override
            public QueryBuilder.FluentQuery<T> gte(F value) {
                QueryContext.Condition condition = new QueryContext.Condition();
                condition.setFieldName(fieldName);
                condition.setOperator(">=");
                condition.setValue1(value);
                queryContext.addCondition(condition);
                return DefaultFluentQuery.this;
            }

            @Override
            public QueryBuilder.FluentQuery<T> lt(F value) {
                QueryContext.Condition condition = new QueryContext.Condition();
                condition.setFieldName(fieldName);
                condition.setOperator("<");
                condition.setValue1(value);
                queryContext.addCondition(condition);
                return DefaultFluentQuery.this;
            }

            @Override
            public QueryBuilder.FluentQuery<T> lte(F value) {
                QueryContext.Condition condition = new QueryContext.Condition();
                condition.setFieldName(fieldName);
                condition.setOperator("<=");
                condition.setValue1(value);
                queryContext.addCondition(condition);
                return DefaultFluentQuery.this;
            }

            @Override
            public QueryBuilder.FluentQuery<T> like(String value) {
                QueryContext.Condition condition = new QueryContext.Condition();
                condition.setFieldName(fieldName);
                condition.setOperator("LIKE");
                condition.setValue1(value);
                queryContext.addCondition(condition);
                return DefaultFluentQuery.this;
            }

            @Override
            public QueryBuilder.FluentQuery<T> notLike(String value) {
                QueryContext.Condition condition = new QueryContext.Condition();
                condition.setFieldName(fieldName);
                condition.setOperator("NOT LIKE");
                condition.setValue1(value);
                queryContext.addCondition(condition);
                return DefaultFluentQuery.this;
            }

            @Override
            public QueryBuilder.FluentQuery<T> in(java.util.Collection<F> values) {
                QueryContext.Condition condition = new QueryContext.Condition();
                condition.setFieldName(fieldName);
                condition.setOperator("IN");
                condition.setValue1(values);
                queryContext.addCondition(condition);
                return DefaultFluentQuery.this;
            }

            @Override
            public QueryBuilder.FluentQuery<T> notIn(java.util.Collection<F> values) {
                QueryContext.Condition condition = new QueryContext.Condition();
                condition.setFieldName(fieldName);
                condition.setOperator("NOT IN");
                condition.setValue1(values);
                queryContext.addCondition(condition);
                return DefaultFluentQuery.this;
            }

            @Override
            public QueryBuilder.FluentQuery<T> between(F start, F end) {
                QueryContext.Condition condition = new QueryContext.Condition();
                condition.setFieldName(fieldName);
                condition.setOperator("BETWEEN");
                condition.setValue1(start);
                condition.setValue2(end);
                queryContext.addCondition(condition);
                return DefaultFluentQuery.this;
            }

            @Override
            public QueryBuilder.FluentQuery<T> isNull() {
                QueryContext.Condition condition = new QueryContext.Condition();
                condition.setFieldName(fieldName);
                condition.setOperator("IS NULL");
                queryContext.addCondition(condition);
                return DefaultFluentQuery.this;
            }

            @Override
            public QueryBuilder.FluentQuery<T> isNotNull() {
                QueryContext.Condition condition = new QueryContext.Condition();
                condition.setFieldName(fieldName);
                condition.setOperator("IS NOT NULL");
                queryContext.addCondition(condition);
                return DefaultFluentQuery.this;
            }
        };
    }

    @Override
    public <F> QueryBuilder.Condition<T, F> where(Function<T, F> fieldGetter) {
        // 简化实现，实际应该从Lambda表达式中提取字段名
        throw new UnsupportedOperationException("Lambda expression not supported yet");
    }

    @Override
    public QueryBuilder.FluentQuery<T> orderBy(String fieldName, boolean isAsc) {
        queryContext.addOrder(new QueryContext.Order(fieldName, isAsc));
        return this;
    }

    @Override
    public QueryBuilder.FluentQuery<T> limit(int limit) {
        queryContext.setLimit(limit);
        return this;
    }

    @Override
    public QueryBuilder.FluentQuery<T> offset(int offset) {
        queryContext.setOffset(offset);
        return this;
    }

    @Override
    public QueryBuilder.FluentQuery<T> groupBy(String... fieldNames) {
        queryContext.addGroupByFields(fieldNames);
        return this;
    }

    @Override
    public <J> QueryBuilder.Join<T, J> join(Class<J> joinClass, String joinAlias) {
        queryContext.addJoin(joinClass, joinAlias, JoinType.INNER);
        return createJoinImpl(joinAlias);
    }

    @Override
    public <J> QueryBuilder.Join<T, J> leftJoin(Class<J> joinClass, String joinAlias) {
        queryContext.addJoin(joinClass, joinAlias, JoinType.LEFT);
        return createJoinImpl(joinAlias);
    }

    @Override
    public <J> QueryBuilder.Join<T, J> rightJoin(Class<J> joinClass, String joinAlias) {
        queryContext.addJoin(joinClass, joinAlias, JoinType.RIGHT);
        return createJoinImpl(joinAlias);
    }

    @Override
    public <J> QueryBuilder.Join<T, J> fullJoin(Class<J> joinClass, String joinAlias) {
        queryContext.addJoin(joinClass, joinAlias, JoinType.FULL);
        return createJoinImpl(joinAlias);
    }
    
    /**
     * 创建Join接口的实现
     */
    private <J> QueryBuilder.Join<T, J> createJoinImpl(String joinAlias) {
        return new QueryBuilder.Join<T, J>() {
            @Override
            public <F, JF> QueryBuilder.FluentQuery<T> on(String entityField, String joinEntityField) {
                QueryContext.Join currentJoin = queryContext.getCurrentJoin();
                if (currentJoin != null) {
                    QueryContext.Join.JoinCondition joinCondition = new QueryContext.Join.JoinCondition();
                    joinCondition.setEntityField(entityField);
                    joinCondition.setOperator("=");
                    joinCondition.setJoinEntityField(joinEntityField);
                    currentJoin.addJoinCondition(joinCondition);
                }
                return DefaultFluentQuery.this;
            }
            
            @Override
            public <F> QueryBuilder.Condition<T, F> where(String fieldName) {
                return DefaultFluentQuery.this.where(fieldName);
            }
            
            @Override
            public QueryBuilder.FluentQuery<T> orderBy(String fieldName, boolean isAsc) {
                return DefaultFluentQuery.this.orderBy(fieldName, isAsc);
            }
            
            @Override
            public QueryBuilder.FluentQuery<T> limit(int limit) {
                return DefaultFluentQuery.this.limit(limit);
            }
            
            @Override
            public QueryBuilder.FluentQuery<T> offset(int offset) {
                return DefaultFluentQuery.this.offset(offset);
            }
            
            @Override
            public QueryBuilder.FluentQuery<T> groupBy(String... fieldNames) {
                return DefaultFluentQuery.this.groupBy(fieldNames);
            }
        };
    }

    @Override
    public List<T> list() {
        try {
            SqlBuilder<T> sqlBuilder = new SqlBuilder<>(queryContext);
            // 创建参数映射
            Map<String, Object> paramMap = new java.util.HashMap<>();
            List<Object> params = sqlBuilder.getParameters();
            for (int i = 0; i < params.size(); i++) {
                paramMap.put("param" + i, params.get(i));
            }
            CompiledQuery query = new CompiledQuery(sqlBuilder.buildSelectSql(), paramMap);
            return sqlExecutorAdapter.execute(query, queryContext.getEntityClass());
        } catch (Exception e) {
            throw new RuntimeException("Error executing query", e);
        }
    }

    @Override
    public T single() {
        try {
            SqlBuilder<T> sqlBuilder = new SqlBuilder<>(queryContext);
            String sql = sqlBuilder.buildSelectSql();
            List<Object> parameters = sqlBuilder.getParameters();
            
            // 创建参数映射
            Map<String, Object> paramMap = new java.util.HashMap<>();
            for (int i = 0; i < parameters.size(); i++) {
                paramMap.put("param" + i, parameters.get(i));
            }
            CompiledQuery query = new CompiledQuery(sql, paramMap);
            List<T> results = sqlExecutorAdapter.execute(query, queryContext.getEntityClass());
            if (results.isEmpty()) {
                return null;
            }
            if (results.size() > 1) {
                throw new RuntimeException("Expected single result, but found " + results.size() + " results");
            }
            return results.get(0);
        } catch (Exception e) {
            throw new RuntimeException("Error executing single query", e);
        }
    }

    @Override
    public long count() {
        try {
            SqlBuilder<T> sqlBuilder = new SqlBuilder<>(queryContext);
            // 创建参数映射
            Map<String, Object> paramMap = new java.util.HashMap<>();
            List<Object> params = sqlBuilder.getParameters();
            for (int i = 0; i < params.size(); i++) {
                paramMap.put("param" + i, params.get(i));
            }
            CompiledQuery query = new CompiledQuery(sqlBuilder.buildCountSql(), paramMap);
            return sqlExecutorAdapter.executeCount(query);
        } catch (Exception e) {
            throw new RuntimeException("Error executing count query", e);
        }
    }

    @Override
    public QueryBuilder.PageResult<T> page(int pageNum, int pageSize) {
        try {
            // 设置分页参数
            offset((pageNum - 1) * pageSize);
            limit(pageSize);
            
            // 查询数据列表
            List<T> records = list();
            
            // 查询总数
            long total = count();
            
            // 计算总页数
            int totalPages = (int) Math.ceil((double) total / pageSize);
            
            // 创建分页结果
            QueryBuilder.PageResult<T> pageResult = new QueryBuilder.PageResult<>();
            pageResult.setRecords(records);
            pageResult.setTotal(total);
            pageResult.setPageNum(pageNum);
            pageResult.setPageSize(pageSize);
            pageResult.setTotalPages(totalPages);
            pageResult.setHasNext(totalPages > pageNum);
            pageResult.setHasPrevious(pageNum > 1);
            
            return pageResult;
        } catch (Exception e) {
            throw new RuntimeException("Error executing page query", e);
        }
    }

    @Override
    public boolean exists() {
        return count() > 0;
    }
}