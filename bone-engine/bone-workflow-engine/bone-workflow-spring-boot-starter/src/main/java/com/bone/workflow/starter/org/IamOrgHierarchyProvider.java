package com.bone.workflow.starter.org;

import com.bone.workflow.starter.spi.OrgHierarchyProvider;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 把 IAM 组织目录接到审批引擎的 {@link OrgHierarchyProvider} 上。
 *
 * <p><b>本类只做三件事</b>：缓存、向上 N 级遍历、把一切失败收敛成 {@code empty}。取数本身交给 {@link OrgHierarchyQuery}（便于单测替换成
 * lambda）。
 *
 * <p><b>为什么失败必须收敛成 empty 而不是抛出去</b>：引擎对 {@code empty} 的处理是<b>挂起实例 + 通知管理员</b>，
 * 这是安全的一侧；若让异常穿透，单据发起会直接 500，用户既看不到「为什么不能提交」也无从修复。两者都不放行， 但前者可运维、可恢复。
 *
 * <p><b>为什么向上 N 级要防环</b>：{@code A→B→A} 这种脏数据在组织调整期间真实存在（改错主管）。没有 visited 集合时 {@code LEVEL_UP
 * level=5} 会在 A/B 之间来回跳并「成功」返回一个看似合理的人——审到错的人比挂起更危险。
 */
public class IamOrgHierarchyProvider implements OrgHierarchyProvider {

  private static final Logger log = LoggerFactory.getLogger(IamOrgHierarchyProvider.class);

  private final OrgHierarchyQuery query;
  private final long cacheTtlMillis;
  private final int maxLevels;

  private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

  public IamOrgHierarchyProvider(OrgHierarchyQuery query, long cacheTtlSeconds, int maxLevels) {
    this.query = query;
    this.cacheTtlMillis = Math.max(0L, cacheTtlSeconds) * 1000L;
    this.maxLevels = Math.max(1, maxLevels);
  }

  @Override
  public Optional<String> lineManagerOf(String accountId) {
    return lookup(accountId).flatMap(OrgHierarchy::lineManager);
  }

  @Override
  public Optional<String> deptHeadOf(String accountId) {
    return lookup(accountId).flatMap(OrgHierarchy::deptHead);
  }

  @Override
  public Optional<String> levelUpOf(String accountId, int levels) {
    if (accountId == null || accountId.isBlank() || levels < 1) {
      return Optional.empty();
    }
    if (levels > maxLevels) {
      // 不静默按 maxLevels 截断：定义里写了 50 级却只走到 10 级，返回一个「够用」的人比返回空更危险。
      log.warn(
          "[workflow] LEVEL_UP 请求级数 {} 超过上限 {}，按失败关闭处理: accountId={}",
          levels,
          maxLevels,
          accountId);
      return Optional.empty();
    }
    Set<String> visited = new LinkedHashSet<>();
    visited.add(accountId);
    String current = accountId;
    for (int i = 0; i < levels; i++) {
      Optional<String> next = lineManagerOf(current);
      if (next.isEmpty()) {
        // 链在某级断了（到顶层，或中间某人没设主管）：如实返回空，由引擎挂起。
        return Optional.empty();
      }
      current = next.get();
      if (!visited.add(current)) {
        log.warn("[workflow] 汇报链成环，已中止向上遍历: accountId={}, 环止于={}", accountId, current);
        return Optional.empty();
      }
    }
    return Optional.of(current);
  }

  /** 取组织位置（带 TTL 缓存）。查询失败→empty+告警；查不到该账户→empty（不缓存失败，下次仍会重试）。 */
  private Optional<OrgHierarchy> lookup(String accountId) {
    if (accountId == null || accountId.isBlank()) {
      return Optional.empty();
    }
    if (cacheTtlMillis > 0) {
      CacheEntry cached = cache.get(accountId);
      if (cached != null && !cached.isExpired(cacheTtlMillis)) {
        return cached.value();
      }
    }
    try {
      Optional<OrgHierarchy> resolved = query.byAccountId(accountId);
      cache.put(accountId, new CacheEntry(resolved, System.currentTimeMillis()));
      return resolved;
    } catch (RuntimeException e) {
      // 失败不入缓存：否则一次 IAM 抖动会让该账户在整个 TTL 内都解析不出人，单据白白挂起。
      log.warn("[workflow] 组织层级查询失败，按失败关闭处理（实例将挂起）: accountId={}", accountId, e);
      return Optional.empty();
    }
  }

  private record CacheEntry(Optional<OrgHierarchy> value, long atMillis) {
    boolean isExpired(long ttlMillis) {
      return System.currentTimeMillis() - atMillis >= ttlMillis;
    }
  }
}
