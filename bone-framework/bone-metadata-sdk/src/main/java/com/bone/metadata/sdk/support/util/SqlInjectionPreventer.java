package com.bone.metadata.sdk.support.util;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * SQL注入防护工具类
 */
public class SqlInjectionPreventer {
    // 聚合表达式模式：可以是聚合函数或字段名
    private static final Pattern SQL_AGGREGATION_PATTERN =
            Pattern.compile("^([a-zA-Z_][a-zA-Z0-9_]*\\([a-zA-Z_*][a-zA-Z0-9_,.*\\s]*\\)|[a-zA-Z_][a-zA-Z0-9_.]*)(\\s+[aA][sS]\\s+[a-zA-Z_][a-zA-Z0-9_]*)?$");

    private static final Pattern SQL_FIELD_PATTERN =
            Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.]*$");

    // 更新后的HAVING条件模式，支持聚合函数和别名
    private static final Pattern SQL_HAVING_PATTERN =
            Pattern.compile("^([a-zA-Z_][a-zA-Z0-9_]*\\([a-zA-Z_*][a-zA-Z0-9_,.*\\s]*\\)|[a-zA-Z_][a-zA-Z0-9_.]*)\\s*(=|!=|>|<|>=|<=|\\s+[iI][nN]\\s*\\()\\s*[a-zA-Z0-9_.,'\\s-]+$", Pattern.CASE_INSENSITIVE);

    /**
     * 验证并清理聚合表达式
     */
    public static String sanitizeAggregation(String aggregation) {
        if (!StringUtils.hasText(aggregation)) {
            throw new IllegalArgumentException("聚合表达式不能为空");
        }

        if (!SQL_AGGREGATION_PATTERN.matcher(aggregation).matches()) {
            throw new IllegalArgumentException("无效的聚合表达式: " + aggregation);
        }

        return aggregation;
    }

    /**
     * 验证并清理字段名
     */
    public static String sanitizeFieldName(String fieldName) {
        if (!StringUtils.hasText(fieldName)) {
            throw new IllegalArgumentException("字段名不能为空");
        }

        if (!SQL_FIELD_PATTERN.matcher(fieldName).matches()) {
            throw new IllegalArgumentException("无效的字段名: " + fieldName);
        }

        return fieldName;
    }

    /**
     * 验证并清理HAVING条件
     */
    public static String sanitizeHavingCondition(String havingCondition) {
        if (!StringUtils.hasText(havingCondition)) {
            throw new IllegalArgumentException("HAVING条件不能为空");
        }

        // 首先尝试匹配聚合函数模式
        if (SQL_AGGREGATION_PATTERN.matcher(havingCondition.split("\\s+")[0]).matches()) {
            // 如果是聚合函数开头的条件，使用更宽松的验证
            Pattern AGGREGATE_HAVING_PATTERN =
                    Pattern.compile("^([a-zA-Z_][a-zA-Z0-9_]*\\([a-zA-Z_*][a-zA-Z0-9_,.*\\s]*\\))\\s*(=|!=|>|<|>=|<=)\\s*[a-zA-Z0-9_.,'\\s-]+$", Pattern.CASE_INSENSITIVE);

            if (!AGGREGATE_HAVING_PATTERN.matcher(havingCondition).matches()) {
                throw new IllegalArgumentException("无效的HAVING条件: " + havingCondition);
            }
        } else {
            // 对于普通字段条件，使用原有的验证
            if (!SQL_HAVING_PATTERN.matcher(havingCondition).matches()) {
                throw new IllegalArgumentException("无效的HAVING条件: " + havingCondition);
            }
        }

        return havingCondition;
    }
}