package com.bone.metadata.engine.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

/** 权限检查器，用于检查用户对实体和字段的访问权限 */
@Component
public class PermissionChecker {

  /**
   * 检查用户是否有实体访问权限
   *
   * @param entityName 实体名称
   * @param authentication 用户认证信息
   * @param permissionType 权限类型（read, write, delete等）
   * @return 如果有权限则返回true，否则返回false
   */
  public boolean hasEntityAccessPermission(
      String entityName, CustomAuthentication authentication, String permissionType) {
    // 管理员角色拥有所有权限
    if (authentication.hasRole("ADMIN")) {
      return true;
    }

    // system用户拥有所有权限
    if ("system".equals(authentication.getName())) {
      return true;
    }

    // 默认拒绝（deny-by-default）：非管理员/非 system 用户必须显式经权限服务校验。
    // 已知限制：尚未接入 IAM 权限服务（PermissionServicePort），
    // 当前 deny-by-default 是故意的安全策略——无显式授权即拒绝。
    // 接入权限服务后，替换为：
    // return permissionService.hasPermission(authentication.getName(), entityName, permissionType);
    return false;
  }

  /**
   * 检查用户是否有字段访问权限
   *
   * @param entityName 实体名称
   * @param fieldName 字段名称
   * @param authentication 用户认证信息
   * @param permissionType 权限类型（read, write等）
   * @return 如果有权限则返回true，否则返回false
   */
  public boolean hasFieldAccessPermission(
      String entityName,
      String fieldName,
      CustomAuthentication authentication,
      String permissionType) {
    // 管理员角色拥有所有权限
    if (authentication.hasRole("ADMIN")) {
      return true;
    }

    // system用户拥有所有权限
    if ("system".equals(authentication.getName())) {
      return true;
    }

    // 默认拒绝（deny-by-default）：非管理员/非 system 用户必须显式经权限服务校验。
    // 已知限制：尚未接入 IAM 权限服务（PermissionServicePort），
    // 当前 deny-by-default 是故意的安全策略——无显式授权即拒绝。
    // 接入权限服务后，替换为：
    // return permissionService.hasFieldPermission(authentication.getName(), entityName,
    // fieldName, permissionType);
    return false;
  }

  /**
   * 获取用户可读的字段列表
   *
   * @param entityName 实体名称
   * @param authentication 用户认证信息
   * @return 用户可读的字段列表
   */
  public List<String> getReadableFields(String entityName, CustomAuthentication authentication) {
    // 管理员角色和system用户可以读取所有字段（返回*）
    if (authentication.hasRole("ADMIN") || "system".equals(authentication.getName())) {
      return Arrays.asList("*");
    }

    // 默认拒绝（deny-by-default）：非管理员/非 system 用户无显式授权时返回空字段集。
    // 已知限制：尚未接入 IAM 权限服务（PermissionServicePort），
    // 当前返回空列表是故意的安全策略——无显式授权即不可读。
    // 接入权限服务后，替换为：
    // return permissionService.getReadableFields(authentication.getName(), entityName);
    return Collections.emptyList();
  }

  /**
   * 检查用户是否有字段访问权限（适配String userId参数的方法）
   *
   * @param entityName 实体名称
   * @param fieldName 字段名称
   * @param userId 用户ID
   * @param permissionType 权限类型（read, write等）
   * @return 如果有权限则返回true，否则返回false
   */
  public boolean hasFieldAccessPermission(
      String entityName, String fieldName, String userId, String permissionType) {
    // 创建临时的CustomAuthentication实现
    CustomAuthentication tempAuth =
        new CustomAuthentication() {
          @Override
          public String getName() {
            return userId;
          }

          @Override
          public List<String> getAuthorities() {
            // 简单实现，根据用户ID判断是否有管理员权限
            if ("admin".equals(userId)) {
              return Arrays.asList("ADMIN");
            }
            return Arrays.asList();
          }

          @Override
          public boolean hasRole(String role) {
            return getAuthorities().contains(role);
          }
        };

    // 调用主方法进行权限检查
    return hasFieldAccessPermission(entityName, fieldName, tempAuth, permissionType);
  }

  /**
   * 获取用户可读的字段列表（适配String userId参数的方法）
   *
   * @param entityName 实体名称
   * @param userId 用户ID
   * @return 用户可读的字段列表
   */
  public List<String> getReadableFields(String entityName, String userId) {
    // 创建临时的CustomAuthentication实现
    CustomAuthentication tempAuth =
        new CustomAuthentication() {
          @Override
          public String getName() {
            return userId;
          }

          @Override
          public List<String> getAuthorities() {
            // 简单实现，根据用户ID判断是否有管理员权限
            if ("admin".equals(userId)) {
              return Arrays.asList("ADMIN");
            }
            return Arrays.asList();
          }

          @Override
          public boolean hasRole(String role) {
            return getAuthorities().contains(role);
          }
        };

    // 调用主方法获取可读字段列表
    return getReadableFields(entityName, tempAuth);
  }
}
