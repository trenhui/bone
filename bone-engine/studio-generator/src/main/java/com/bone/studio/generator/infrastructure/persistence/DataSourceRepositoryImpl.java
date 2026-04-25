package com.bone.studio.generator.infrastructure.persistence;

import com.bone.studio.generator.domain.data.DataSource;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
public class DataSourceRepositoryImpl implements DataSourceRepository {

    private final Map<String, DataSource> store = new HashMap<>();

    @Override
    public DataSource findById(String id) {
        log.debug("[DataSourceRepository] findById called, id={}, found={}", id, store.containsKey(id));
        return store.get(id);
    }

    @Override
    public String save(DataSource entity) {
        log.info("[DataSourceRepository] save called, entity.id={}, entity.name={}, current store size={}",
                entity.getId(), entity.getName(), store.size());

        if (entity.getId() == null) {
            String id = String.valueOf(System.currentTimeMillis());
            log.debug("[DataSourceRepository] entity.getId() is null, generated new id={}", id);
            entity = DataSource.create(
                    id,
                    entity.getName(),
                    entity.getType(),
                    entity.getHost(),
                    entity.getPort(),
                    entity.getDatabase(),
                    entity.getUsername(),
                    entity.getPassword()
            );
        }
        store.put(entity.getId(), entity);
        log.info("[DataSourceRepository] 数据源已保存到内存存储, id={}, store size after={}", entity.getId(), store.size());
        return entity.getId();
    }

    @Override
    public boolean deleteById(String id) {
        return store.remove(id) != null;
    }

    @Override
    public DataSource findByIdIncludingDeleted(String id) {
        return store.get(id);
    }

    @Override
    public List<DataSource> findByIds(List<String> idList) {
        List<DataSource> result = new ArrayList<>();
        for (String id : idList) {
            DataSource entity = store.get(id);
            if (entity != null) {
                result.add(entity);
            }
        }
        return result;
    }

    @Override
    public List<DataSource> findByIdsIncludingDeleted(List<String> idList) {
        return findByIds(idList);
    }

    @Override
    public String insert(DataSource entity) {
        return save(entity);
    }

    @Override
    public void batchInsert(List<DataSource> entities) {
        for (DataSource entity : entities) {
            save(entity);
        }
    }

    @Override
    public boolean update(DataSource entity) {
        if (store.containsKey(entity.getId())) {
            store.put(entity.getId(), entity);
            return true;
        }
        return false;
    }

    @Override
    public int updateByCriteria(DataSource entity, com.bone.metadata.sdk.query.criteria.Criteria<DataSource> criteria) {
        return 0;
    }

    @Override
    public void batchSave(List<DataSource> entityList) {
        for (DataSource entity : entityList) {
            save(entity);
        }
    }

    @Override
    public void deleteByIds(List<String> ids) {
        for (String id : ids) {
            store.remove(id);
        }
    }

    @Override
    public List<DataSource> findByCriteria(com.bone.metadata.sdk.query.criteria.Criteria<DataSource> criteria) {
        return new ArrayList<>(store.values());
    }

    @Override
    public DataSource findOneByCriteria(com.bone.metadata.sdk.query.criteria.Criteria<DataSource> criteria) {
        return store.values().stream().findFirst().orElse(null);
    }

    @Override
    public com.bone.core.model.PageResult<DataSource> pageByCriteria(com.bone.metadata.sdk.query.criteria.Criteria<DataSource> criteria) {
        return com.bone.core.model.PageResult.of(new ArrayList<>(store.values()), (long) store.size(), 1, 10);
    }

    @Override
    public Long countByCriteria(com.bone.metadata.sdk.query.criteria.Criteria<DataSource> criteria) {
        return (long) store.size();
    }

    @Override
    public com.bone.core.model.PageResult<DataSource> queryByCondition(List<com.bone.core.model.QueryParam> queryParams, List<com.bone.core.model.SortingField> sortingFields, Integer pageNo, Integer pageSize, String bizIdentityCode) {
        return com.bone.core.model.PageResult.of(new ArrayList<>(store.values()), (long) store.size(), pageNo, pageSize);
    }

    @Override
    public List<DataSource> query(com.bone.core.model.Query queryParam) {
        return new ArrayList<>(store.values());
    }

    @Override
    public com.bone.core.model.PageResult<DataSource> queryPage(com.bone.core.model.PageParam pageParam) {
        return com.bone.core.model.PageResult.of(new ArrayList<>(store.values()), (long) store.size(), pageParam.getPage(), pageParam.getSize());
    }

    @Override
    public List<Map<String, Object>> aggregate(List<String> aggregations, com.bone.metadata.sdk.query.criteria.Criteria<DataSource> criteria, List<String> groupBy) {
        return new ArrayList<>();
    }

    @Override
    public List<Map<String, Object>> aggregate(List<String> aggregations, com.bone.metadata.sdk.query.criteria.Criteria<DataSource> criteria, List<String> groupBy, List<String> having) {
        return new ArrayList<>();
    }

    @Override
    public Map<String, Object> aggregate(List<String> aggregations, com.bone.metadata.sdk.query.criteria.Criteria<DataSource> criteria) {
        return new HashMap<>();
    }

    @Override
    public com.bone.core.model.PageResult<Map<String, Object>> aggregateWithPagination(List<String> aggregations, com.bone.metadata.sdk.query.criteria.Criteria<DataSource> criteria, List<String> groupBy, List<String> having, int pageNumber, int pageSize) {
        return com.bone.core.model.PageResult.of(new ArrayList<>(), 0L, pageNumber, pageSize);
    }

    @Override
    public com.bone.metadata.sdk.sql.executor.SqlExecutor getSqlExecutor() {
        return null;
    }

    @Override
    public List<DataSource> findByType(String type) {
        List<DataSource> result = new ArrayList<>();
        for (DataSource dataSource : store.values()) {
            if (dataSource.getType().equals(type)) {
                result.add(dataSource);
            }
        }
        return result;
    }

    @Override
    public List<DataSource> findByStatus(String status) {
        List<DataSource> result = new ArrayList<>();
        for (DataSource dataSource : store.values()) {
            if (dataSource.getStatus().equals(status)) {
                result.add(dataSource);
            }
        }
        return result;
    }
}