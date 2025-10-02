package com.bone.metadata.sdk.sql.dialect;

import com.bone.metadata.sdk.domain.model.TableMetadata;

/**
 * SQL 方言接口
 */
public interface SqlDialect {

    /**
     * 获取数据库产品名称
     */
    String getDatabaseProductName();

    /**
     * 生成分页查询语句
     * @param baseSql 基础 SQL 查询
     * @param offset 偏移量
     * @param limit 每页记录数
     */
    String paginateQuery(String baseSql, long offset, long limit);

    /**
     * 生成建表语句
     * @param tableMetadata 表元数据
     */
    String generateCreateTableStatement(TableMetadata tableMetadata);

    /**
     * 获取类型映射
     * @param javaType Java 类型
     * @return 对应的数据库类型
     */
    String getColumnType(Class<?> javaType);

    /**
     * 转义标识符
     * @param identifier 标识符
     */
    String quoteIdentifier(String identifier);
}