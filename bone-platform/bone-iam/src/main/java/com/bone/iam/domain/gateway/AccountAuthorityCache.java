package com.bone.iam.domain.gateway;

import java.util.List;
import java.util.Optional;

/** 账号权限码缓存端口（Redis 等由 infrastructure 实现）。 */
public interface AccountAuthorityCache {

  Optional<List<String>> get(Long accountId);

  void put(Long accountId, List<String> scopes);

  void evictAccount(Long accountId);

  void evictAccountsForRole(Long roleId);
}
