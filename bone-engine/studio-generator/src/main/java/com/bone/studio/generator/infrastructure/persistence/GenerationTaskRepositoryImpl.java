package com.bone.studio.generator.infrastructure.persistence;

import com.bone.studio.generator.domain.data.GenerationTask;
import com.bone.studio.generator.domain.repository.GenerationTaskRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class GenerationTaskRepositoryImpl implements GenerationTaskRepository {

    private final Map<Long, GenerationTask> store = new HashMap<>();

    @Override
    public GenerationTask findById(Long id) {
        return store.get(id);
    }

    @Override
    public Long save(GenerationTask entity) {
        if (entity.getId() == null) {
            // 简单实现，直接使用当前时间戳作为 ID
            entity = GenerationTask.builder()
                    .id(System.currentTimeMillis())
                    .build();
        }
        store.put(entity.getId(), entity);
        return entity.getId();
    }

    @Override
    public boolean deleteById(Long id) {
        return store.remove(id) != null;
    }

    @Override
    public GenerationTask findByIdIncludingDeleted(Long id) {
        return store.get(id);
    }

    @Override
    public List<GenerationTask> findByIds(List<Long> idList) {
        List<GenerationTask> result = new ArrayList<>();
        for (Long id : idList) {
            GenerationTask entity = store.get(id);
            if (entity != null) {
                result.add(entity);
            }
        }
        return result;
    }

    @Override
    public List<GenerationTask> findByIdsIncludingDeleted(List<Long> idList) {
        return findByIds(idList);
    }

    @Override
    public Long insert(GenerationTask entity) {
        return save(entity);
    }

    @Override
    public void batchInsert(List<GenerationTask> entities) {
        for (GenerationTask entity : entities) {
            save(entity);
        }
    }

    @Override
    public boolean update(GenerationTask entity) {
        if (store.containsKey(entity.getId())) {
            store.put(entity.getId(), entity);
            return true;
        }
        return false;
    }

    @Override
    public int updateByCriteria(GenerationTask entity, com.bone.metadata.sdk.query.criteria.Criteria<GenerationTask> criteria) {
        return 0;
    }

    @Override
    public void batchSave(List<GenerationTask> entityList) {
        for (GenerationTask entity : entityList) {
            save(entity);
        }
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        for (Long id : ids) {
            store.remove(id);
        }
    }

    @Override
    public List<GenerationTask> findByCriteria(com.bone.metadata.sdk.query.criteria.Criteria<GenerationTask> criteria) {
        return new ArrayList<>(store.values());
    }

    @Override
    public GenerationTask findOneByCriteria(com.bone.metadata.sdk.query.criteria.Criteria<GenerationTask> criteria) {
        return store.values().stream().findFirst().orElse(null);
    }

    @Override
    public com.bone.core.model.PageResult<GenerationTask> pageByCriteria(com.bone.metadata.sdk.query.criteria.Criteria<GenerationTask> criteria) {
        return com.bone.core.model.PageResult.of(new ArrayList<>(store.values()), (long) store.size(), 1, 10);
    }

    @Override
    public Long countByCriteria(com.bone.metadata.sdk.query.criteria.Criteria<GenerationTask> criteria) {
        return (long) store.size();
    }

    @Override
    public com.bone.core.model.PageResult<GenerationTask> queryByCondition(List<com.bone.core.model.QueryParam> queryParams, List<com.bone.core.model.SortingField> sortingFields, Integer pageNo, Integer pageSize, String bizIdentityCode) {
        return com.bone.core.model.PageResult.of(new ArrayList<>(store.values()), (long) store.size(), pageNo, pageSize);
    }

    @Override
    public List<GenerationTask> query(com.bone.core.model.Query queryParam) {
        return new ArrayList<>(store.values());
    }

    @Override
    public com.bone.core.model.PageResult<GenerationTask> queryPage(com.bone.core.model.PageParam pageParam) {
        return com.bone.core.model.PageResult.of(new ArrayList<>(store.values()), (long) store.size(), pageParam.getPage(), pageParam.getSize());
    }

    @Override
    public List<Map<String, Object>> aggregate(List<String> aggregations, com.bone.metadata.sdk.query.criteria.Criteria<GenerationTask> criteria, List<String> groupBy) {
        return new ArrayList<>();
    }

    @Override
    public List<Map<String, Object>> aggregate(List<String> aggregations, com.bone.metadata.sdk.query.criteria.Criteria<GenerationTask> criteria, List<String> groupBy, List<String> having) {
        return new ArrayList<>();
    }

    @Override
    public Map<String, Object> aggregate(List<String> aggregations, com.bone.metadata.sdk.query.criteria.Criteria<GenerationTask> criteria) {
        return new HashMap<>();
    }

    @Override
    public com.bone.core.model.PageResult<Map<String, Object>> aggregateWithPagination(List<String> aggregations, com.bone.metadata.sdk.query.criteria.Criteria<GenerationTask> criteria, List<String> groupBy, List<String> having, int pageNumber, int pageSize) {
        return com.bone.core.model.PageResult.of(new ArrayList<>(), 0L, pageNumber, pageSize);
    }

    @Override
    public com.bone.metadata.sdk.sql.executor.SqlExecutor getSqlExecutor() {
        return null;
    }

    @Override
    public GenerationTask findByTaskId(String taskId) {
        return store.values().stream()
                .filter(task -> task.getTaskId().equals(taskId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<GenerationTask> findByDataSourceId(Long dataSourceId) {
        List<GenerationTask> result = new ArrayList<>();
        for (GenerationTask task : store.values()) {
            if (task.getDataSourceId().equals(dataSourceId)) {
                result.add(task);
            }
        }
        return result;
    }

    @Override
    public List<GenerationTask> findByStatus(String status) {
        List<GenerationTask> result = new ArrayList<>();
        for (GenerationTask task : store.values()) {
            if (task.getStatus().equals(status)) {
                result.add(task);
            }
        }
        return result;
    }
}