package com.bone.metadata.sdk.query.dsl.builder;

import com.bone.metadata.sdk.domain.exception.MissingTenantContextException;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.domain.spec.TableMetadataResolver;
import com.bone.metadata.sdk.query.builder.TenantFilterInjector;
import com.bone.metadata.sdk.query.dsl.context.QueryContext;
import com.bone.metadata.sdk.query.dsl.context.QueryContext.Condition;
import com.bone.metadata.sdk.query.dsl.context.QueryContext.Join;
import com.bone.metadata.sdk.query.dsl.context.QueryContext.Order;
import com.bone.metadata.sdk.query.dsl.util.SqlSafeUtils;
import com.bone.metadata.sdk.support.cache.FieldCache;
import com.bone.metadata.sdk.support.util.SqlUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/** SQL构建器 - 根据查询上下文构建参数化SQL语句 */
@Slf4j
public class SqlBuilder<T> {

  private final QueryContext<T> queryContext;
  private final StringBuilder sql = new StringBuilder();
  private final List<Object> parameters = new ArrayList<>();
  private int paramIndex = 1;

  /** 租户作用域解析结果缓存（每个 SqlBuilder 实例只解析一次；@Table 缺失等解析异常按非租户表降级）。 */
  private boolean tenantResolved;

  private boolean tenantScoped;
  private String tenantColumn;
  private Object tenantValue;

  public SqlBuilder(QueryContext<T> queryContext) {
    this.queryContext = queryContext;
  }

  /** 构建完整的SELECT查询SQL */
  public String buildSelectSql() {
    sql.setLength(0);
    parameters.clear();

    buildSelectClause();
    buildFromClause();
    buildJoinClauses();
    buildWhereClause();
    buildGroupByClause();
    buildOrderByClause();
    buildLimitOffsetClause();
    return sql.toString();
  }

  /** 构建COUNT查询SQL */
  public String buildCountSql() {
    sql.setLength(0);
    parameters.clear();

    // 创建一个完全独立的COUNT查询，不与其他构建方法共享逻辑
    String tableName = getTableName(queryContext.getEntityClass());
    sql.append("SELECT COUNT(*) FROM " + tableName);

    resolveTenantScope();
    List<Condition> conditions = queryContext.getConditions();
    boolean hasConditions = !conditions.isEmpty();
    // 处理WHERE条件（整体加括号后再 AND 租户条件，防止 caller OR 分组击穿租户隔离）
    if (hasConditions) {
      sql.append(" WHERE (");
      for (int i = 0; i < conditions.size(); i++) {
        if (i > 0) {
          sql.append(conditions.get(i).isOr() ? " OR " : " AND ");
        }
        buildCountCondition(conditions.get(i));
      }
      sql.append(")");
    }
    if (tenantScoped) {
      sql.append(hasConditions ? " AND " : " WHERE ");
      appendTenantPredicate(null);
    }

    return sql.toString();
  }

