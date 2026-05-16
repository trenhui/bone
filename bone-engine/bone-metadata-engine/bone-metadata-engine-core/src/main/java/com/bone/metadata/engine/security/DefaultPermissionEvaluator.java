package com.bone.metadata.engine.security;

import com.bone.metadata.engine.model.EntityMetadata;
import com.bone.metadata.engine.model.FieldMetadata;
import java.util.*;
import java.util.stream.Collectors;

/** 默认权限评估器实现 提供实体、字段和记录级别的权限评估功能 */
public class DefaultPermissionEvaluator implements PermissionEvaluator {

  // 模拟实体元数据缓存
  private final Map<String, EntityMetadata> entityMetadataCache = new HashMap<>();

  // 模拟脱敏规则缓存
  private final Map<String, List<DataMaskingRule>> maskingRulesCache = new HashMap<>();

  public DefaultPermissionEvaluator() {
    // 初始化一些默认的脱敏规则
    initDefaultMaskingRules();
  }

  @Override
  public boolean hasOperationPermission(CustomAuthentication authentication, String operation) {
    // 管理员拥有所有操作权限
    if (hasRole(authentication, "ADMIN")) {
      return true;
    }

    // 简化实现：根据操作名称判断权限
    // 实际项目中应该从权限配置中获取
    if (operation.endsWith("Query") || operation.endsWith("Get") || operation.endsWith("Find")) {
      return hasRole(authentication, "VIEWER") || hasRole(authentication, "EDITOR");
    }

    if (operation.endsWith("Create")
        || operation.endsWith("Update")
        || operation.endsWith("Delete")) {
      return hasRole(authentication, "EDITOR");
    }

    // 默认不允许
    return false;
  }

  @Override
  public boolean hasPermission(
      CustomAuthentication authentication, String entityName, String operation) {
    // 管理员拥有所有权限
    if (hasRole(authentication, "ADMIN")) {
      return true;
    }

    // 简化实现：根据实体名称和操作类型判断权限
    // 实际项目中应该从权限配置中获取
    String permission = entityName + ":" + operation;

    // 检查用户是否有对应的权限
    return authentication.getAuthorities().stream()
        .anyMatch(
            auth ->
                auth.equals(permission)
                    || auth.equals("ROLE_" + permission)
                    || auth.equals("ALL_" + operation));
  }

  @Override
  public boolean hasRecordAccessPermission(
      CustomAuthentication authentication, String entityName, Object recordId) {
    // 管理员可以访问所有记录
    if (hasRole(authentication, "ADMIN")) {
      return true;
    }

    // 简化实现：这里应该根据实际的记录级权限控制逻辑
    // 例如根据记录所有者、部门等信息判断权限

    // 假设普通用户只能访问自己创建的记录
    // 实际项目中应该查询记录的所有者信息并与当前用户比较

    // 暂时返回true，实际项目中应该实现具体的记录级权限控制
    return true;
  }

  @Override
  public EntityMetadata getEntityMetadata(String entityName) {
    // 从缓存中获取实体元数据
    return entityMetadataCache.get(entityName);
  }

  @Override
  public List<DataMaskingRule> getDataMaskingRules(String entityName, String fieldName) {
    String key = entityName + "." + fieldName;
    return maskingRulesCache.getOrDefault(key, Collections.emptyList());
  }

  @Override
  public List<String> getAccessibleFields(
      CustomAuthentication authentication, String entityName, String operation) {
    EntityMetadata metadata = getEntityMetadata(entityName);
    if (metadata == null) {
      return Collections.emptyList();
    }

    // 管理员可以访问所有字段
    if (hasRole(authentication, "ADMIN")) {
      return metadata.getFields().values().stream()
          .map(FieldMetadata::getApiName)
          .collect(Collectors.toList());
    }

    // 获取用户角色
    List<String> userRoles =
        authentication.getAuthorities().stream()
            .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
            .collect(Collectors.toList());

    // 根据操作类型和用户角色过滤字段
    return metadata.getFields().values().stream()
        .filter(
            field -> {
              if (field.getPermissionMetadata() == null) {
                return true; // 没有配置权限的字段默认允许访问
              }

              if ("READ".equals(operation)) {
                return field.getPermissionMetadata().getReadableRoles().stream()
                    .anyMatch(userRoles::contains);
              } else if ("WRITE".equals(operation) || "UPDATE".equals(operation)) {
                return field.getPermissionMetadata().getEditableRoles().stream()
                    .anyMatch(userRoles::contains);
              }

              return false;
            })
        .map(FieldMetadata::getApiName)
        .collect(Collectors.toList());
  }

