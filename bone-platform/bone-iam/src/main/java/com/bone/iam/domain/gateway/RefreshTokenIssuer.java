package com.bone.iam.domain.gateway;

import java.util.Map;

/** 刷新令牌签发与轮换端口。 */
public interface RefreshTokenIssuer {

    String issue(Long accountId, Long tenantId);

    Map<String, String> rotate(String rawRefreshToken);

    void revoke(String rawRefreshToken);
}
