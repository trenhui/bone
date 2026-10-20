package com.bone.core.security.jwt;

import java.util.List;

/**
 * JWT 主体信息，从 token claims 解析得到。各模块共用此模型。
 *
 * @param scopes 权限码（资源服务器实体级授权依据）
 * @param roleCodes 角色码（身份属性，下游字段级角色裁剪依据；claim 缺失与空集合同义，均为无角色）
 */
public record JwtPrincipal(
    String userId, String username, String tenantId, List<String> scopes, List<String> roleCodes) {}
