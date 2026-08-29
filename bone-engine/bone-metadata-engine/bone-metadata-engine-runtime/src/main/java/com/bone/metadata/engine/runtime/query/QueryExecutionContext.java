package com.bone.metadata.engine.runtime.query;

import com.bone.metadata.engine.domain.metadata.EntityMetadata;
import com.bone.metadata.engine.runtime.query.ast.QueryAst;

/** 查询执行上下文，用于在查询执行过程中传递和存储相关信息 */
public class QueryExecutionContext {

  private long startTime;
  private String smartql;
  private String optimizedSmartql;
  private String generatedSql;
  private String cacheKey;
  private String userId;
  private QueryAst queryAst;
  private EntityMetadata entityMetadata;
  private Object resultType;
  private Object parameters;
  private int resultCount;
  private boolean cacheHit;
  private boolean success;
  private String errorMessage;

  /** 获取查询开始时间 */
  public long getStartTime() {
    return startTime;
  }

  public void setStartTime(long startTime) {
    this.startTime = startTime;
  }

  /** 获取原始SmartQL查询字符串 */
  public String getSmartql() {
    return smartql;
  }

  public void setSmartql(String smartql) {
    this.smartql = smartql;
  }

  /** 获取优化后的SmartQL查询字符串 */
  public String getOptimizedSmartql() {
    return optimizedSmartql;
  }

  public void setOptimizedSmartql(String optimizedSmartql) {
    this.optimizedSmartql = optimizedSmartql;
  }

  /** 获取生成的SQL查询字符串 */
  public String getGeneratedSql() {
    return generatedSql;
  }

  public void setGeneratedSql(String generatedSql) {
    this.generatedSql = generatedSql;
  }

  /** 获取缓存键 */
  public String getCacheKey() {
    return cacheKey;
  }

  public void setCacheKey(String cacheKey) {
    this.cacheKey = cacheKey;
  }

  /** 获取用户ID */
  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  /** 获取查询抽象语法树 */
  public QueryAst getQueryAst() {
    return queryAst;
  }

  public void setQueryAst(QueryAst queryAst) {
    this.queryAst = queryAst;
  }

  /** 获取实体元数据 */
  public EntityMetadata getEntityMetadata() {
    return entityMetadata;
  }

  public void setEntityMetadata(EntityMetadata entityMetadata) {
    this.entityMetadata = entityMetadata;
  }

  /** 获取结果类型 */
  public Object getResultType() {
    return resultType;
  }

  public void setResultType(Object resultType) {
    this.resultType = resultType;
  }

  /** 获取查询参数 */
  public Object getParameters() {
    return parameters;
  }

  public void setParameters(Object parameters) {
    this.parameters = parameters;
  }

  /** 获取结果数量 */
  public int getResultCount() {
    return resultCount;
  }

  public void setResultCount(int resultCount) {
    this.resultCount = resultCount;
  }

  /** 检查是否命中缓存 */
  public boolean isCacheHit() {
    return cacheHit;
  }

  public void setCacheHit(boolean cacheHit) {
    this.cacheHit = cacheHit;
  }

  /** 检查查询是否成功执行 */
  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  /** 获取错误信息 */
  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }
}
