package com.bone.metadata.sdk.sql.processor;

import com.bone.metadata.sdk.domain.exception.QueryExecutionException;
import com.bone.metadata.sdk.domain.exception.UndefinedFieldException;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.support.cache.FieldCache;
import com.bone.metadata.sdk.support.util.RepositoryClassUtils;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** SQL 安全防护工具类，提供注入攻击检测和查询参数合法性校验. */
public final class SqlSecurityGuard {
  private static final Logger log = LoggerFactory.getLogger(SqlSecurityGuard.class);

  // SQL 注入关键词匹配模式（仅用于 SELECT 语句的注入检测）
  private static final Pattern SQL_INJECTION_KEYWORD_PATTERN =
      Pattern.compile("(?i)(?:\\b(DROP|TRUNCATE|ALTER|CREATE|EXECUTE|GRANT|REVOKE)\\b|;)");

  /**
   * 扫描SQL语句中的注入攻击关键词，如检测到危险操作则抛出异常.
   *
   * <p>对于 SDK 内部构建的写操作（INSERT/UPDATE/DELETE），跳过注入检测， 因为这些 SQL 由 SDK 安全构建，不会包含注入风险。 仅对 SELECT
   * 语句进行注入关键词检查。
   *
   * @param sql 需要检查的SQL语句
   * @throws QueryExecutionException 如果发现不安全的操作或SQL为空
   */
  public static void scanForInjectionKeywords(String sql) throws QueryExecutionException {
    if (sql == null || sql.trim().isEmpty()) {
      throw new QueryExecutionException("SQL statement must not be empty");
    }

    // SDK 内部构建的写操作 SQL 安全可信，跳过注入检测
    String trimmedSql = sql.trim().toUpperCase();
    if (trimmedSql.startsWith("INSERT ")
        || trimmedSql.startsWith("UPDATE ")
        || trimmedSql.startsWith("DELETE ")) {
      return;
    }

    if (SQL_INJECTION_KEYWORD_PATTERN.matcher(sql).find()) {
      log.warn("Blocked SQL injection attempt, detected dangerous keywords: {}", sql);
      throw new QueryExecutionException("SQL contains forbidden operations");
    }
  }

  /**
   * 校验查询参数是否在实体类字段中明确定义，防止未定义的字段污染查询.
   *
   * @param query 编译后的查询对象
   * @param entityClass 实体类类型
   * @throws UndefinedFieldException 如果存在未定义的字段
   */
  public static void validateQueryParameters(CompiledQuery query, Class<?> entityClass)
      throws UndefinedFieldException {

    // 如果是简单类型，跳过字段校验
    if (RepositoryClassUtils.isSimpleType(entityClass)) {
      return;
    }

    for (String paramKey : query.getParameters().keySet()) {
      String baseColumn = paramKey;
      if (paramKey.endsWith("_0") || paramKey.endsWith("_1")) {
        // 去掉后缀 _0 或 _1
        baseColumn = paramKey.substring(0, paramKey.length() - 2);
      }

      if (baseColumn.equalsIgnoreCase("ext_tenant_id")
          || baseColumn.equalsIgnoreCase("ext_app_code")
          || baseColumn.equalsIgnoreCase("ext_entity_type")) {
        continue;
      }

      if (!FieldCache.hasFieldByColumn(entityClass, baseColumn)
          && RepositoryClassUtils.isSimpleType(query.getParameters().get(paramKey).getClass())) {
        String errorMsg =
            String.format(
                "Field '%s' is undefined in entity %s", baseColumn, entityClass.getSimpleName());
        log.warn("Undefined field '{}' detected in query: {}", baseColumn, query.getSql());
        throw new UndefinedFieldException(errorMsg);
      }
    }
  }

  /**
   * 校验查询参数是否在实体类字段中明确定义，防止未定义的字段污染查询.
   *
   * @param parameters 编译后的查询对象
   * @param entityClass 实体类类型
   * @throws UndefinedFieldException 如果存在未定义的字段
   */
  public static void validateQueryParameters(Map<String, Object> parameters, Class<?> entityClass)
      throws UndefinedFieldException {

    // 如果是简单类型，跳过字段校验
    if (RepositoryClassUtils.isSimpleType(entityClass)) {
      return;
    }

    for (String paramKey : parameters.keySet()) {
      String baseColumn = paramKey;
      if (paramKey.endsWith("_0") || paramKey.endsWith("_1")) {
        // 去掉后缀 _0 或 _1
        baseColumn = paramKey.substring(0, paramKey.length() - 2);
      }

      if (baseColumn.equalsIgnoreCase("ext_tenant_id")
          || baseColumn.equalsIgnoreCase("ext_app_code")
          || baseColumn.equalsIgnoreCase("ext_entity_type")) {
        continue;
      }

      if (!FieldCache.hasFieldByColumn(entityClass, baseColumn)
          && RepositoryClassUtils.isSimpleType(parameters.get(paramKey).getClass())) {
        String errorMsg =
            String.format(
                "Field '%s' is undefined in entity %s", baseColumn, entityClass.getSimpleName());
        log.warn("Undefined field '{}' detected in query", baseColumn);
        throw new UndefinedFieldException(errorMsg);
      }
    }
  }

  //    public void validateQueryParameters(String sql, Map<String, Object> parameters, Class<?>
  // resultType) {
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
  //                throw new UndefinedFieldException("Field '" + paramName + "' is undefined in
  // entity " + resultType.getSimpleName());
  //            }
  //        }
  //    }

  // 使用RepositoryClassUtils中的isSimpleType方法
}
