package com.bone.metadata.sdk.sql.dialect;

import com.bone.metadata.sdk.support.config.MetadataSdkContext;

/**
 * 不同数据库的分页语法适配器
 */
public class DatabaseDialect {

    /**
     * 构建分页子句
     * @param limit  每页条数
     * @param offset 偏移量
     */
    public String buildPagination(int limit, int offset) {
        if (limit <= 0) return "";
        return switch (MetadataSdkContext.getDatabaseType()) {
            case ORACLE, SQLSERVER -> String.format("OFFSET %d ROWS FETCH NEXT %d ROWS ONLY", offset, limit);
            default -> String.format("LIMIT %d OFFSET %d", limit, offset);
        };
    }

    /**
     * 返回数据库能识别的布尔“假”字面量：
     * H2/MySQL 用 0，Postgres/Oracle 用 FALSE。
     */
    public String literalFalse() {
        return switch (MetadataSdkContext.getDatabaseType()) {
            case H2, MYSQL -> "0";
            default -> "FALSE";
        };
    }

    /**
     * 返回数据库能识别的布尔“假”字面量：
     * H2/MySQL 用 0，Postgres/Oracle 用 TRUE。
     */
    public String literalTrue() {
        return switch (MetadataSdkContext.getDatabaseType()) {
            case H2, MYSQL -> "1";
            default -> "TRUE";
        };
    }
}
