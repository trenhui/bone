package com.bone.metadata.engine.runtime.security;

import com.bone.metadata.engine.domain.metadata.FieldLevelSecurityMetadata;
import com.bone.metadata.engine.domain.model.EntityMetadata;
import com.bone.metadata.engine.domain.model.FieldMetadata;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;

/** 字段级安全管理器，负责处理字段级权限验证和数据脱敏 实现设计文档中描述的数据安全配置功能 */
public class FieldLevelSecurityManager {

  private final PermissionEvaluator permissionEvaluator;
  private final DataMaskingService dataMaskingService;
  private final AuditService auditService;

  @Autowired
  public FieldLevelSecurityManager(
      PermissionEvaluator permissionEvaluator,
      DataMaskingService dataMaskingService,
      AuditService auditService) {
    this.permissionEvaluator = permissionEvaluator;
    this.dataMaskingService = dataMaskingService;
    this.auditService = auditService;
  }

  /**
   * 处理数据访问（权限检查和脱敏）
   *
   * @param authentication 当前用户认证信息
   * @param entityName 实体名称
   * @param data 数据对象
   * @param operation 操作类型（READ/WRITE等）
   * @return 处理后的数据
   */
  @SuppressWarnings("unchecked")
  public <T> T processDataAccess(
      CustomAuthentication authentication, String entityName, T data, String operation) {
    if (data == null) {
      return null;
    }

    // 获取实体元数据
    EntityMetadata metadata = permissionEvaluator.getEntityMetadata(entityName);
    if (metadata == null) {
      return data;
    }

    // 针对Map类型的数据进行处理
    if (data instanceof Map) {
      Map<String, Object> dataMap = (Map<String, Object>) data;
      return (T) processMapData(authentication, metadata, dataMap, operation);
    }

    // 针对List类型的数据进行处理
    if (data instanceof List) {
      List<Object> dataList = (List<Object>) data;
      return (T)
          dataList.stream()
              .map(item -> processDataAccess(authentication, entityName, item, operation))
              .collect(Collectors.toList());
    }

    // 对于实体对象，需要使用反射进行处理，这里简化处理
    // 实际项目中应该根据实体类型进行适当的处理
    return data;
  }

  /** 处理Map类型的数据 */
  private Map<String, Object> processMapData(
      CustomAuthentication authentication,
      EntityMetadata metadata,
      Map<String, Object> dataMap,
      String operation) {
    Map<String, Object> processedData = new HashMap<>(dataMap.size());
    String username = authentication.getName();

    for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
      String fieldName = entry.getKey();
      Object value = entry.getValue();
      FieldMetadata field = metadata.getField(fieldName);

      // 如果字段不存在，直接保留原始值
      if (field == null) {
        processedData.put(fieldName, value);
        continue;
      }

      // 检查字段权限
      if (!hasFieldPermission(authentication, metadata, field, operation)) {
        // 记录访问拒绝日志
        auditService.logAccessDenied(
            authentication, "FIELD_ACCESS", metadata.getApiName() + "." + fieldName, operation);

        // 对于READ操作，应用脱敏处理；对于其他操作，跳过该字段
        if ("READ".equals(operation)) {
          processedData.put(fieldName, applyDataMasking(authentication, metadata, field, value));
        }
        continue;
      }

      // 如果有权限，检查是否需要解密（针对加密字段）
      if (field.isEncrypted() && value != null && "READ".equals(operation)) {
        value = decryptValue(field, value);
      }

      // 对于嵌套对象，递归处理
      if (value instanceof Map || value instanceof List) {
        value = processDataAccess(authentication, metadata.getApiName(), value, operation);
      }

      processedData.put(fieldName, value);
    }

