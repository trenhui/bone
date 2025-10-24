package com.bone.metadata.sdk.sql.processor;

import java.util.regex.Matcher;

import com.bone.metadata.sdk.support.cache.FieldCache;
import com.bone.metadata.sdk.domain.exception.QueryExecutionException;
import com.bone.metadata.sdk.domain.exception.UndefinedFieldException;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.support.util.RepositoryClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * SQL 安全防护工具类，提供注入攻击检测和查询参数合法性校验.
 */
public final class SqlSecurityGuard {
    private static final Logger log = LoggerFactory.getLogger(SqlSecurityGuard.class);

    // SQL 注入关键词匹配模式 - 增强的安全规则
    private static final Pattern SQL_INJECTION_KEYWORD_PATTERN = Pattern.compile(
            "(?i)(?:\\b(DROP|DELETE|TRUNCATE|ALTER|CREATE|EXECUTE|GRANT|REVOKE|INSERT|UPDATE|MERGE|UNION|INTERSECT|EXCEPT|EXEC|xp_)\\b|;|--|#|/\\*|\\*/|\\+|\\|)",
            Pattern.CASE_INSENSITIVE
    );
    
    // SQL 注释模式
    private static final Pattern SQL_COMMENT_PATTERN = Pattern.compile(
            "(?i)(?:--.*?$|/\\*.*?\\*/)",
            Pattern.MULTILINE
    );

    /**
     * 扫描SQL语句中的注入攻击关键词，如检测到危险操作则抛出异常.
     *
     * @param sql 需要检查的SQL语句
     * @throws QueryExecutionException 如果发现不安全的操作或SQL为空
     */
    public static void scanForInjectionKeywords(String sql) throws QueryExecutionException {
        if (sql == null || sql.trim().isEmpty()) {
            throw new QueryExecutionException("SQL statement must not be empty");
        }
        
        // 移除SQL注释后再进行检测，避免注释中的关键词触发误报
        String sqlWithoutComments = removeComments(sql);
        
        // 检查SQL注入关键词
        Matcher keywordMatcher = SQL_INJECTION_KEYWORD_PATTERN.matcher(sqlWithoutComments);
        if (keywordMatcher.find()) {
            String matchedKeyword = keywordMatcher.group(1);
            log.warn("Blocked SQL injection attempt, detected dangerous keyword: '{}' in SQL: {}", 
                    matchedKeyword != null ? matchedKeyword : "special character", sql);
            throw new QueryExecutionException("SQL contains forbidden operations or characters: " + 
                    (matchedKeyword != null ? matchedKeyword : "special character"));
        }
    }
    
    /**
     * 移除SQL语句中的注释
     */
    private static String removeComments(String sql) {
        Matcher commentMatcher = SQL_COMMENT_PATTERN.matcher(sql);
        return commentMatcher.replaceAll("");
    }

    /**
     * 校验查询参数是否在实体类字段中明确定义，防止未定义的字段污染查询.
     *
     * @param query       编译后的查询对象
     * @param entityClass 实体类类型
     * @throws UndefinedFieldException 如果存在未定义的字段
     */
    /**
     * 校验查询参数是否在实体类字段中明确定义，防止未定义的字段污染查询.
     *
     * @param query       编译后的查询对象
     * @param entityClass 实体类类型
     * @throws UndefinedFieldException 如果存在未定义的字段
     */
    public static void validateQueryParameters(CompiledQuery query, Class<?> entityClass) throws UndefinedFieldException {
        if (query == null || query.getParameters() == null) {
            return;
        }

        // 如果是简单类型，跳过字段校验
        if (isSimpleType(entityClass)) {
            log.debug("Skipping parameter validation for simple type: {}", entityClass.getName());
            return;
        }

        log.debug("Validating query parameters for entity: {}", entityClass.getSimpleName());
        
        for (String paramKey : query.getParameters().keySet()) {
            String baseColumn = paramKey;
            
            // 处理特殊后缀
            if (paramKey.endsWith("_0") || paramKey.endsWith("_1")) {
                // 去掉后缀 _0 或 _1
                baseColumn = paramKey.substring(0, paramKey.length() - 2);
                log.trace("Normalized parameter key: {} -> {}", paramKey, baseColumn);
            }

            // 系统参数例外
            if (baseColumn.equalsIgnoreCase("ext_tenant_id") ||
                    baseColumn.equalsIgnoreCase("ext_app_code") ||
                    baseColumn.equalsIgnoreCase("ext_entity_type") ||
                    baseColumn.startsWith("_") ||  // 以_开头的系统参数
                    baseColumn.equalsIgnoreCase("page_size") ||
                    baseColumn.equalsIgnoreCase("page_number")) {
                log.trace("Skipping system parameter: {}", baseColumn);
                continue;
            }

            Object paramValue = query.getParameters().get(paramKey);
            
            // 验证字段是否存在，对于简单类型参数执行严格校验
            if (!FieldCache.hasFieldByColumn(entityClass, baseColumn) && isSimpleType(paramValue != null ? paramValue.getClass() : Object.class)) {
                String errorMsg = String.format("Field '%s' is undefined in entity %s", baseColumn, entityClass.getSimpleName());
                log.warn("Undefined field '{}' detected in query: {}, parameter value: {}", 
                        baseColumn, query.getSql(), maskSensitiveValue(paramKey, paramValue));
                throw new UndefinedFieldException(errorMsg);
            }
        }
    }

