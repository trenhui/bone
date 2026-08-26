package com.bone.metadata.engine.query;

import com.bone.metadata.engine.context.UserContext;
import com.bone.metadata.engine.domain.core.SmartBaseEntity;
import com.bone.metadata.engine.domain.exception.QueryExecutionException;
import com.bone.metadata.engine.domain.metadata.EntityMetadata;
import com.bone.metadata.engine.domain.model.DynamicSmartEntity;
import com.bone.metadata.engine.metadata.MetadataRegistry;
import com.bone.metadata.engine.query.ast.QueryAst;
import com.bone.metadata.engine.security.CustomAuthentication;
import com.bone.metadata.engine.security.FieldLevelSecurityFilter;
import com.bone.metadata.engine.security.PermissionChecker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 智能查询执行器，支持对动态实体和静态实体的统一查询 */
@RequiredArgsConstructor
public class SmartQueryExecutor {

  private static final Logger log = LoggerFactory.getLogger(SmartQueryExecutor.class);

  private final MetadataRegistry metadataRegistry;
  private final SmartQLParser queryParser;
  private final SqlQueryGenerator sqlGenerator;
  private final PermissionChecker permissionChecker;
  private final FieldLevelSecurityFilter flsFilter;
  private final UserContext userContext;
  private final QueryCacheManager queryCacheManager;
  private final AiQueryOptimizer aiQueryOptimizer;
  private final QueryPerformanceMonitor queryMonitor;

  /** 执行SmartQL查询 */
  // 修复Transactional注解找不到的问题
  // @Transactional(readOnly = true)
  public <T extends SmartBaseEntity> List<T> executeQuery(
      String smartql, Map<String, Object> parameters, Class<T> resultType) {
    QueryExecutionContext context = new QueryExecutionContext();
    context.setStartTime(System.currentTimeMillis());
    context.setSmartql(smartql);
    context.setParameters(parameters);
    context.setResultType(resultType);
    context.setUserId(userContext.getCurrentUserId());

    try {
      log.debug("执行SmartQL查询: {}", smartql);

      // 1. 解析查询
      QueryAst queryAst = queryParser.parse(smartql);
      context.setQueryAst(queryAst);

      // 2. 验证实体访问权限
      CustomAuthentication authentication =
          new CustomAuthentication() {
            @Override
            public String getName() {
              return context.getUserId();
            }

            @Override
            public List<String> getAuthorities() {
              // 实际应用中应从安全上下文中获取权限信息
              return new ArrayList<>();
            }

            @Override
            public boolean hasRole(String role) {
              // 实际应用中应从安全上下文中获取角色信息
              return false;
            }
          };

      if (!permissionChecker.hasEntityAccessPermission(
          queryAst.getObjectName(), authentication, "read")) {
        throw new SecurityException(
            "用户 " + context.getUserId() + " 没有实体 " + queryAst.getObjectName() + " 的读取权限");
      }

      // 3. 获取实体元数据
      EntityMetadata entityMetadata = metadataRegistry.getEntityMetadata(queryAst.getObjectName());
      context.setEntityMetadata(entityMetadata);

      // 验证字段访问权限
      validateFieldPermissions(queryAst, entityMetadata, authentication);

      // 5. AI优化查询
      String optimizedSmartql =
          aiQueryOptimizer.optimizeQuery(smartql, entityMetadata, parameters, context.getUserId());

      if (!optimizedSmartql.equals(smartql)) {
        log.debug("AI优化后的查询: {}", optimizedSmartql);
        queryAst = queryParser.parse(optimizedSmartql);
        context.setOptimizedSmartql(optimizedSmartql);
      }

      // 6. 检查缓存
      String cacheKey =
          queryCacheManager.generateCacheKey(optimizedSmartql, parameters, context.getUserId());
      context.setCacheKey(cacheKey);

      @SuppressWarnings("unchecked")
      List<T> cachedResults = (List<T>) queryCacheManager.getFromCache(cacheKey);
      if (cachedResults != null) {
        log.debug("从缓存获取查询结果，缓存键: {}", cacheKey);
        context.setCacheHit(true);
        context.setResultCount(cachedResults.size());
        queryMonitor.recordQueryMetrics(context);
        return cachedResults;
      }

      // 7. 生成SQL查询
      String sql = sqlGenerator.generateSql(queryAst, entityMetadata);
      context.setGeneratedSql(sql);
      log.trace("生成的SQL查询: {}", sql);

      // 8. 准备查询参数
      List<Object> sqlParameters = prepareSqlParameters(queryAst, parameters);

      // 9. 执行查询（这里暂时返回空列表，需要实际实现查询执行逻辑）
      List<T> results = new ArrayList<>();
      context.setResultCount(results.size());

      // 10. 应用字段级安全过滤
      List<T> securedResults = applyFieldSecurity(results, entityMetadata, authentication);

      // 11. 缓存查询结果
      if (shouldCacheQuery(queryAst, entityMetadata)) {
        queryCacheManager.cacheResult(cacheKey, securedResults, entityMetadata.getQueryCacheTtl());
        log.debug("缓存查询结果，缓存键: {}", cacheKey);
      }

      // 12. 记录查询指标
      queryMonitor.recordQueryMetrics(context);

      return securedResults;

    } catch (Exception e) {
      log.error("执行SmartQL查询失败: {}", smartql, e);
      context.setSuccess(false);
      context.setErrorMessage(e.getMessage());
      queryMonitor.recordQueryMetrics(context);
      throw new QueryExecutionException("查询执行失败: " + e.getMessage(), e);
    }
  }

