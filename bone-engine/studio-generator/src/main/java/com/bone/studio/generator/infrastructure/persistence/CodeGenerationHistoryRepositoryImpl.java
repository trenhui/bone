package com.bone.studio.generator.infrastructure.persistence;

import com.bone.studio.generator.domain.history.CodeGenerationHistory;
import com.bone.studio.generator.domain.repository.CodeGenerationHistoryRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class CodeGenerationHistoryRepositoryImpl implements CodeGenerationHistoryRepository {

    private final Map<Long, CodeGenerationHistory> store = new HashMap<>();

    @Override
    public CodeGenerationHistory findById(Long id) {
        return store.get(id);
    }

    @Override
    public Long save(CodeGenerationHistory entity) {
        if (entity.getId() == null) {
            // 简单实现，直接使用当前时间戳作为 ID
            entity = CodeGenerationHistory.builder()
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
    public CodeGenerationHistory findByIdIncludingDeleted(Long id) {
        return store.get(id);
    }

    @Override
    public List<CodeGenerationHistory> findByIds(List<Long> idList) {
        List<CodeGenerationHistory> result = new ArrayList<>();
        for (Long id : idList) {
            CodeGenerationHistory entity = store.get(id);
            if (entity != null) {
                result.add(entity);
            }
        }
        return result;
    }

    @Override
    public List<CodeGenerationHistory> findByIdsIncludingDeleted(List<Long> idList) {
        return findByIds(idList);
    }

    @Override
    public Long insert(CodeGenerationHistory entity) {
        return save(entity);
    }

    @Override
    public void batchInsert(List<CodeGenerationHistory> entities) {
        for (CodeGenerationHistory entity : entities) {
            save(entity);
        }
    }

    @Override
    public boolean update(CodeGenerationHistory entity) {
        if (store.containsKey(entity.getId())) {
            store.put(entity.getId(), entity);
            return true;
        }
        return false;
    }

    @Override
    public int updateByCriteria(CodeGenerationHistory entity, com.bone.metadata.sdk.query.criteria.Criteria<CodeGenerationHistory> criteria) {
        return 0;
    }

    @Override
    public void batchSave(List<CodeGenerationHistory> entityList) {
        for (CodeGenerationHistory entity : entityList) {
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
    public List<CodeGenerationHistory> findByCriteria(com.bone.metadata.sdk.query.criteria.Criteria<CodeGenerationHistory> criteria) {
        return new ArrayList<>(store.values());
    }

    @Override
    public CodeGenerationHistory findOneByCriteria(com.bone.metadata.sdk.query.criteria.Criteria<CodeGenerationHistory> criteria) {
        return store.values().stream().findFirst().orElse(null);
    }

    @Override
    public com.bone.core.model.PageResult<CodeGenerationHistory> pageByCriteria(com.bone.metadata.sdk.query.criteria.Criteria<CodeGenerationHistory> criteria) {
        return com.bone.core.model.PageResult.of(new ArrayList<>(store.values()), (long) store.size(), 1, 10);
    }

    @Override
    public Long countByCriteria(com.bone.metadata.sdk.query.criteria.Criteria<CodeGenerationHistory> criteria) {
        return (long) store.size();
    }

    @Override
    public com.bone.core.model.PageResult<CodeGenerationHistory> queryByCondition(List<com.bone.core.model.QueryParam> queryParams, List<com.bone.core.model.SortingField> sortingFields, Integer pageNo, Integer pageSize, String bizIdentityCode) {
        return com.bone.core.model.PageResult.of(new ArrayList<>(store.values()), (long) store.size(), pageNo, pageSize);
    }

    @Override
    public List<CodeGenerationHistory> query(com.bone.core.model.Query queryParam) {
        return new ArrayList<>(store.values());
    }

    @Override
    public com.bone.core.model.PageResult<CodeGenerationHistory> queryPage(com.bone.core.model.PageParam pageParam) {
        return com.bone.core.model.PageResult.of(new ArrayList<>(store.values()), (long) store.size(), pageParam.getPage(), pageParam.getSize());
    }

    @Override
    public List<Map<String, Object>> aggregate(List<String> aggregations, com.bone.metadata.sdk.query.criteria.Criteria<CodeGenerationHistory> criteria, List<String> groupBy) {
        return new ArrayList<>();
    }

    @Override
    public List<Map<String, Object>> aggregate(List<String> aggregations, com.bone.metadata.sdk.query.criteria.Criteria<CodeGenerationHistory> criteria, List<String> groupBy, List<String> having) {
        return new ArrayList<>();
    }

    @Override
    public Map<String, Object> aggregate(List<String> aggregations, com.bone.metadata.sdk.query.criteria.Criteria<CodeGenerationHistory> criteria) {
        return new HashMap<>();
    }

    @Override
    public com.bone.core.model.PageResult<Map<String, Object>> aggregateWithPagination(List<String> aggregations, com.bone.metadata.sdk.query.criteria.Criteria<CodeGenerationHistory> criteria, List<String> groupBy, List<String> having, int pageNumber, int pageSize) {
        return com.bone.core.model.PageResult.of(new ArrayList<>(), 0L, pageNumber, pageSize);
    }

    @Override
    public com.bone.metadata.sdk.sql.executor.SqlExecutor getSqlExecutor() {
        return null;
    }

    @Override
    public List<CodeGenerationHistory> findByTaskId(String taskId) {
        List<CodeGenerationHistory> result = new ArrayList<>();
        for (CodeGenerationHistory history : store.values()) {
            if (history.getTaskId().equals(taskId)) {
                result.add(history);
            }
        }
        return result;
    }

    @Override
    public List<CodeGenerationHistory> findByDataSourceId(String dataSourceId) {
        List<CodeGenerationHistory> result = new ArrayList<>();
        for (CodeGenerationHistory history : store.values()) {
            if (history.getDataSourceId().equals(dataSourceId)) {
                result.add(history);
            }
        }
        return result;
    }

    @Override
    public List<CodeGenerationHistory> findByStatus(String status) {
        List<CodeGenerationHistory> result = new ArrayList<>();
        for (CodeGenerationHistory history : store.values()) {
            if (history.getStatus().equals(status)) {
                result.add(history);
            }
        }
        return result;
    }
}