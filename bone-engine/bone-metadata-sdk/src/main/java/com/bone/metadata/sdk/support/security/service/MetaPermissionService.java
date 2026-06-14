package com.bone.metadata.sdk.support.security.service;

/** 元数据权限服务，提供基于元数据名称或 ID 的行级权限校验。 */
public interface MetaPermissionService {
  /** 判断指定用户对指定元数据名称是否拥有某种动作权限（如 "READ","WRITE"）。 */
  boolean hasPermission(String username, String metaName, String permission);

  /** 判断指定用户对指定元数据 ID 是否拥有某种动作权限。 */
  boolean hasPermission(String username, Long metaId, String permission);

  /** 判断当前登陆用户对指定元数据资源 ，是否拥有某种动作权限。 */
  boolean hasPermission(String resource, String action);

  /** 判断当前登陆用户对指定元数据资源 ，是否拥有权限。 */
  boolean hasPermission(String resource);
}
