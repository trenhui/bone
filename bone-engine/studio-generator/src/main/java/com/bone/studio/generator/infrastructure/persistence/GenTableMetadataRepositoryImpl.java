package com.bone.studio.generator.infrastructure.persistence;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.studio.generator.domain.data.GenTableMetadata;
import com.bone.studio.generator.domain.repository.GenTableMetadataRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class GenTableMetadataRepositoryImpl implements GenTableMetadataRepository {

    private final Map<Long, GenTableMetadata> store = new HashMap<>();

    @Override
    public GenTableMetadata findById(Long id) {
        return store.get(id);
    }

    @Override
    public Long save(GenTableMetadata entity) {
        if (entity.getId() == null) {
            entity = GenTableMetadata.builder()
                    .id(System.currentTimeMillis())
                    .tenantId(entity.getTenantId())
                    .dataSourceId(entity.getDataSourceId())
                    .tableSchema(entity.getTableSchema())
                    .originalTableName(entity.getOriginalTableName())
                    .customEntityName(entity.getCustomEntityName())
                    .moduleName(entity.getModuleName())
                    .tableComment(entity.getTableComment())
                    .syncStatus(entity.getSyncStatus())
                    .lastSyncAt(entity.getLastSyncAt())
                    .createdBy(entity.getCreatedBy())
                    .updatedBy(entity.getUpdatedBy())
                    .createdAt(entity.getCreatedAt())
                    .updatedAt(entity.getUpdatedAt())
                    .deleted(entity.isDeleted())
                    .version(entity.getVersion())
                    .columns(entity.getColumns())
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
    public GenTableMetadata findByIdIncludingDeleted(Long id) {
        return store.get(id);
    }

    @Override
    public List<GenTableMetadata> findByIds(List<Long> idList) {
        List<GenTableMetadata> result = new ArrayList<>();
        for (Long id : idList) {
            GenTableMetadata entity = store.get(id);
            if (entity != null) {
                result.add(entity);
            }
        }
        return result;
    }

    @Override
    public List<GenTableMetadata> findByIdsIncludingDeleted(List<Long> idList) {
        return findByIds(idList);
    }

    @Override
    public Long insert(GenTableMetadata entity) {
        return save(entity);
    }

    @Override
    public void batchInsert(List<GenTableMetadata> entities) {
        for (GenTableMetadata entity : entities) {
            save(entity);
        }
    }

    @Override
    public boolean update(GenTableMetadata entity) {
        if (store.containsKey(entity.getId())) {
            store.put(entity.getId(), entity);
            return true;
        }
        return false;
    }

    @Override
    public int updateByCriteria(GenTableMetadata entity, Criteria<GenTableMetadata> criteria) {
        return 0;
    }

    @Override
    public void batchSave(List<GenTableMetadata> entityList) {
        for (GenTableMetadata entity : entityList) {
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
    public List<GenTableMetadata> findByCriteria(Criteria<GenTableMetadata> criteria) {
        return new ArrayList<>(store.values());
    }

    @Override
    public GenTableMetadata findOneByCriteria(Criteria<GenTableMetadata> criteria) {
        return store.values().stream().findFirst().orElse(null);
    }

    @Override
    public com.bone.core.model.PageResult<GenTableMetadata> pageByCriteria(Criteria<GenTableMetadata> criteria) {
        return com.bone.core.model.PageResult.of(new ArrayList<>(store.values()), (long) store.size(), 1, 10);
    }

    @Override
    public Long countByCriteria(Criteria<GenTableMetadata> criteria) {
        return (long) store.size();
    }

    @Override
    public com.bone.core.model.PageResult<GenTableMetadata> queryByCondition(List<com.bone.core.model.QueryParam> queryParams, List<com.bone.core.model.SortingField> sortingFields, Integer pageNo, Integer pageSize, String bizIdentityCode) {
        return com.bone.core.model.PageResult.of(new ArrayList<>(store.values()), (long) store.size(), pageNo, pageSize);
    }

    @Override
    public List<GenTableMetadata> query(com.bone.core.model.Query queryParam) {
        return new ArrayList<>(store.values());
    }

    @Override
    public com.bone.core.model.PageResult<GenTableMetadata> queryPage(com.bone.core.model.PageParam pageParam) {
        return com.bone.core.model.PageResult.of(new ArrayList<>(store.values()), (long) store.size(), pageParam.getPage(), pageParam.getSize());
    }

    @Override
    public List<Map<String, Object>> aggregate(List<String> aggregations, Criteria<GenTableMetadata> criteria, List<String> groupBy) {
        return new ArrayList<>();
    }

    @Override
    public List<Map<String, Object>> aggregate(List<String> aggregations, Criteria<GenTableMetadata> criteria, List<String> groupBy, List<String> having) {
        return new ArrayList<>();
    }

    @Override
    public Map<String, Object> aggregate(List<String> aggregations, Criteria<GenTableMetadata> criteria) {
        return new HashMap<>();
    }

    @Override
    public com.bone.core.model.PageResult<Map<String, Object>> aggregateWithPagination(List<String> aggregations, Criteria<GenTableMetadata> criteria, List<String> groupBy, List<String> having, int pageNumber, int pageSize) {
        return com.bone.core.model.PageResult.of(new ArrayList<>(), 0L, pageNumber, pageSize);
    }

    @Override
    public SqlExecutor getSqlExecutor() {
        return null;
    }

    @Override
    public GenTableMetadata findByDataSourceIdAndTableName(String dataSourceId, String tableName) {
        return store.values().stream()
                .filter(m -> String.valueOf(m.getDataSourceId()).equals(dataSourceId)
                        && m.getOriginalTableName().equals(tableName))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<GenTableMetadata> findByDataSourceId(String dataSourceId) {
        List<GenTableMetadata> result = new ArrayList<>();
        for (GenTableMetadata m : store.values()) {
            if (String.valueOf(m.getDataSourceId()).equals(dataSourceId)) {
                result.add(m);
            }
        }
        return result;
    }
}