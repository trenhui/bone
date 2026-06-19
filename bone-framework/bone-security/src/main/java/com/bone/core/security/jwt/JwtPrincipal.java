package com.bone.core.security.jwt;

import java.util.List;

/** JWT 主体信息，从 token claims 解析得到。各模块共用此模型。 */
public record JwtPrincipal(String userId, String username, String tenantId, List<String> scopes) {}