  @Override
  public boolean hasFieldPermission(
      CustomAuthentication authentication, String entityName, String fieldName, String operation) {
    EntityMetadata metadata = getEntityMetadata(entityName);
    if (metadata == null) {
      return false;
    }

    FieldMetadata field = metadata.getField(fieldName);
    if (field == null) {
      return false;
    }

    // 管理员可以访问所有字段
    if (hasRole(authentication, "ADMIN")) {
      return true;
    }

    if (field.getPermissionMetadata() == null) {
      // 如果字段没有配置权限，则继承实体权限
      return hasPermission(authentication, entityName, operation);
    }

    // 获取用户角色
    List<String> userRoles =
        authentication.getAuthorities().stream()
            .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
            .collect(Collectors.toList());

    // 根据操作类型检查权限
    if ("READ".equals(operation)) {
      return field.getPermissionMetadata().getReadableRoles().stream()
          .anyMatch(userRoles::contains);
    } else if ("WRITE".equals(operation) || "UPDATE".equals(operation)) {
      return field.getPermissionMetadata().getEditableRoles().stream()
          .anyMatch(userRoles::contains);
    }

    return false;
  }

  @Override
  public List<String> getUserOperations(CustomAuthentication authentication, String operationType) {
    // 简化实现：根据用户角色返回可执行的操作列表
    List<String> operations = new ArrayList<>();

    if (hasRole(authentication, "ADMIN")) {
      // 管理员可以执行所有操作
      operations.add("purchaseOrderCreate");
      operations.add("purchaseOrderUpdate");
      operations.add("purchaseOrderDelete");
      operations.add("purchaseOrderQuery");
      operations.add("purchaseOrderApprove");
      operations.add("purchaseOrderReject");
    } else if (hasRole(authentication, "EDITOR")) {
      operations.add("purchaseOrderCreate");
      operations.add("purchaseOrderUpdate");
      operations.add("purchaseOrderQuery");
    } else if (hasRole(authentication, "VIEWER")) {
      operations.add("purchaseOrderQuery");
    }

    return operations;
  }

  @Override
  public List<Object> validateBatchOperation(
      CustomAuthentication authentication,
      String entityName,
      List<Object> recordIds,
      String operation) {
    // 验证用户是否有操作权限
    if (!hasOperationPermission(authentication, operation)) {
      // 如果没有操作权限，所有记录都没有权限
      return new ArrayList<>(recordIds);
    }

    // 验证每条记录的访问权限
    List<Object> unauthorizedIds = new ArrayList<>();
    for (Object recordId : recordIds) {
      if (!hasRecordAccessPermission(authentication, entityName, recordId)) {
        unauthorizedIds.add(recordId);
      }
    }

    return unauthorizedIds;
  }

  /** 检查用户是否有指定角色 */
  private boolean hasRole(CustomAuthentication authentication, String role) {
    return authentication.hasRole(role)
        || authentication.getAuthorities().contains(role)
        || authentication.getAuthorities().contains("ROLE_" + role);
  }

  /** 初始化默认的脱敏规则 */
  private void initDefaultMaskingRules() {
    // 手机号脱敏规则
    DataMaskingRule phoneRule = new DataMaskingRule();
    phoneRule.setFieldName("phoneNumber");
    phoneRule.setType("PHONE");
    phoneRule.setPattern("(\\d{3})\\d{4}(\\d{4})");
    phoneRule.setReplacement("$1****$2");
    List<String> phoneExceptions = new ArrayList<>();
    phoneExceptions.add("ADMIN");
    phoneExceptions.add("HR_ADMIN");
    phoneRule.setExceptions(phoneExceptions);

    // 身份证号脱敏规则
    DataMaskingRule idCardRule = new DataMaskingRule();
    idCardRule.setFieldName("idCardNumber");
    idCardRule.setType("ID_CARD");
    idCardRule.setPattern("(\\d{6})\\d{8}(\\d{4})");
    idCardRule.setReplacement("$1********$2");
    List<String> idCardExceptions = new ArrayList<>();
    idCardExceptions.add("ADMIN");
    idCardRule.setExceptions(idCardExceptions);

    // 银行卡号脱敏规则
    DataMaskingRule bankCardRule = new DataMaskingRule();
    bankCardRule.setFieldName("bankCardNumber");
    bankCardRule.setType("BANK_CARD");
    bankCardRule.setPattern("(\\d{4})\\d{8,12}(\\d{4})");
    bankCardRule.setReplacement("$1********$2");
    List<String> bankCardExceptions = new ArrayList<>();
    bankCardExceptions.add("ADMIN");
    bankCardExceptions.add("FINANCE_ADMIN");
    bankCardRule.setExceptions(bankCardExceptions);

    // 添加到缓存
    List<DataMaskingRule> contactRules = new ArrayList<>();
    contactRules.add(phoneRule);
    maskingRulesCache.put("contact.phoneNumber", contactRules);

    List<DataMaskingRule> userRules = new ArrayList<>();
    userRules.add(phoneRule);
    userRules.add(idCardRule);
    maskingRulesCache.put("user.phoneNumber", userRules);
    maskingRulesCache.put("user.idCardNumber", userRules);

    List<DataMaskingRule> paymentRules = new ArrayList<>();
    paymentRules.add(bankCardRule);
    maskingRulesCache.put("payment.bankCardNumber", paymentRules);
  }
}