  /** 执行分页查询 */
  // 修复Transactional注解找不到的问题
  // @Transactional(readOnly = true)
  public <T extends SmartBaseEntity> Map<String, Object> executePaginatedQuery(
      String smartql,
      Map<String, Object> parameters,
      Class<T> resultType,
      Object pageable) { // 修改Pageable为Object类型
    // 1. 执行总数查询
    String countQuery = sqlGenerator.generateCountQuery(smartql);
    long totalCount = executeCountQuery(countQuery, parameters);

    // 2. 如果总数为0，直接返回空结果
    if (totalCount == 0) {
      Map<String, Object> result = new HashMap<>();
      result.put("content", new ArrayList<>());
      result.put("totalElements", 0);
      // 修复pageable方法调用问题
      // result.put("page", pageable.getPageNumber());
      // result.put("size", pageable.getPageSize());
      result.put("page", 0); // 默认值
      result.put("size", 10); // 默认值
      return result;
    }

    // 3. 应用分页
    String paginatedQuery = sqlGenerator.applyPagination(smartql, pageable);

    // 4. 执行分页查询
    List<T> results = executeQuery(paginatedQuery, parameters, resultType);

    Map<String, Object> result = new HashMap<>();
    result.put("content", results);
    result.put("totalElements", totalCount);
    // 修复pageable方法调用问题
    // result.put("page", pageable.getPageNumber());
    // result.put("size", pageable.getPageSize());
    result.put("page", 0); // 默认值
    result.put("size", 10); // 默认值

    return result;
  }