    return processedData;
  }

  /** 检查用户是否有字段访问权限 */
  private boolean hasFieldPermission(
      CustomAuthentication authentication,
      EntityMetadata entity,
      FieldMetadata field,
      String operation) {
    // 管理员拥有所有权限
    if (authentication.hasRole("ADMIN")) {
      return true;
    }

    // 获取字段级安全元数据
    FieldLevelSecurityMetadata securityMetadata = field.getPermissionMetadata();
    if (securityMetadata == null) {
      // 如果没有配置字段级安全，则使用默认权限策略
      return permissionEvaluator.hasPermission(authentication, entity.getApiName(), operation);
    }

    // 获取用户角色
    List<String> userRoles = authentication.getAuthorities();

    // 检查操作类型对应的权限
    if ("READ".equals(operation)) {
      return securityMetadata.getReadableRoles().stream().anyMatch(userRoles::contains);
    } else if ("WRITE".equals(operation) || "UPDATE".equals(operation)) {
      return securityMetadata.getEditableRoles().stream().anyMatch(userRoles::contains);
    }

    // 其他操作类型默认继承实体权限
    return permissionEvaluator.hasPermission(authentication, entity.getApiName(), operation);
  }

  /** 应用数据脱敏 */
  private Object applyDataMasking(
      CustomAuthentication authentication,
      EntityMetadata entity,
      FieldMetadata field,
      Object value) {
    if (value == null) {
      return null;
    }

    // 获取脱敏规则
    List<DataMaskingRule> maskingRules =
        permissionEvaluator.getDataMaskingRules(entity.getApiName(), field.getName());
    if (maskingRules == null || maskingRules.isEmpty()) {
      // 如果没有配置脱敏规则，默认返回星号
      return "****";
    }

    // 检查用户是否在例外列表中
    List<String> userRoles = authentication.getAuthorities();

    for (DataMaskingRule rule : maskingRules) {
      // 如果用户角色在例外列表中，则不应用脱敏
      List<String> exceptions = rule.getExceptions();
      if (exceptions != null && exceptions.stream().anyMatch(userRoles::contains)) {
        return value;
      }

      // 应用脱敏规则
      return dataMaskingService.applyMasking(value, rule);
    }

    return "****";
  }

  /** 解密加密字段值 */
  private Object decryptValue(FieldMetadata field, Object encryptedValue) {
    try {
      // 实际项目中应该使用加密服务进行解密
      // 这里简化处理，实际应该调用加密服务
      String algorithm =
          field.getEncryptionAlgorithm() != null
              ? field.getEncryptionAlgorithm()
              : "AES/GCM/NoPadding";

      // 简化实现，实际应该使用加密服务
      return encryptedValue;
    } catch (Exception e) {
      // 解密失败，返回原始值
      return encryptedValue;
    }
  }

  /** 验证批量操作的权限 */
  public void validateBatchOperation(
      CustomAuthentication authentication, String operation, List<?> recordIds) {
    // 验证操作权限
    if (!permissionEvaluator.hasOperationPermission(authentication, operation)) {
      auditService.logAccessDenied(authentication, "OPERATION_ACCESS", operation, "");
      throw new AccessDeniedException("您没有权限执行此操作");
    }

    // 验证每条记录的访问权限
    String entityName = getEntityFromOperation(operation);
    for (Object recordId : recordIds) {
      validateRecordAccess(authentication, entityName, recordId);
    }
  }

  /** 验证记录访问权限 */
  private void validateRecordAccess(
      CustomAuthentication authentication, String entityName, Object recordId) {
    if (!permissionEvaluator.hasRecordAccessPermission(authentication, entityName, recordId)) {
      auditService.logAccessDenied(
          authentication, "RECORD_ACCESS", entityName, recordId.toString());
      throw new AccessDeniedException("您没有权限访问此记录");
    }
  }

  /** 从操作名称中提取实体名称 */
  private String getEntityFromOperation(String operation) {
    // 假设操作名称格式为：entityAction，例如：purchaseOrderCreate
    // 这里简化处理，实际应该有更复杂的解析逻辑
    return operation.replaceAll("([A-Z])", " $1").trim().split(" ")[0].toLowerCase();
  }

  /** 获取用户可读的字段列表 */
  public List<String> getReadableFields(CustomAuthentication authentication, String entityName) {
    EntityMetadata metadata = permissionEvaluator.getEntityMetadata(entityName);
    if (metadata == null) {
      return Collections.emptyList();
    }

    return metadata.getFields().values().stream()
        .filter(field -> hasFieldPermission(authentication, metadata, field, "READ"))
        .map(FieldMetadata::getApiName)
        .collect(Collectors.toList());
  }

  /** 访问拒绝异常 */
  public static class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
      super(message);
    }
  }

  /** 数据脱敏规则内部类 */
  // 删除内部DataMaskingRule类，使用外部的DataMaskingRule类
}
