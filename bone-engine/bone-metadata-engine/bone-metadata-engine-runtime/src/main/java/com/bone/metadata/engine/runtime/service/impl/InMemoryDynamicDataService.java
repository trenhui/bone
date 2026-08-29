package com.bone.metadata.engine.runtime.service.impl;

import com.bone.metadata.engine.runtime.metadata.MetadataRegistry;
import com.bone.metadata.engine.runtime.service.DynamicDataService;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 内存实现的动态数据服务 用于开发、测试和演示环境 */
@Service("inMemoryDynamicDataService")
public class InMemoryDynamicDataService implements DynamicDataService {

  private final Map<String, Map<String, Map<String, Object>>> dataStore = new ConcurrentHashMap<>();
  private final MetadataRegistry metadataRegistry;

  @Autowired
  public InMemoryDynamicDataService(MetadataRegistry metadataRegistry) {
    this.metadataRegistry = metadataRegistry;
  }

  @Override
  public Map<String, Object> getById(String entityName, String entityId) {
    Map<String, Map<String, Object>> entityStore = getDataStore(entityName);
    if (entityStore != null) {
      Map<String, Object> entity = entityStore.get(entityId);
      return entity != null ? new HashMap<>(entity) : null;
    }
    return null;
  }

  @Override
  public Map<String, Object> create(String entityName, Map<String, Object> data) {
    Map<String, Map<String, Object>> entityStore = getDataStore(entityName);
    if (entityStore == null) {
      entityStore = new ConcurrentHashMap<>();
      dataStore.put(entityName, entityStore);
    }

    // 生成ID
    String id = (String) data.get("id");
    if (id == null || id.isEmpty()) {
      id = generateId();
      data.put("id", id);
    }

    // 添加创建时间
    data.put("createdAt", new Date());
    data.put("updatedAt", new Date());

    // 存储数据
    Map<String, Object> storedData = new HashMap<>(data);
    entityStore.put(id, storedData);

    return new HashMap<>(storedData);
  }

  @Override
  public Map<String, Object> update(String entityName, String entityId, Map<String, Object> data) {
    Map<String, Map<String, Object>> entityStore = getDataStore(entityName);
    if (entityStore != null && entityStore.containsKey(entityId)) {
      Map<String, Object> storedData = entityStore.get(entityId);

      // 更新字段（不覆盖ID和创建时间）
      data.forEach(
          (key, value) -> {
            if (!"id".equals(key) && !"createdAt".equals(key)) {
              storedData.put(key, value);
            }
          });

      // 更新修改时间
      storedData.put("updatedAt", new Date());

      return new HashMap<>(storedData);
    }
    return null;
  }

  @Override
  public boolean delete(String entityName, String entityId) {
    Map<String, Map<String, Object>> entityStore = getDataStore(entityName);
    if (entityStore != null) {
      return entityStore.remove(entityId) != null;
    }
    return false;
  }

  @Override
  public List<Map<String, Object>> query(String entityName, Map<String, Object> queryParams) {
    Map<String, Map<String, Object>> entityStore = getDataStore(entityName);
    if (entityStore == null) {
      return Collections.emptyList();
    }

    List<Map<String, Object>> results = new ArrayList<>();

    for (Map<String, Object> entity : entityStore.values()) {
      if (matchesQuery(entity, queryParams)) {
        results.add(new HashMap<>(entity));
      }
    }

    return results;
  }

  @Override
  public List<Map<String, Object>> queryByIds(String entityName, List<String> ids) {
    Map<String, Map<String, Object>> entityStore = getDataStore(entityName);
    if (entityStore == null) {
      return Collections.emptyList();
    }

    return ids.stream()
        .map(id -> entityStore.get(id))
        .filter(Objects::nonNull)
        .map(HashMap::new)
        .collect(Collectors.toList());
  }

  @Override
  public Map<String, Object> queryPage(
      String entityName, Map<String, Object> queryParams, int page, int size) {
    List<Map<String, Object>> allResults = query(entityName, queryParams);

    // 计算分页
    int total = allResults.size();
    int fromIndex = Math.max(0, (page - 1) * size);
    int toIndex = Math.min(fromIndex + size, total);

    List<Map<String, Object>> pageResults =
        fromIndex < toIndex ? allResults.subList(fromIndex, toIndex) : Collections.emptyList();

    // 构建结果
    Map<String, Object> result = new HashMap<>();
    result.put("total", total);
    result.put("page", page);
    result.put("size", size);
    result.put("pages", (int) Math.ceil((double) total / size));
    result.put("data", pageResults);

    return result;
  }

  @Override
  public List<Map<String, Object>> batchCreate(
      String entityName, List<Map<String, Object>> dataList) {
    List<Map<String, Object>> results = new ArrayList<>();
    for (Map<String, Object> data : dataList) {
      results.add(create(entityName, data));
    }
    return results;
  }

  @Override
  public List<Map<String, Object>> batchUpdate(
      String entityName, List<Map<String, Object>> dataList) {
    List<Map<String, Object>> results = new ArrayList<>();
    for (Map<String, Object> data : dataList) {
      String id = (String) data.get("id");
      if (id != null) {
        Map<String, Object> updated = update(entityName, id, data);
        if (updated != null) {
          results.add(updated);
        }
      }
    }
    return results;
  }

  @Override
  public int batchDelete(String entityName, List<String> ids) {
    int deletedCount = 0;
    for (String id : ids) {
      if (delete(entityName, id)) {
        deletedCount++;
      }
    }
    return deletedCount;
  }

  @Override
  public Object executeOperation(
      String entityName, String operationName, Map<String, Object> params) {
    // 简化实现
    Map<String, Object> result = new HashMap<>();
    result.put("entityName", entityName);
    result.put("operationName", operationName);
    result.put("params", params);
    result.put("status", "mocked");
    return result;
  }

  @Override
  public Map<String, Object> validateData(String entityName, Map<String, Object> data) {
    // 简化实现
    Map<String, Object> result = new HashMap<>();
    result.put("valid", true);
    result.put("errors", Collections.emptyList());
    return result;
  }

  @Override
  public Map<String, Object> getStatistics(String entityName, Map<String, Object> queryParams) {
    List<Map<String, Object>> results = query(entityName, queryParams);

    Map<String, Object> stats = new HashMap<>();
    stats.put("count", results.size());

    // 可以根据需要添加更多统计信息
    return stats;
  }

  /** 获取实体的数据存储 */
  private Map<String, Map<String, Object>> getDataStore(String entityName) {
    return dataStore.get(entityName);
  }

  /** 检查实体是否匹配查询条件 */
  private boolean matchesQuery(Map<String, Object> entity, Map<String, Object> queryParams) {
    if (queryParams == null || queryParams.isEmpty()) {
      return true;
    }

    for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
      String key = entry.getKey();
      Object queryValue = entry.getValue();
      Object entityValue = entity.get(key);

      // 支持等于比较
      if (!Objects.equals(entityValue, queryValue)) {
        return false;
      }
    }

    return true;
  }

  /** 生成唯一ID */
  private String generateId() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  /** 清空指定实体的数据（用于测试） */
  public void clear(String entityName) {
    Map<String, Map<String, Object>> entityStore = dataStore.get(entityName);
    if (entityStore != null) {
      entityStore.clear();
    }
  }

  /** 清空所有数据（用于测试） */
  public void clearAll() {
    dataStore.clear();
  }
}
