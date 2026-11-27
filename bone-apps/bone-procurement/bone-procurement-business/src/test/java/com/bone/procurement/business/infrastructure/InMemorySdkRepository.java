package com.bone.procurement.business.infrastructure;

import com.bone.core.domain.entity.Entity;
import com.bone.core.model.PageParam;
import com.bone.core.model.PageResult;
import com.bone.core.model.Query;
import com.bone.core.model.QueryParam;
import com.bone.core.model.SortingField;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** 测试夹具：模拟 SDK 仓储的跨实例共享存储，用于重启读回；不是生产仓储实现。 */
public class InMemorySdkRepository<T extends Entity<Long>> implements Repository<T, Long> {

  private final Map<Long, T> store;
  private final AtomicLong sequence;

  public InMemorySdkRepository() {
    this(new LinkedHashMap<>(), new AtomicLong(1000));
  }

  public InMemorySdkRepository(Map<Long, T> store, AtomicLong sequence) {
    this.store = store;
    this.sequence = sequence;
  }

  @Override
  public T findById(Long id) {
    return store.get(id);
  }

  @Override
  public T findByIdIncludingDeleted(Long id) {
    return store.get(id);
  }

  @Override
  public List<T> findByIds(List<Long> ids) {
    List<T> out = new ArrayList<>();
    for (Long id : ids) {
      if (store.containsKey(id)) {
        out.add(store.get(id));
      }
    }
    return out;
  }

  @Override
  public List<T> findByIdsIncludingDeleted(List<Long> ids) {
    return findByIds(ids);
  }

  @Override
  public Long insert(T entity) {
    if (entity.getId() == null) {
      entity.setId(sequence.incrementAndGet());
    }
    store.put(entity.getId(), entity);
    return entity.getId();
  }

  @Override
  public void batchInsert(List<T> entities) {
    entities.forEach(this::insert);
  }

  @Override
  public boolean update(T entity) {
    store.put(entity.getId(), entity);
    return true;
  }

  @Override
  public int updateByCriteria(T entity, Criteria<T> criteria) {
    return update(entity) ? 1 : 0;
  }

  @Override
  public Long save(T entity) {
    return insert(entity);
  }

  @Override
  public void batchSave(List<T> entities) {
    entities.forEach(this::insert);
  }

  @Override
  public boolean deleteById(Long id) {
    return store.remove(id) != null;
  }

  @Override
  public void deleteByIds(List<Long> ids) {
    ids.forEach(store::remove);
  }

  @Override
  public int deleteByCriteria(Criteria<T> criteria) {
    int size = store.size();
    store.clear();
    return size;
  }

  @Override
  public List<T> findByCriteria(Criteria<T> criteria) {
    return new ArrayList<>(store.values());
  }

  @Override
  public T findOneByCriteria(Criteria<T> criteria) {
    return store.values().stream().findFirst().orElse(null);
  }

  @Override
  public PageResult<T> pageByCriteria(Criteria<T> criteria) {
    return PageResult.of(new ArrayList<>(store.values()), (long) store.size(), 1, 10);
  }

  @Override
  public Long countByCriteria(Criteria<T> criteria) {
    return (long) store.size();
  }

  @Override
  public PageResult<T> queryByCondition(
      List<QueryParam> queryParams,
      List<SortingField> sortingFields,
      Integer page,
      Integer size,
      String bizIdentityCode) {
    return PageResult.of(new ArrayList<>(store.values()), (long) store.size(), page, size);
  }

  @Override
  public List<T> query(Query query) {
    return new ArrayList<>(store.values());
  }

  @Override
  public PageResult<T> queryPage(PageParam pageParam) {
    return PageResult.of(new ArrayList<>(store.values()), (long) store.size(), 1, 10);
  }

  @Override
  public List<Map<String, Object>> aggregate(
      List<String> aggregations, Criteria<T> criteria, List<String> groupBy) {
    return List.of();
  }

  @Override
  public List<Map<String, Object>> aggregate(
      List<String> aggregations, Criteria<T> criteria, List<String> groupBy, List<String> having) {
    return List.of();
  }

  @Override
  public List<Map<String, Object>> aggregate(
      List<String> aggregations,
      Criteria<T> criteria,
      List<String> groupBy,
      List<String> having,
      boolean includeDeleted) {
    return List.of();
  }

  @Override
  public Map<String, Object> aggregate(List<String> aggregations, Criteria<T> criteria) {
    return Map.of();
  }

  @Override
  public Map<String, Object> aggregate(
      List<String> aggregations, Criteria<T> criteria, boolean includeDeleted) {
    return Map.of();
  }

  @Override
  public PageResult<Map<String, Object>> aggregateWithPagination(
      List<String> aggregations,
      Criteria<T> criteria,
      List<String> groupBy,
      List<String> having,
      int page,
      int size) {
    return PageResult.of(List.of(), 0L, page, size);
  }

  @Override
  public PageResult<Map<String, Object>> aggregateWithPagination(
      List<String> aggregations,
      Criteria<T> criteria,
      List<String> groupBy,
      List<String> having,
      int page,
      int size,
      boolean includeDeleted) {
    return PageResult.of(List.of(), 0L, page, size);
  }

  @Override
  public SqlExecutor getSqlExecutor() {
    return null;
  }
}