  /** 为COUNT查询构建条件，确保只处理简单条件 */
  private void buildCountCondition(Condition condition) {
    String fieldName = condition.getFieldName();
    String columnName = camelToSnake(fieldName);
    String operator = condition.getOperator();
    Object value1 = condition.getValue1();

    sql.append(columnName).append(" ").append(operator);

    switch (operator) {
      case "IN":
      case "NOT IN":
        if (value1 instanceof List) {
          List<?> values = (List<?>) value1;
          if (values.isEmpty()) {
            sql.append(" (NULL)");
          } else {
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < values.size(); i++) {
              if (i > 0) {
                placeholders.append(", ");
              }
              String paramName = "p" + paramIndex;
              placeholders.append(":").append(paramName);
              addParameter(values.get(i));
              paramIndex++;
            }
            sql.append(" (").append(placeholders).append(")");
          }
        }
        break;
      case "BETWEEN":
        String paramName1 = "p" + paramIndex;
        sql.append(" :").append(paramName1);
        addParameter(value1);
        paramIndex++;

        String paramName2 = "p" + paramIndex;
        sql.append(" AND :").append(paramName2);
        addParameter(condition.getValue2());
        paramIndex++;
        break;
      case "IS NULL":
      case "IS NOT NULL":
        // 不需要添加参数
        break;
      default:
        String paramName = "p" + paramIndex;
        sql.append(" :").append(paramName);
        addParameter(value1);
        paramIndex++;
        break;
    }
  }

  /** 构建聚合查询SQL */
  public String buildAggregateSql(String function, String fieldName) {
    sql.setLength(0);
    parameters.clear();

    // 验证字段名
    if (!SqlSafeUtils.isValidFieldName(fieldName)) {
      throw new IllegalArgumentException("Invalid field name for aggregate: " + fieldName);
    }

    String columnName = camelToSnake(fieldName);
    sql.append("SELECT ")
        .append(function)
        .append("(")
        .append(queryContext.getEntityAlias())
        .append(".")
        .append(columnName)
        .append(")");

    buildFromClause();
    buildJoinClauses();
    buildWhereClause();
    buildGroupByClause();

    return sql.toString();
  }

  /** 构建投影查询SQL（选择特定字段） */
  public String buildProjectionSql(String fieldName) {
    sql.setLength(0);
    parameters.clear();

    // 验证字段名
    if (!SqlSafeUtils.isValidFieldName(fieldName)) {
      throw new IllegalArgumentException("Invalid field name for projection: " + fieldName);
    }

    String columnName = camelToSnake(fieldName);
    sql.append("SELECT ").append(queryContext.getEntityAlias()).append(".").append(columnName);

    buildFromClause();
    buildJoinClauses();
    buildWhereClause();
    buildGroupByClause();
    buildOrderByClause();
    buildLimitOffsetClause();

    return sql.toString();
  }

  /** 构建完整的查询 CompiledQuery */
  public CompiledQuery buildQuery() {
    String sql = buildSelectSql();
    return createCompiledQuery(sql);
  }

  /** 构建计数查询 CompiledQuery */
  public CompiledQuery buildCountQuery() {
    String sql = buildCountSql();
    return createCompiledQuery(sql);
  }

  /** 构建聚合查询 CompiledQuery */
  public CompiledQuery buildAggregateQuery(String function, String fieldName) {
    String sql = buildAggregateSql(function, fieldName);
    return createCompiledQuery(sql);
  }

  /** 构建投影查询 CompiledQuery */
  public CompiledQuery buildProjectionQuery(String fieldName) {
    String sql = buildProjectionSql(fieldName);
    return createCompiledQuery(sql);
  }

  /** 创建 CompiledQuery 对象 */
  private CompiledQuery createCompiledQuery(String sql) {
    // 将参数列表转换为命名参数字典，确保与SQL中的":p1", ":p2"等命名参数匹配
    Map<String, Object> paramMap = new HashMap<>();
    for (int i = 0; i < parameters.size(); i++) {
      paramMap.put("p" + (i + 1), parameters.get(i));
    }
    return new CompiledQuery(sql, paramMap);
  }

  // /**
  //  * 参数转换工具方法
  //  */
  // private Map<String, Object> convertToParamMap(List<Object> params) {
  //     Map<String, Object> paramMap = new HashMap<>();
  //     if (params != null) {
  //         for (int i = 0; i < params.size(); i++) {
  //             paramMap.put("p" + i, params.get(i));
  //         }
  //     }
  //     return paramMap;
  // }

  /** 获取SQL参数 */
  public List<Object> getParameters() {
    return parameters;
  }

  // ===== SQL 子句构建方法 =====

  /** 构建SELECT子句 */
  private void buildSelectClause() {
    String entityAlias = queryContext.getEntityAlias();
    sql.append("SELECT ").append(entityAlias).append(".*");
  }

  /** 构建FROM子句 */
  private void buildFromClause() {
    String tableName = getTableName(queryContext.getEntityClass());
    String entityAlias = queryContext.getEntityAlias();
    sql.append(" FROM ").append(tableName).append(" ").append(entityAlias);
  }

  /** 构建JOIN子句 */
  private void buildJoinClauses() {
    for (Join join : queryContext.getJoins()) {
      String joinType = join.getJoinType().name();
      String joinTableName = getTableName(join.getJoinClass());
      String joinAlias = join.getJoinEntityAlias();

      sql.append(" ")
          .append(joinType)
          .append(" JOIN ")
          .append(joinTableName)
          .append(" ")
          .append(joinAlias)
          .append(" ON ");

      // 构建关联条件
      for (int i = 0; i < join.getJoinConditions().size(); i++) {
        Join.JoinCondition joinCondition = join.getJoinConditions().get(i);

        if (i > 0) {
          sql.append(joinCondition.isOr() ? " OR " : " AND ");
        }

        if (joinCondition.getJoinEntityField() != null) {
          // 实体字段与关联表字段相等的条件
          sql.append(queryContext.getEntityAlias())
              .append(".")
              .append(camelToSnake(joinCondition.getEntityField()))
              .append(" ")
              .append(joinCondition.getOperator())
              .append(" ")
              .append(joinAlias)
              .append(".")
              .append(camelToSnake(joinCondition.getJoinEntityField()));
        } else if (joinCondition.getValue() != null) {
          // 实体字段与值比较的条件
          sql.append(queryContext.getEntityAlias())
              .append(".")
              .append(camelToSnake(joinCondition.getEntityField()))
              .append(" ")
              .append(joinCondition.getOperator())
              .append(" :param")
              .append(parameters.size());
          addParameter(joinCondition.getValue());
        }
      }
    }
  }

  /** 构建WHERE子句 */
  private void buildWhereClause() {
    resolveTenantScope();
    List<Condition> conditions = queryContext.getConditions();
    boolean hasConditions = !conditions.isEmpty();
    if (!hasConditions && !tenantScoped) {
      return;
    }

    sql.append(" WHERE ");
    // caller 条件整体加括号后再 AND 租户条件：若直接追加，
    // "a OR tenantId=xx OR t.tenant_id = :ctx" 的 OR 链会击穿租户隔离
    if (hasConditions) {
      sql.append("(");
      for (int i = 0; i < conditions.size(); i++) {
        Condition condition = conditions.get(i);

        if (i > 0) {
          sql.append(condition.isOr() ? " OR " : " AND ");
        }

        buildCondition(condition);
      }
      sql.append(")");
    }
    if (tenantScoped) {
      if (hasConditions) {
        sql.append(" AND ");
      }
      appendTenantPredicate(queryContext.getEntityAlias());
    }
  }

  /** 追加租户谓词 {@code [alias.]tenant_id = :pN} 并绑定参数（ADR-0029，值已在 resolveTenantScope 解析）。 */
  private void appendTenantPredicate(String alias) {
    if (alias != null && !alias.isEmpty()) {
      sql.append(alias).append(".");
    }
    String paramName = "p" + paramIndex;
    sql.append(tenantColumn).append(" = :").append(paramName);
    addParameter(tenantValue);
    paramIndex++;
  }

  /**
   * 解析主表的租户作用域（每个实例一次）：
   *
   * <ul>
   *   <li>非租户表（实体未映射 {@code tenant_id}）→ 不注入（如全局目录表 DomainTemplate）；
   *   <li>租户表 → 经 {@link TenantFilterInjector#resolveDslTenantValue} 按可信上下文 &gt; caller EQ 兜底 &gt;
   *       失败关闭取值；
   *   <li>实体缺 {@code @Table}（TableMetadataResolver 拒绝）→ 降级为非租户表，不破坏既有查询（与表名解析的宽容策略一致）。
   * </ul>
   */
  private void resolveTenantScope() {
    if (tenantResolved) {
      return;
    }
    tenantResolved = true;
    try {
      TableMetadata tbl = TableMetadataResolver.load(queryContext.getEntityClass());
      Object callerTenantEq = findCallerTenantEqValue();
      Object resolved = TenantFilterInjector.resolveDslTenantValue(tbl, callerTenantEq);
      if (resolved == null) {
        return;
      }
      tenantScoped = true;
      tenantColumn = tbl.getTenantIdColumn().getName();
      tenantValue = resolved;
    } catch (MissingTenantContextException e) {
      throw e;
    } catch (Exception e) {
      // 元数据解析失败（如实体缺 @Table）按非租户表降级，不破坏既有查询
      log.debug(
          "Tenant scope resolution skipped for {}: {}",
          queryContext.getEntityClass(),
          e.getMessage());
    }
  }

  /** 找 caller 在主表显式 EQ 限定的租户条件值（字段名 tenantId 或列名 tenant_id）。 */
  private Object findCallerTenantEqValue() {
    for (Condition c : queryContext.getConditions()) {
      if (c.isHaving()) {
        continue;
      }
      if ("=".equals(c.getOperator())
          && ("tenantId".equals(c.getFieldName()) || "tenant_id".equals(c.getFieldName()))
          && c.getValue1() != null) {
        return c.getValue1();
      }
    }
    return null;
  }

  /** 构建条件表达式 */
  private void buildCondition(Condition condition) {
    String fieldName = condition.getFieldName();
    String columnName = camelToSnake(fieldName);
    String operator = condition.getOperator();
    Object value1 = condition.getValue1();
    Object value2 = condition.getValue2();

    // 直接使用实体别名
    String entityAlias = queryContext.getEntityAlias();

    // 构建条件
    switch (operator) {
      case "IN":
      case "NOT IN":
        sql.append(entityAlias)
            .append(".")
            .append(columnName)
            .append(" ")
            .append(operator)
            .append(" (");
        if (value1 instanceof List) {
          List<?> values = (List<?>) value1;
          if (values.isEmpty()) {
            sql.append("NULL");
          } else {
            for (int i = 0; i < values.size(); i++) {
              if (i > 0) {
                sql.append(", ");
              }
              String paramName = "p" + paramIndex;
              sql.append(":").append(paramName);
              addParameter(values.get(i));
              paramIndex++;
            }
          }
        }
        sql.append(")");
        break;

      case "BETWEEN":
        String paramName1 = "p" + paramIndex;
        sql.append(entityAlias)
            .append(".")
            .append(columnName)
            .append(" ")
            .append(operator)
            .append(" :")
            .append(paramName1);
        addParameter(value1);
        paramIndex++;

        String paramName2 = "p" + paramIndex;
        sql.append(" AND :").append(paramName2);
        addParameter(value2);
        paramIndex++;
        break;

      case "LIKE":
      case "NOT LIKE":
        String paramName = "p" + paramIndex;
        sql.append(entityAlias)
            .append(".")
            .append(columnName)
            .append(" ")
            .append(operator)
            .append(" :")
            .append(paramName);
        addParameter(value1);
        paramIndex++;
        break;

      case "IS NULL":
      case "IS NOT NULL":
        sql.append(entityAlias).append(".").append(columnName).append(" ").append(operator);
        // 不需要添加参数
        break;

      default:
        paramName = "p" + paramIndex;
        sql.append(entityAlias)
            .append(".")
            .append(columnName)
            .append(" ")
            .append(operator)
            .append(" :")
            .append(paramName);
        addParameter(value1);
        paramIndex++;
        break;
    }
  }

  /** 构建GROUP BY子句 */
  private void buildGroupByClause() {
    List<String> groupByFields = queryContext.getGroupByFields();
    if (groupByFields.isEmpty()) {
      return;
    }

    sql.append(" GROUP BY ")
        .append(
            groupByFields.stream()
                .map(field -> queryContext.getEntityAlias() + "." + camelToSnake(field))
                .collect(Collectors.joining(", ")));
  }

  /** 构建ORDER BY子句 */
  private void buildOrderByClause() {
    List<Order> orders = queryContext.getOrders();
    if (orders.isEmpty()) {
      return;
    }

    sql.append(" ORDER BY ")
        .append(
            orders.stream()
                .map(
                    order ->
                        queryContext.getEntityAlias()
                            + "."
                            + camelToSnake(order.getFieldName())
                            + (order.isAsc() ? " ASC" : " DESC"))
                .collect(Collectors.joining(", ")));
  }

  /** 构建LIMIT和OFFSET子句 */
  private void buildLimitOffsetClause() {
    if (queryContext.getLimit() != null) {
      // 直接拼接LIMIT值，避免参数绑定问题
      sql.append(" LIMIT ").append(queryContext.getLimit());

      if (queryContext.getOffset() != null) {
        // 直接拼接OFFSET值，避免参数绑定问题
        sql.append(" OFFSET ").append(queryContext.getOffset());
      }
    }
  }

  // ===== 工具方法 =====

  private void addParameter(Object value) {
    parameters.add(SqlUtil.toJdbcParameter(value));
  }

  /** 将字段名解析为数据库列名，优先使用 @Column 注解映射，回退到驼峰转下划线。 */
  private String camelToSnake(String fieldName) {
    if (fieldName == null) {
      return null;
    }
    // 尝试通过 FieldCache 解析 @Column 注解的实际列名
    try {
      java.lang.reflect.Field field =
          FieldCache.getFieldByName(queryContext.getEntityClass(), fieldName);
      if (field != null) {
        com.bone.metadata.sdk.domain.annotation.Column columnAnn =
            field.getAnnotation(com.bone.metadata.sdk.domain.annotation.Column.class);
        if (columnAnn != null && columnAnn.name() != null && !columnAnn.name().isEmpty()) {
          return columnAnn.name();
        }
      }
    } catch (Exception ignored) {
      // 回退到默认命名规则
    }
    // 回退：驼峰转下划线
    StringBuilder result = new StringBuilder();
    result.append(Character.toLowerCase(fieldName.charAt(0)));
    for (int i = 1; i < fieldName.length(); i++) {
      char c = fieldName.charAt(i);
      if (Character.isUpperCase(c)) {
        result.append('_');
        result.append(Character.toLowerCase(c));
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }

  /** 获取表名 - 支持自定义@Table注解 */
  private String getTableName(Class<?> entityClass) {
    try {
      // 检查是否有自定义的@Table注解
      Class<?> tableAnnotationClass = null;
      try {
        // 尝试加载自定义Table注解
        tableAnnotationClass = Class.forName("com.bone.metadata.sdk.domain.annotation.Table");
        // 获取注解实例（使用反射避免直接引用）
        java.lang.reflect.Method getAnnotationMethod =
            Class.class.getMethod("getAnnotation", Class.class);
        Object tableAnnotation = getAnnotationMethod.invoke(entityClass, tableAnnotationClass);

        if (tableAnnotation != null) {
          // 获取name属性
          java.lang.reflect.Method nameMethod = tableAnnotationClass.getMethod("value");
          Object tableNameObj = nameMethod.invoke(tableAnnotation);
          if (tableNameObj instanceof String) {
            String tableName = (String) tableNameObj;
            if (!tableName.isEmpty()) {
              return tableName;
            }
          }
        }
      } catch (ClassNotFoundException e) {
        // 自定义注解不存在，继续处理
      }
    } catch (Exception e) {
      // 解析注解失败，使用默认命名规则
    }

    // 默认使用实体类名作为表名（转为小写并添加s后缀）
    String className = entityClass.getSimpleName().toLowerCase();
    if (className.endsWith("s")
        || className.endsWith("x")
        || className.endsWith("z")
        || (className.length() > 1
            && className.endsWith("h")
            && !className.endsWith("ch")
            && !className.endsWith("sh"))) {
      return className;
    } else {
      return className + "s";
    }
  }
}
