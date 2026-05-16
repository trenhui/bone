package com.bone.metadata.engine.security;

import com.bone.metadata.engine.model.EntityMetadata;
import java.util.List;

/** 权限评估器接口，负责评估用户对实体、字段和记录的访问权限 根据设计文档中的数据安全配置需求设计 */
public interface PermissionEvaluator {

  /**
   * 检查用户是否有操作权限
   *
   * @param authentication 用户认证信息
   * @param operation 操作名称
   * @return 是否有权限
   */
  boolean hasOperationPermission(CustomAuthentication authentication, String operation);

  /**
   * 检查用户是否有实体权限
   *
   * @param authentication 用户认证信息
   * @param entityName 实体名称
   * @param operation 操作类型（READ/WRITE/DELETE等）
   * @return 是否有权限
   */
  boolean hasPermission(CustomAuthentication authentication, String entityName, String operation);

  /**
   * 检查用户是否有记录访问权限
   *
   * @param authentication 用户认证信息
   * @param entityName 实体名称
   * @param recordId 记录ID
   * @return 是否有权限
   */
  boolean hasRecordAccessPermission(
      CustomAuthentication authentication, String entityName, Object recordId);

  /**
   * 获取实体元数据
   *
   * @param entityName 实体名称
   * @return 实体元数据
   */
  EntityMetadata getEntityMetadata(String entityName);

  /**
   * 获取数据脱敏规则
   *
   * @param entityName 实体名称
   * @param fieldName 字段名称
   * @return 脱敏规则列表
   */
  List<DataMaskingRule> getDataMaskingRules(String entityName, String fieldName);

  /**
   * 获取用户有权访问的字段列表
   *
   * @param authentication 用户认证信息
   * @param entityName 实体名称
   * @param operation 操作类型
   * @return 有权访问的字段列表
   */
  List<String> getAccessibleFields(
      CustomAuthentication authentication, String entityName, String operation);

  /**
   * 检查用户是否有字段权限
   *
   * @param authentication 用户认证信息
   * @param entityName 实体名称
   * @param fieldName 字段名称
   * @param operation 操作类型
   * @return 是否有权限
   */
  boolean hasFieldPermission(
      CustomAuthentication authentication, String entityName, String fieldName, String operation);

  /**
   * 获取用户在指定操作类型下可以执行的操作列表
   *
   * @param authentication 用户认证信息
   * @param operationType 操作类型
   * @return 可执行的操作列表
   */
  List<String> getUserOperations(CustomAuthentication authentication, String operationType);

  /**
   * 验证批量操作权限
   *
   * @param authentication 用户认证信息
   * @param entityName 实体名称
   * @param recordIds 记录ID列表
   * @param operation 操作类型
   * @return 没有权限的记录ID列表
   */
  List<Object> validateBatchOperation(
      CustomAuthentication authentication,
      String entityName,
      List<Object> recordIds,
      String operation);
}