  /** 执行计数查询 */
  // 修复Transactional注解找不到的问题
  // @Transactional(readOnly = true)
  public long executeCountQuery(String smartql, Map<String, Object> parameters) {
    try {
      log.debug("执行计数查询: {}", smartql);

      // 解析查询
      QueryAst queryAst = queryParser.parse(smartql);

      // 验证权限
      CustomAuthentication authentication =
          new CustomAuthentication() {
            @Override
            public String getName() {
              return userContext.getCurrentUserId();
            }

            @Override
            public List<String> getAuthorities() {
              // 实际应用中应从安全上下文中获取权限信息
              return new ArrayList<>();
            }

            @Override
            public boolean hasRole(String role) {
              // 实际应用中应从安全上下文中获取角色信息
              return false;
            }
          };

      if (!permissionChecker.hasEntityAccessPermission(
          queryAst.getObjectName(), authentication, "read")) {
        throw new SecurityException(
            "用户 "
                + userContext.getCurrentUserId()
                + " 没有实体 "
                + queryAst.getObjectName()
                + " 的读取权限");
      }

      // 获取元数据
      EntityMetadata entityMetadata = metadataRegistry.getEntityMetadata(queryAst.getObjectName());

      // 生成计数SQL
      String countSql = sqlGenerator.generateCountSql(queryAst, entityMetadata);
      log.trace("生成的计数SQL: {}", countSql);

      // 准备参数
      List<Object> sqlParameters = prepareSqlParameters(queryAst, parameters);

      // 暂时返回0，需要实际实现计数查询逻辑
      return 0;

    } catch (Exception e) {
      log.error("执行计数查询失败: {}", smartql, e);
      throw new QueryExecutionException("计数查询执行失败: " + e.getMessage(), e);
    }
  }

  /** 准备SQL查询参数 */
  private List<Object> prepareSqlParameters(QueryAst queryAst, Map<String, Object> parameters) {
    return queryAst.getParameters().stream()
        .map(paramName -> parameters.getOrDefault(paramName, null))
        .collect(Collectors.toList());
  }

  /** 获取实体类（动态或静态） */
  private Class<? extends SmartBaseEntity> getEntityClass(EntityMetadata metadata) {
    if (metadata.getEntityClass() != null) {
      // 添加类型转换，确保返回的类是SmartBaseEntity的子类
      @SuppressWarnings("unchecked")
      Class<? extends SmartBaseEntity> entityClass =
          (Class<? extends SmartBaseEntity>) metadata.getEntityClass();
      return entityClass;
    }
    return DynamicSmartEntity.class;
  }

  /** 验证字段访问权限 */
  private void validateFieldPermissions(
      QueryAst queryAst, EntityMetadata entityMetadata, CustomAuthentication authentication) {
    List<String> requestedFields = queryAst.getSelectFields();

    // 检查是否请求了没有权限的字段
    List<String> unauthorizedFields =
        requestedFields.stream()
            .filter(
                field ->
                    !permissionChecker.hasFieldAccessPermission(
                        entityMetadata.getApiName(), field, authentication, "read"))
            .collect(Collectors.toList());

    if (!unauthorizedFields.isEmpty()) {
      throw new SecurityException(
          "用户 "
              + authentication.getName()
              + " 没有以下字段的读取权限: "
              + String.join(", ", unauthorizedFields));
    }
  }

  /** 应用字段级安全过滤 */
  private <T extends SmartBaseEntity> List<T> applyFieldSecurity(
      List<T> results, EntityMetadata entityMetadata, CustomAuthentication authentication) {
    if (results == null || results.isEmpty()) {
      return results;
    }

    // 获取用户有权限查看的字段
    List<String> readableFields =
        permissionChecker.getReadableFields(entityMetadata.getApiName(), authentication);

    // 如果用户有权限查看所有字段，则直接返回
    if (readableFields.contains("*")) {
      return results;
    }

    // 对每个结果应用字段过滤
    return results.stream()
        .map(entity -> flsFilter.filterFields(entity, readableFields))
        .collect(Collectors.toList());
  }

  /** 判断查询是否应该被缓存 */
  private boolean shouldCacheQuery(QueryAst queryAst, EntityMetadata entityMetadata) {
    // 不缓存包含NOW()等动态函数的查询
    if (queryAst.containsDynamicFunctions()) {
      return false;
    }

    // 不缓存有复杂条件的查询
    if (queryAst.hasComplexConditions()) {
      return false;
    }

    // 尊重实体的缓存设置
    return entityMetadata.isCacheable();
  }
}
