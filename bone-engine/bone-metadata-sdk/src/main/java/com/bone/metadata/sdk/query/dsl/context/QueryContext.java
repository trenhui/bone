package com.bone.metadata.sdk.query.dsl.context;

import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.metadata.sdk.query.dsl.join.JoinImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 查询上下文管理类 - 存储和管理查询的所有状态信息
 */
public class QueryContext<T> {

    private final Class<T> entityClass;
    private final String entityAlias;
    private final QueryBuilder.FluentQuery<T> fluentQuery;
    private final List<Condition> conditions = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private final List<Join> joins = new ArrayList<>();
    private final List<String> groupByFields = new ArrayList<>();
    private Integer limit;
    private Integer offset;
    private Join currentJoin;

    public QueryContext(Class<T> entityClass, String entityAlias, QueryBuilder.FluentQuery<T> fluentQuery) {
        this.entityClass = entityClass;
        this.entityAlias = entityAlias;
        this.fluentQuery = fluentQuery;
    }

    // 内部类：条件定义
    public static class Condition {
        private String fieldName;
        private String operator;
        private Object value1;
        private Object value2;
        private boolean or = false;

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public Object getValue1() {
            return value1;
        }

        public void setValue1(Object value1) {
            this.value1 = value1;
        }

        public Object getValue2() {
            return value2;
        }

        public void setValue2(Object value2) {
            this.value2 = value2;
        }

        public boolean isOr() {
            return or;
        }

        public void setOr(boolean or) {
            this.or = or;
        }
    }

    // 内部类：排序定义
    public static class Order {
        private String fieldName;
        private boolean asc = true;

        public Order(String fieldName, boolean asc) {
            this.fieldName = fieldName;
            this.asc = asc;
        }

        public String getFieldName() {
            return fieldName;
        }

        public boolean isAsc() {
            return asc;
        }
    }

    // 内部类：关联查询定义
    public static class Join {
        private Class<?> joinClass;
        private String joinEntityAlias;
        private JoinType joinType;
        private final List<JoinCondition> joinConditions = new ArrayList<>();

        public Class<?> getJoinClass() {
            return joinClass;
        }

        public void setJoinClass(Class<?> joinClass) {
            this.joinClass = joinClass;
        }

        public String getJoinEntityAlias() {
            return joinEntityAlias;
        }

        public void setJoinEntityAlias(String joinEntityAlias) {
            this.joinEntityAlias = joinEntityAlias;
        }

        public JoinType getJoinType() {
            return joinType;
        }

        public void setJoinType(JoinType joinType) {
            this.joinType = joinType;
        }

        public List<JoinCondition> getJoinConditions() {
            return joinConditions;
        }

        public void addJoinCondition(JoinCondition joinCondition) {
            this.joinConditions.add(joinCondition);
        }

        // 内部类：关联条件定义
        public static class JoinCondition {
            private String entityField;
            private String operator;
            private String joinEntityField;
            private Object value;
            private boolean or = false;

            public String getEntityField() {
                return entityField;
            }

            public void setEntityField(String entityField) {
                this.entityField = entityField;
            }

            public String getOperator() {
                return operator;
            }

            public void setOperator(String operator) {
                this.operator = operator;
            }

            public String getJoinEntityField() {
                return joinEntityField;
            }

            public void setJoinEntityField(String joinEntityField) {
                this.joinEntityField = joinEntityField;
            }

            public Object getValue() {
                return value;
            }

            public void setValue(Object value) {
                this.value = value;
            }

            public boolean isOr() {
                return or;
            }

            public void setOr(boolean or) {
                this.or = or;
            }
        }
    }

    // 关联类型枚举
    public enum JoinType {
        INNER, LEFT, RIGHT, FULL
    }

    // Getter方法
    public Class<T> getEntityClass() {
        return entityClass;
    }

    public String getEntityAlias() {
        return entityAlias;
    }

    public QueryBuilder.FluentQuery<T> getFluentQuery() {
        return fluentQuery;
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public List<Order> getOrders() {
        return orders;
    }

    public List<Join> getJoins() {
        return joins;
    }

    public List<String> getGroupByFields() {
        return groupByFields;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public Join getCurrentJoin() {
        return currentJoin;
    }

    // 添加条件
    public void addCondition(Condition condition) {
        this.conditions.add(condition);
    }

    // 添加排序
    public void addOrder(Order order) {
        this.orders.add(order);
    }

    // 添加关联查询
    public <J> void addJoin(Class<J> joinClass, String joinEntityAlias, JoinType joinType) {
        Join join = new Join();
        join.setJoinClass(joinClass);
        join.setJoinEntityAlias(joinEntityAlias);
        join.setJoinType(joinType);
        this.joins.add(join);
        this.currentJoin = join;
    }

    // 添加分组字段
    public void addGroupByFields(String... fieldNames) {
        for (String fieldName : fieldNames) {
            this.groupByFields.add(fieldName);
        }
    }
}