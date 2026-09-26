package com.bone.system.infrastructure.dict;

import com.bone.system.domain.model.dict.SysDictItem;
import com.bone.system.domain.service.DictOptionsCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 字典下拉数据源的进程内缓存。
 *
 * <p><b>为什么不用 Caffeine / Spring Cache</b>：字典量级在千级、失效是「整个类型」粒度， 一个 {@code ConcurrentHashMap}
 * 足够；为一个支撑域引入缓存框架与 {@code @EnableCaching} 配置（依赖变更属 L3 审批项）换不来对应的收益。等出现多实例一致性问题时再上 Redis 失效广播。
 */
@Component
public class InMemoryDictOptionsCache implements DictOptionsCache {

  private static final String ROOT_PARENT = "\u0000ROOT";
  private static final String NO_LANGUAGE = "\u0000DEFAULT";
  private static final int MAX_ENTRIES = 2_000;

  private final Map<String, List<SysDictItem>> store = new ConcurrentHashMap<>();

  @Override
  public Optional<List<SysDictItem>> get(
      long tenantId, String typeCode, String parentCode, String language) {
    return Optional.ofNullable(store.get(key(tenantId, typeCode, parentCode, language)));
  }

  @Override
  public void put(
      long tenantId, String typeCode, String parentCode, String language, List<SysDictItem> items) {
    if (store.size() >= MAX_ENTRIES) {
      // 兜底：缓存条目异常膨胀时整体清空，宁可多查一次库也不让字典缓存吃住堆内存。
      store.clear();
    }
    store.put(key(tenantId, typeCode, parentCode, language), List.copyOf(items));
  }

  @Override
  public void evictType(String typeCode) {
    List<String> stale = new ArrayList<>();
    for (String key : store.keySet()) {
      if (key.contains("|" + typeCode + "|")) {
        stale.add(key);
      }
    }
    stale.forEach(store::remove);
  }

  @Override
  public void clear() {
    store.clear();
  }

  private static String key(long tenantId, String typeCode, String parentCode, String language) {
    return tenantId
        + "|"
        + typeCode
        + "|"
        + (parentCode == null ? ROOT_PARENT : parentCode)
        + "|"
        + (language == null || language.isBlank() ? NO_LANGUAGE : language);
  }
}
