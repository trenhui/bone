package com.bone.iam.domain.gateway;

import java.util.List;

/** 访问令牌签发端口（JWT 等由 infrastructure 实现）。 */
public interface AccessTokenIssuer {

    String issueAccessToken(Long accountId, String username, Long tenantId, List<String> scopes);
}
