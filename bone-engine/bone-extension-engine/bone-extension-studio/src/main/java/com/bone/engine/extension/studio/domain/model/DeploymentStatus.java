package com.bone.engine.extension.studio.domain.model;

/**
 * 制品部署状态机（详设 v2.5 §3.3 [Target]）。
 *
 * <p>持久化于 {@code exts_plugin_version.deployment_status}；与 {@code is_active}、扩展实现 {@code status}
 * 协同表达路由态。
 */
public enum DeploymentStatus {
  UPLOADED,
  VALIDATED,
  REJECTED,
  STAGED,
  ACTIVE,
  DEPRECATED
}
