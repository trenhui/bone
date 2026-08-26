package com.bone.metadata.engine.domain.metadata;

import java.util.List;

/** 权限元数据接口 提供权限相关的方法 */
public interface PermissionMetadata {

  /** 获取可读角色列表 */
  List<String> getReadableRoles();

  /** 获取可编辑角色列表 */
  List<String> getEditableRoles();
}