    /**
     * 校验查询参数是否在实体类字段中明确定义，防止未定义的字段污染查询.
     *
     * @param parameters       编译后的查询对象
     * @param entityClass 实体类类型
     * @throws UndefinedFieldException 如果存在未定义的字段
     */
    /**
     * 校验查询参数是否在实体类字段中明确定义，防止未定义的字段污染查询.
     *
     * @param parameters 查询参数
     * @param entityClass 实体类类型
     * @throws UndefinedFieldException 如果存在未定义的字段
     */
    public static void validateQueryParameters(Map<String, Object> parameters, Class<?> entityClass) throws UndefinedFieldException {
        if (parameters == null || parameters.isEmpty()) {
            return;
        }

        // 如果是简单类型，跳过字段校验
        if (isSimpleType(entityClass)) {
            log.debug("Skipping parameter validation for simple type: {}", entityClass.getName());
            return;
        }

        log.debug("Validating parameters for entity: {}", entityClass.getSimpleName());
        
        for (String paramKey : parameters.keySet()) {
            String baseColumn = paramKey;
            
            // 处理特殊后缀
            if (paramKey.endsWith("_0") || paramKey.endsWith("_1")) {
                // 去掉后缀 _0 或 _1
                baseColumn = paramKey.substring(0, paramKey.length() - 2);
                log.trace("Normalized parameter key: {} -> {}", paramKey, baseColumn);
            }

            // 系统参数例外
            if (baseColumn.equalsIgnoreCase("ext_tenant_id") ||
                    baseColumn.equalsIgnoreCase("ext_app_code") ||
                    baseColumn.equalsIgnoreCase("ext_entity_type") ||
                    baseColumn.startsWith("_") ||  // 以_开头的系统参数
                    baseColumn.equalsIgnoreCase("page_size") ||
                    baseColumn.equalsIgnoreCase("page_number")) {
                log.trace("Skipping system parameter: {}", baseColumn);
                continue;
            }

            Object paramValue = parameters.get(paramKey);
            
            // 验证字段是否存在，对于简单类型参数执行严格校验
            if (!FieldCache.hasFieldByColumn(entityClass, baseColumn) && isSimpleType(paramValue != null ? paramValue.getClass() : Object.class)) {
                String errorMsg = String.format("Field '%s' is undefined in entity %s", baseColumn, entityClass.getSimpleName());
                log.warn("Undefined field '{}' detected in query parameters, parameter value: {}", 
                        baseColumn, maskSensitiveValue(paramKey, paramValue));
                throw new UndefinedFieldException(errorMsg);
            }
        }
    }
    
    /**
     * 掩码敏感值，用于日志记录
     */
    private static Object maskSensitiveValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        
        String lowerKey = key.toLowerCase();
        if (lowerKey.contains("password") || lowerKey.contains("secret") || lowerKey.contains("token") || 
            lowerKey.contains("passwd") || lowerKey.contains("pwd")) {
            return "***masked***";
        }
        return value;
    }


//    public void validateQueryParameters(String sql, Map<String, Object> parameters, Class<?> resultType) {
//        // 获取结果类型的字段映射
//        Map<String, String> fieldMappings = FieldCache.getFieldMappings(resultType);
//
//        // 忽略系统参数（以下划线开头的参数）
//        Set<String> systemParams = parameters.keySet().stream()
//                .filter(key -> key.startsWith("_"))
//                .collect(Collectors.toSet());
//
//        for (String paramName : parameters.keySet()) {
//            // 跳过系统参数
//            if (systemParams.contains(paramName)) {
//                continue;
//            }
//
//            if (!fieldMappings.containsKey(paramName)) {
//                throw new UndefinedFieldException("Field '" + paramName + "' is undefined in entity " + resultType.getSimpleName());
//            }
//        }
//    }

    // 使用公共工具类替代重复方法
    private static boolean isSimpleType(Class<?> type) {
        return RepositoryClassUtils.isSimpleType(type);
    }
}