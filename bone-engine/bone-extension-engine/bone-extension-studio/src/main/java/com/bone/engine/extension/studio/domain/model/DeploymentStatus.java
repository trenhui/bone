package com.bone.engine.extension.studio.domain.model;

/**
 * 制品部署状态机（详设 v2.5 §3.3 [Target]）。
 *
 * <p>当前持久化仍以 {@code is_active} + 版本表为主；本枚举供 API/OpenAPI 演进与后续 DDL {@code deployment_status} 对齐。
 */
public enum DeploymentStatus {
    UPLOADED,
    VALIDATED,
    REJECTED,
    STAGED,
    ACTIVE,
    DEPRECATED
}
