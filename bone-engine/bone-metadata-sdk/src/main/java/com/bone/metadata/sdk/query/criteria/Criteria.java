package com.bone.metadata.sdk.query.criteria;

import com.bone.core.enums.Operator;
import com.bone.metadata.sdk.support.function.SFunction;
import com.bone.metadata.sdk.domain.enums.SortDirection;
import com.bone.metadata.sdk.support.util.SqlUtil;
import com.bone.metadata.sdk.query.dsl.JoinType;
import lombok.Data;
import lombok.experimental.Accessors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 支持主表和扩展表条件、排序与分页的通用查询构造器。
 */
public class Criteria<T> {
    private final List<Condition> mainConditions = new ArrayList<>();
    private final List<Condition> extConditions = new ArrayList<>();
    private final Map<String, Object> parameters = new LinkedHashMap<>();
    private final Map<String, AtomicInteger> columnCounterMap = new ConcurrentHashMap<>();

    private final List<String> sortItems = new ArrayList<>();
    private final List<JoinInfo<?>> joinInfos = new ArrayList<>();
    private int pageSize = 5000;
    private int pageNo = 1;
    
    // 手动添加getter和setter方法
    public int getPageNo() {
        return pageNo;
    }
    
    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }
    
    public int getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
    
    public List<Condition> getMainConditions() {
        return mainConditions;
    }
    
    public List<Condition> getExtConditions() {
        return extConditions;
    }
    
    public Map<String, Object> getParameters() {
        return parameters;
    }
    
    public Map<String, AtomicInteger> getColumnCounterMap() {
        return columnCounterMap;
    }
    
    public List<String> getSortItems() {
        return sortItems;
    }
    
    public List<JoinInfo<?>> getJoinInfos() {
        return joinInfos;
    }

    private Criteria() {
    }

    public static <T> Criteria<T> create() {
        return new Criteria<>();
    }

    public static <T> Criteria<T> builder() {
        return new Criteria<>();
    }


    /**
     * 分页设置，从1开始
     */
    public Criteria<T> page(int pageNo, int pageSize) {
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        return this;
    }

    /**
     * 计算 OFFSET
     */
    public int getOffset() {
        return (pageNo - 1) * pageSize;
    }

    // ------------------ 主表条件 (Lambda + String) ------------------

    public <R> Criteria<T> eq(boolean condition, SFunction<T, R> fn, Object v) {
        return condition ? eq(SqlUtil.getFieldName(fn), v) : this;
    }

    public <R> Criteria<T> eq(SFunction<T, R> fn, Object v) {
        return eq(SqlUtil.getFieldName(fn), v);
    }

    public Criteria<T> eq(String fieldName, Object v) {
        return add(mainConditions, fieldName, Operator.EQ, false, v);
    }

    public <R> Criteria<T> ne(boolean condition, SFunction<T, R> fn, Object v) {
        return condition ? ne(SqlUtil.getFieldName(fn), v) : this;
    }

    public <R> Criteria<T> ne(SFunction<T, R> fn, Object v) {
        return ne(SqlUtil.getFieldName(fn), v);
    }

    public Criteria<T> ne(String fieldName, Object v) {
        return add(mainConditions, fieldName, Operator.NE, false, v);
    }

    public <R> Criteria<T> gt(boolean condition, SFunction<T, R> fn, Object v) {
        return condition ? gt(SqlUtil.getFieldName(fn), v) : this;
    }

    public <R> Criteria<T> gt(SFunction<T, R> fn, Object v) {
        return gt(SqlUtil.getFieldName(fn), v);
    }

    public Criteria<T> gt(String fieldName, Object v) {
        return add(mainConditions, fieldName, Operator.GT, false, v);
    }

    public <R> Criteria<T> gte(boolean condition, SFunction<T, R> fn, Object v) {
        return condition ? gte(SqlUtil.getFieldName(fn), v) : this;
    }

    public <R> Criteria<T> gte(SFunction<T, R> fn, Object v) {
        return gte(SqlUtil.getFieldName(fn), v);
    }

    public Criteria<T> gte(String fieldName, Object v) {
        return add(mainConditions, fieldName, Operator.GTE, false, v);
    }

    public <R> Criteria<T> lt(boolean condition, SFunction<T, R> fn, Object v) {
        return condition ? lt(SqlUtil.getFieldName(fn), v) : this;
    }

    public <R> Criteria<T> lt(SFunction<T, R> fn, Object v) {
        return lt(SqlUtil.getFieldName(fn), v);
    }

    public Criteria<T> lt(String fieldName, Object v) {
        return add(mainConditions, fieldName, Operator.LT, false, v);
    }

    public <R> Criteria<T> lte(boolean condition, SFunction<T, R> fn, Object v) {
        return condition ? lte(SqlUtil.getFieldName(fn), v) : this;
    }

    public <R> Criteria<T> lte(SFunction<T, R> fn, Object v) {
        return lte(SqlUtil.getFieldName(fn), v);
    }

    public Criteria<T> lte(String fieldName, Object v) {
        return add(mainConditions, fieldName, Operator.LTE, false, v);
    }

    public <R> Criteria<T> like(boolean condition, SFunction<T, R> fn, String v) {
        return condition ? like(fn, v) : this;
    }

    public <R> Criteria<T> like(SFunction<T, R> fn, String v) {
        return like(SqlUtil.getFieldName(fn), v);
    }

    public Criteria<T> like(String fieldName, String v) {
        return add(mainConditions, fieldName, Operator.LIKE, false, v);
    }


    public <R> Criteria<T> notLike(boolean condition, SFunction<T, R> fn, String v) {
        return condition ? notLike(fn, v) : this;
    }

    public <R> Criteria<T> notLike(SFunction<T, R> fn, String v) {
        return notLike(SqlUtil.getFieldName(fn), v);
    }

    public Criteria<T> notLike(String fieldName, String v) {
        return add(mainConditions, fieldName, Operator.NOT_LIKE, false, v);
    }

    public <R> Criteria<T> likeLeft(boolean condition, SFunction<T, R> fn, String v) {
        return condition ? likeLeft(fn, v) : this;
    }

    public <R> Criteria<T> likeLeft(SFunction<T, R> fn, String v) {
        return add(mainConditions, SqlUtil.getFieldName(fn), Operator.LIKE_LEFT, false, v);
    }

    public <R> Criteria<T> likeRight(boolean condition, SFunction<T, R> fn, String v) {
        return condition ? likeRight(fn, v) : this;
    }

    public <R> Criteria<T> likeRight(SFunction<T, R> fn, String v) {
        return add(mainConditions, SqlUtil.getFieldName(fn), Operator.LIKE_RIGHT, false, v);
    }


    public <R> Criteria<T> in(boolean condition, SFunction<T, R> fn, Object... vs) {
        return condition ? in(fn, vs) : this;
    }

    public <R> Criteria<T> in(SFunction<T, R> fn, Object... vs) {
        return in(SqlUtil.getFieldName(fn), vs);
    }

    public Criteria<T> in(String fieldName, Object... vs) {
        return add(mainConditions, fieldName, Operator.IN, false, vs);
    }

    public <R> Criteria<T> notIn(boolean condition, SFunction<T, R> fn, Object... vs) {
        return condition ? notIn(fn, vs) : this;
    }

    public <R> Criteria<T> notIn(SFunction<T, R> fn, Object... vs) {
        return notIn(SqlUtil.getFieldName(fn), vs);
    }

    public Criteria<T> notIn(String fieldName, Object... vs) {
        return add(mainConditions, fieldName, Operator.NOT_IN, false, vs);
    }

    public <R> Criteria<T> between(boolean condition, SFunction<T, R> fn, Object a, Object b) {
        return condition ? between(SqlUtil.getFieldName(fn), a, b) : this;
    }

    public <R> Criteria<T> between(SFunction<T, R> fn, Object a, Object b) {
        return between(SqlUtil.getFieldName(fn), a, b);
    }

    public Criteria<T> between(String fieldName, Object a, Object b) {
        return add(mainConditions, fieldName, Operator.BETWEEN, false, a, b);
    }

    public <R> Criteria<T> isNull(boolean condition, SFunction<T, R> fn) {
        return condition ? isNull(SqlUtil.getFieldName(fn)) : this;
    }

    public <R> Criteria<T> isNull(SFunction<T, R> fn) {
        return isNull(SqlUtil.getFieldName(fn));
    }

    public Criteria<T> isNull(String fieldName) {
        return add(mainConditions, fieldName, Operator.IS_NULL, false);
    }

    public <R> Criteria<T> isNotNull(boolean condition, SFunction<T, R> fn) {
        return condition ? isNotNull(SqlUtil.getFieldName(fn)) : this;
    }


    public <R> Criteria<T> isNotNull(SFunction<T, R> fn) {
        return isNotNull(SqlUtil.getFieldName(fn));
    }

    public Criteria<T> isNotNull(String fieldName) {
        return add(mainConditions, fieldName, Operator.IS_NOT_NULL, false);
    }

    // ------------------ 扩展表条件 (Lambda + String) ------------------

    public <R> Criteria<T> eqExtra(boolean condition, SFunction<T, R> fn, Object v) {
        return condition ? eqExtra(SqlUtil.getFieldName(fn), v) : this;
    }

    public <R> Criteria<T> eqExtra(SFunction<T, R> fn, Object v) {
        return eqExtra(SqlUtil.getFieldName(fn), v);
    }

    public Criteria<T> eqExtra(String fieldName, Object v) {
        return add(extConditions, fieldName, Operator.EQ, true, v);
    }


    public <R> Criteria<T> neExtra(boolean condition, SFunction<T, R> fn, Object v) {
        return condition ? neExtra(SqlUtil.getFieldName(fn), v) : this;
    }

    public <R> Criteria<T> neExtra(SFunction<T, R> fn, Object v) {
        return neExtra(SqlUtil.getFieldName(fn), v);
    }

    public Criteria<T> neExtra(String fieldName, Object v) {
        return add(extConditions, fieldName, Operator.NE, true, v);
    }

    public <R> Criteria<T> gtExtra(boolean condition, SFunction<T, R> fn, Object v) {
        return condition ? gtExtra(SqlUtil.getFieldName(fn), v) : this;
    }

    public <R> Criteria<T> gtExtra(SFunction<T, R> fn, Object v) {
        return gtExtra(SqlUtil.getFieldName(fn), v);
    }

    public Criteria<T> gtExtra(String fieldName, Object v) {
        return add(extConditions, fieldName, Operator.GT, true, v);
    }

    public <R> Criteria<T> gteExtra(boolean condition, SFunction<T, R> fn, Object v) {
        return condition ? gteExtra(SqlUtil.getFieldName(fn), v) : this;
    }

    public <R> Criteria<T> gteExtra(SFunction<T, R> fn, Object v) {
        return gteExtra(SqlUtil.getFieldName(fn), v);
    }

    public Criteria<T> gteExtra(String fieldName, Object v) {
        return add(extConditions, fieldName, Operator.GTE, true, v);
    }

    public <R> Criteria<T> ltExtra(boolean condition, SFunction<T, R> fn, Object v) {
        return condition ? ltExtra(SqlUtil.getFieldName(fn), v) : this;
    }

    public <R> Criteria<T> ltExtra(SFunction<T, R> fn, Object v) {
        return ltExtra(SqlUtil.getFieldName(fn), v);
    }

    public Criteria<T> ltExtra(String fieldName, Object v) {
        return add(extConditions, fieldName, Operator.LT, true, v);
    }

    public <R> Criteria<T> lteExtra(boolean condition, SFunction<T, R> fn, Object v) {
        return condition ? lteExtra(SqlUtil.getFieldName(fn), v) : this;
    }

    public <R> Criteria<T> lteExtra(SFunction<T, R> fn, Object v) {
        return lteExtra(SqlUtil.getFieldName(fn), v);
    }

    public Criteria<T> lteExtra(String fieldName, Object v) {
        return add(extConditions, fieldName, Operator.LTE, true, v);
    }

    public <R> Criteria<T> likeExtra(boolean condition, SFunction<T, R> fn, String v) {
        return condition ? likeExtra(fn, v) : this;
    }

    public <R> Criteria<T> likeExtra(SFunction<T, R> fn, String v) {
        return likeExtra(SqlUtil.getFieldName(fn), v);
    }

    public Criteria<T> likeExtra(String fieldName, String v) {
        return add(extConditions, fieldName, Operator.LIKE, true, v);
    }

    public <R> Criteria<T> inExtra(boolean condition, SFunction<T, R> fn, Object... vs) {
        return condition ? inExtra(SqlUtil.getFieldName(fn), vs) : this;
    }

    public <R> Criteria<T> inExtra(SFunction<T, R> fn, Object... vs) {
        return inExtra(SqlUtil.getFieldName(fn), vs);
    }

    public Criteria<T> inExtra(String fieldName, Object... vs) {
        return add(extConditions, fieldName, Operator.IN, true, vs);
    }

    public <R> Criteria<T> notInExtra(boolean condition, SFunction<T, R> fn, Object... vs) {
        return condition ? notInExtra(SqlUtil.getFieldName(fn), vs) : this;
    }

    public <R> Criteria<T> notInExtra(SFunction<T, R> fn, Object... vs) {
        return notInExtra(SqlUtil.getFieldName(fn), vs);
    }

    public Criteria<T> notInExtra(String fieldName, Object... vs) {
        return add(extConditions, fieldName, Operator.NOT_IN, true, vs);
    }

    public <R> Criteria<T> betweenExtra(boolean condition, SFunction<T, R> fn, Object a, Object b) {
        return condition ? betweenExtra(SqlUtil.getFieldName(fn), a, b) : this;
    }

    public <R> Criteria<T> betweenExtra(SFunction<T, R> fn, Object a, Object b) {
        return betweenExtra(SqlUtil.getFieldName(fn), a, b);
    }

    public Criteria<T> betweenExtra(String fieldName, Object a, Object b) {
        return add(extConditions, fieldName, Operator.BETWEEN, true, a, b);
    }

    public <R> Criteria<T> isNullExtra(boolean condition, SFunction<T, R> fn) {
        return condition ? isNullExtra(SqlUtil.getFieldName(fn)) : this;
    }

    public <R> Criteria<T> isNullExtra(SFunction<T, R> fn) {
        return isNullExtra(SqlUtil.getFieldName(fn));
    }

    public Criteria<T> isNullExtra(String fieldName) {
        return add(extConditions, fieldName, Operator.IS_NULL, true);
    }

    public <R> Criteria<T> isNotNullExtra(boolean condition, SFunction<T, R> fn) {
        return condition ? isNotNullExtra(SqlUtil.getFieldName(fn)) : this;
    }

    public <R> Criteria<T> isNotNullExtra(SFunction<T, R> fn) {
        return isNotNullExtra(SqlUtil.getFieldName(fn));
    }

    public Criteria<T> isNotNullExtra(String fieldName) {
        return add(extConditions, fieldName, Operator.IS_NOT_NULL, true);
    }


    /**
     * 是否需要关联扩展表
     */
    public boolean requiresExtJoin() {
        return !extConditions.isEmpty();
    }

    /**
     * 添加关联表信息
     */
    public <J> Criteria<T> addJoinInfo(Class<J> joinEntityClass, JoinType joinType, String joinCondition, Map<String, Object> joinParameters) {
        JoinInfo<J> joinInfo = new JoinInfo<>(joinEntityClass, joinType, joinCondition, joinParameters);
        this.joinInfos.add(joinInfo);
        return this;
    }

    /**
     * 获取所有关联表信息
     */
    // 所有getter方法已在前面定义，这里不再重复定义
    
    /**
     * 关联表信息内部类
     */
    public static class JoinInfo<T> {
        private Class<T> joinEntityClass;
        private JoinType joinType;
        private String joinCondition;
        private Map<String, Object> joinParameters;
        
        public JoinInfo(Class<T> joinEntityClass, JoinType joinType, String joinCondition, Map<String, Object> joinParameters) {
            this.joinEntityClass = joinEntityClass;
            this.joinType = joinType;
            this.joinCondition = joinCondition;
            this.joinParameters = joinParameters;
        }
        
        public Class<T> getJoinEntityClass() {
            return joinEntityClass;
        }
        
        public JoinType getJoinType() {
            return joinType;
        }
        
        public String getJoinCondition() {
            return joinCondition;
        }
        
        public Map<String, Object> getJoinParameters() {
            return joinParameters;
        }
    }

    public <R> Criteria<T> addSort(boolean condition, SFunction<T, R> fn, SortDirection dir) {
        return condition ? addSort(SqlUtil.getFieldName(fn), dir) : this;
    }

    public <R> Criteria<T> addSort(SFunction<T, R> fn, SortDirection dir) {
        return addSort(SqlUtil.getFieldName(fn), dir);
    }

    /**
     * 添加排序
     */
    public Criteria<T> addSort(String field, SortDirection dir) {
        sortItems.add(SqlUtil.toSnakeCase(field) + " " + dir.getDirection());
        return this;
    }

    // ------------------ SQL 片段生成 ------------------

    /**
     * 返回不含 WHERE/ORDER BY 的纯条件
     */
    public String whereSql() {
        List<String> all = new ArrayList<>();
        all.addAll(mainConditions.stream().map(Condition::toSql).toList());
        all.addAll(extConditions.stream().map(Condition::toSql).toList());
        return String.join(" AND ", all);
    }

    /**
     * 返回带 WHERE/ORDER BY 的完整片段
     */
    public String toSql() {
        String w = whereSql();
        StringBuilder sb = new StringBuilder();
        if (!w.isEmpty()) {
            sb.append(" WHERE ").append(w);
        }
        if (!sortItems.isEmpty()) {
            sb.append(" ORDER BY ").append(String.join(", ", sortItems));
        }
        return sb.toString();
    }

    // ------------------ 私有方法 ------------------

    private Criteria<T> add(List<Condition> target,
                            String fieldName,
                            Operator op,
                            boolean ext,
                            Object... vals) {
        String column = SqlUtil.toSnakeCase(fieldName);
        String paramName = generateParamName(column);
        Condition cond = new Condition(SqlUtil.toCamelCase(fieldName),column, paramName, op, ext, vals);
        target.add(cond);
        bind(cond);
        return this;
    }

    private String generateParamName(String columnName) {
        AtomicInteger counter = columnCounterMap.computeIfAbsent(columnName, k -> new AtomicInteger(0));
        return columnName + "_" + counter.getAndIncrement();
    }

    private void bind(Condition cond) {
        String paramName = cond.getParamName();
        switch (cond.getOperator()) {
            case BETWEEN -> {
                parameters.put(cond.getColumn() + "_0", cond.getValues()[0]);
                parameters.put(cond.getColumn() + "_1", cond.getValues()[1]);
            }
            case IN, NOT_IN -> parameters.put(paramName,
                    cond.getValues().length > 1 ? Arrays.asList(cond.getValues())
                            : cond.getValues()[0]);
            case IS_NULL, IS_NOT_NULL -> { /* no param */ }
            default -> parameters.put(paramName, cond.getValues()[0]);
        }
    }
}