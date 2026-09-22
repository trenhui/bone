package com.bone.iam.infrastructure.gateway;

import com.bone.iam.domain.gateway.AccountAuthorityCache;
import com.bone.iam.domain.model.account.AccountRole;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 账号权限码 Redis 缓存（[Target] 30min TTL）。无 Redis 时退化为直查 DB。 */
@Service
public class AccountAuthorityCacheGatewayAdapter implements AccountAuthorityCache {

  static final String KEY_PREFIX = "iam:authz:scopes:account:";
  static final String EMPTY_MARKER = "__EMPTY__";
  static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

  private final StringRedisTemplate redisTemplate;

  public AccountAuthorityCacheGatewayAdapter(
      @Autowired(required = false) StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public Optional<List<String>> get(Long accountId) {
    if (redisTemplate == null || accountId == null) {
      return Optional.empty();
    }
    String raw = redisTemplate.opsForValue().get(KEY_PREFIX + accountId);
    if (raw == null) {
      return Optional.empty();
    }
    if (EMPTY_MARKER.equals(raw)) {
      return Optional.of(List.of());
    }
    return Optional.of(Arrays.stream(raw.split(",")).filter(s -> !s.isBlank()).toList());
  }

  @Override
  public void put(Long accountId, List<String> scopes) {
    if (redisTemplate == null || accountId == null) {
      return;
    }
    List<String> safe = scopes != null ? scopes : List.of();
    String value = safe.isEmpty() ? EMPTY_MARKER : String.join(",", safe);
    redisTemplate.opsForValue().set(KEY_PREFIX + accountId, value, DEFAULT_TTL);
  }

  @Override
  public void evictAccount(Long accountId) {
    if (redisTemplate == null || accountId == null) {
      return;
    }
    redisTemplate.delete(KEY_PREFIX + accountId);
  }

  /** 角色权限或账号-角色变更后，失效持有该角色的所有账号缓存。 */
  @Override
  public void evictAccountsForRole(Long roleId) {
    if (redisTemplate == null || roleId == null) {
      return;
    }
    List<Long> accountIds =
        QueryBuilder.from(AccountRole.class)
            .where(AccountRole::getRoleId)
            .eq(roleId)
            .list()
            .stream()
            .map(AccountRole::getAccountId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    accountIds.forEach(this::evictAccount);
  }
}
