package com.bone.metadata.sdk.query.criteria;

import com.bone.core.enums.Operator;
import com.bone.metadata.sdk.domain.enums.DatabaseType;
import com.bone.metadata.sdk.support.config.MetadataSdkContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 查询条件模型，支持主表(m)和扩展表(ext)前缀。
 */
@Slf4j
// 移除@Data注解，显式添加必要的getter方法
public class Condition {
    
    // 显式添加getter方法
    public String getParamName() {
        return paramName;
    }
    
    public String getColumn() {
        return column;
    }
    
    public Operator getOperator() {
        return operator;
    }
    
    public Object[] getValues() {
        return values;
    }
    
    // 添加缺失的getter方法
    public String getFieldName() {
        return fieldName;
    }
    
    public boolean isExtension() {
        return extension;
    }
    /**
     * 字段（snakecase）
     */
    private final String fieldName;

    /**
     * 物理列名（snake_case）
     */
    private final String column;

    /**
     * 参数名
     */
    private final String paramName;
    /**
     * 操作符
     */
    private final Operator operator;
    /**
     * 参数值数组
     */
    private final Object[] values;
    /**
     * 是否扩展表字段
     */
    private final boolean extension;

    // LIKE 模板：数据库类型 -> SQL 生成函数
    private static final Map<DatabaseType, Function<LikeContext, String>> LIKE_TEMPLATES = new HashMap<>();

    // LIKE 上下文（传递列名、参数名、前后缀）
    private record LikeContext(String column, String paramName, String prefix, String suffix, boolean notLike) {}

    // 静态初始化块：配置 LIKE 模板
    static {
        // MySQL/OceanBase/H2 - CONCAT 风格，需要 ESCAPE '\\\\'
        Function<LikeContext, String> mysqlLike = ctx -> {
            String notKeyword = ctx.notLike ? "NOT " : "";
            return String.format("%s %sLIKE CONCAT('%s', :%s, '%s') ESCAPE '\\\\'",
                    ctx.column, notKeyword, ctx.prefix, ctx.paramName, ctx.suffix);
        };
        LIKE_TEMPLATES.put(DatabaseType.MYSQL, mysqlLike);
        LIKE_TEMPLATES.put(DatabaseType.OceanBase, mysqlLike);
        LIKE_TEMPLATES.put(DatabaseType.H2, mysqlLike);

        // PostgreSQL - 使用标准LIKE语法，参数值需要预先处理
        Function<LikeContext, String> postgresLike = ctx -> {
            String notKeyword = ctx.notLike ? "NOT " : "";
            return String.format("%s %sLIKE :%s ESCAPE '\\\\'", ctx.column, notKeyword, ctx.paramName);
        };
        LIKE_TEMPLATES.put(DatabaseType.POSTGRESQL, postgresLike);

        // Oracle - 使用标准LIKE语法，参数值需要预先处理
        Function<LikeContext, String> oracleLike = ctx -> {
            String notKeyword = ctx.notLike ? "NOT " : "";
            return String.format("%s %sLIKE :%s ESCAPE '\\\\'", ctx.column, notKeyword, ctx.paramName);
        };
        LIKE_TEMPLATES.put(DatabaseType.ORACLE, oracleLike);

        // SQL Server - 使用标准LIKE语法，参数值需要预先处理
        Function<LikeContext, String> sqlServerLike = ctx -> {
            String notKeyword = ctx.notLike ? "NOT " : "";
            return String.format("%s %sLIKE :%s ESCAPE '\\\\'", ctx.column, notKeyword, ctx.paramName);
        };
        LIKE_TEMPLATES.put(DatabaseType.SQLSERVER, sqlServerLike);

        // 达梦 - CONCAT 风格，需要 ESCAPE '\\'
        Function<LikeContext, String> dmLike = ctx -> {
            String notKeyword = ctx.notLike ? "NOT " : "";
            return String.format("%s %sLIKE CONCAT('%s', :%s, '%s') ESCAPE '\\'",
                    ctx.column, notKeyword, ctx.prefix, ctx.paramName, ctx.suffix);
        };
        LIKE_TEMPLATES.put(DatabaseType.DM, dmLike);
    }

    public Condition(String fieldName, String column, String paramName, Operator operator, boolean extension, Object... values) {
        this.fieldName = fieldName;
        this.column = column;
        this.paramName = paramName;
        this.operator = operator;
        this.extension = extension;
        this.values = values;
    }

    public Condition(String fieldName, String column, String paramName, Operator operator, Object... values) {
        this.fieldName = fieldName;
        this.column = column;
        this.paramName = paramName;
        this.operator = operator;
        this.extension = false;
        this.values = values;
    }

    /**
     * 生成 SQL 片段，只返回纯列名，表别名由上层调用者添加
     */
    public String toSql() {
        return switch (operator) {
            case EQ -> String.format("%s = :%s", column, paramName);
            case NE -> String.format("%s <> :%s", column, paramName);
            case GT -> String.format("%s > :%s", column, paramName);
            case GTE -> String.format("%s >= :%s", column, paramName);
            case LT -> String.format("%s < :%s", column, paramName);
            case LTE -> String.format("%s <= :%s", column, paramName);
            case LIKE -> buildLikeSql(column, "%%", "%%", false);
            case NOT_LIKE -> buildLikeSql(column, "%%", "%%", true);
            case LIKE_LEFT -> buildLikeSql(column, "%%", "", false);
            case LIKE_RIGHT -> buildLikeSql(column, "", "%%", false);
            case IN -> String.format("%s IN (:%s)", column, paramName);
            case NOT_IN -> String.format("%s NOT IN (:%s)", column, paramName);
            case BETWEEN -> String.format("%s BETWEEN :%s_0 AND :%s_1", column, column, column);
            case IS_NULL -> column + " IS NULL";
            case IS_NOT_NULL -> column + " IS NOT NULL";
            default -> throw new IllegalStateException("Unsupported operator " + operator);
        };
    }

    /**
     * 构建 LIKE SQL 片段，适配数据库方言
     */
    private String buildLikeSql(String column, String prefix, String suffix, boolean notLike) {
        DatabaseType dbType = getCurrentDatabaseType();

        // 对于PostgreSQL、Oracle、SQL Server，使用标准LIKE语法
        // 参数值需要在设置参数时预先处理好通配符
        if (dbType == DatabaseType.POSTGRESQL ||
                dbType == DatabaseType.ORACLE ||
                dbType == DatabaseType.SQLSERVER) {
            String notKeyword = notLike ? "NOT " : "";
            return String.format("%s %sLIKE :%s ESCAPE '\\\\'", column, notKeyword, paramName);
        }

        // 对于MySQL、达梦等使用CONCAT的数据库
        LikeContext ctx = new LikeContext(column, paramName, prefix, suffix, notLike);
        Function<LikeContext, String> template = LIKE_TEMPLATES.get(dbType);
        if (template == null) {
            template = LIKE_TEMPLATES.get(DatabaseType.MYSQL);
            log.warn("未找到数据库类型 {} 的LIKE模板，使用MySQL默认模板", dbType);
        }

        return template.apply(ctx);
    }

    /**
     * 获取当前数据库类型
     */
    private static DatabaseType getCurrentDatabaseType() {
        try {
            return MetadataSdkContext.getDatabaseType();
        } catch (Exception e) {
            log.warn("无法获取数据库类型，fallback to MySQL", e);
            return DatabaseType.MYSQL;
        }
    }
}