package com.bone.studio.generator.infrastructure.persistence;

import com.bone.core.model.PageParam;
import com.bone.core.model.PageResult;
import com.bone.core.model.Query;
import com.bone.core.model.QueryParam;
import com.bone.core.model.SortingField;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.studio.generator.domain.data.CodeTemplate;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class CodeTemplateRepositoryImpl implements CodeTemplateRepository {

    private final Map<Long, CodeTemplate> templateStore = new HashMap<>();

    @Override
    public CodeTemplate findById(Long id) {
        return templateStore.get(id);
    }

    @Override
    public Long save(CodeTemplate codeTemplate) {
        templateStore.put(codeTemplate.getId(), codeTemplate);
        return codeTemplate.getId();
    }

    @Override
    public boolean deleteById(Long id) {
        return templateStore.remove(id) != null;
    }

    @Override
    public CodeTemplate findByIdIncludingDeleted(Long id) {
        return templateStore.get(id);
    }

    @Override
    public List<CodeTemplate> findByIds(List<Long> idList) {
        List<CodeTemplate> result = new ArrayList<>();
        for (Long id : idList) {
            CodeTemplate template = templateStore.get(id);
            if (template != null) {
                result.add(template);
            }
        }
        return result;
    }

    @Override
    public List<CodeTemplate> findByIdsIncludingDeleted(List<Long> idList) {
        List<CodeTemplate> result = new ArrayList<>();
        for (Long id : idList) {
            CodeTemplate template = templateStore.get(id);
            if (template != null) {
                result.add(template);
            }
        }
        return result;
    }



    @Override
    public Long insert(CodeTemplate entity) {
        templateStore.put(entity.getId(), entity);
        return entity.getId();
    }

    @Override
    public void batchInsert(List<CodeTemplate> entities) {
        for (CodeTemplate entity : entities) {
            templateStore.put(entity.getId(), entity);
        }
    }

    @Override
    public boolean update(CodeTemplate entity) {
        if (templateStore.containsKey(entity.getId())) {
            templateStore.put(entity.getId(), entity);
            return true;
        }
        return false;
    }

    @Override
    public int updateByCriteria(CodeTemplate entity, Criteria<CodeTemplate> criteria) {
        // 简化实现，实际应根据criteria进行更新
        return 0;
    }

    @Override
    public void batchSave(List<CodeTemplate> entityList) {
        for (CodeTemplate entity : entityList) {
            templateStore.put(entity.getId(), entity);
        }
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        for (Long id : ids) {
            templateStore.remove(id);
        }
    }

    @Override
    public List<CodeTemplate> findByCriteria(Criteria<CodeTemplate> criteria) {
        // 简化实现，实际应根据criteria进行查询
        return new ArrayList<>(templateStore.values());
    }

    @Override
    public CodeTemplate findOneByCriteria(Criteria<CodeTemplate> criteria) {
        // 简化实现，实际应根据criteria进行查询
        return templateStore.values().stream().findFirst().orElse(null);
    }

    @Override
    public PageResult<CodeTemplate> pageByCriteria(Criteria<CodeTemplate> criteria) {
        // 简化实现，实际应根据criteria进行分页查询
        return PageResult.of(new ArrayList<>(templateStore.values()), (long) templateStore.size(), 1, 10);
    }

    @Override
    public Long countByCriteria(Criteria<CodeTemplate> criteria) {
        // 简化实现，实际应根据criteria进行计数
        return (long) templateStore.size();
    }

    @Override
    public PageResult<CodeTemplate> queryByCondition(List<QueryParam> queryParams, List<SortingField> sortingFields, Integer pageNo, Integer pageSize, String bizIdentityCode) {
        // 简化实现，实际应根据条件进行查询
        return PageResult.of(new ArrayList<>(templateStore.values()), (long) templateStore.size(), pageNo, pageSize);
    }

    @Override
    public List<CodeTemplate> query(Query queryParam) {
        // 简化实现，实际应根据查询参数进行查询
        return new ArrayList<>(templateStore.values());
    }

    @Override
    public PageResult<CodeTemplate> queryPage(PageParam pageParam) {
        // 简化实现，实际应根据分页参数进行查询
        return PageResult.of(new ArrayList<>(templateStore.values()), (long) templateStore.size(), pageParam.getPage(), pageParam.getSize());
    }

    @Override
    public List<Map<String, Object>> aggregate(List<String> aggregations, Criteria<CodeTemplate> criteria, List<String> groupBy) {
        // 简化实现，实际应根据聚合表达式进行查询
        return new ArrayList<>();
    }

    @Override
    public List<Map<String, Object>> aggregate(List<String> aggregations, Criteria<CodeTemplate> criteria, List<String> groupBy, List<String> having) {
        // 简化实现，实际应根据聚合表达式进行查询
        return new ArrayList<>();
    }

    @Override
    public Map<String, Object> aggregate(List<String> aggregations, Criteria<CodeTemplate> criteria) {
        // 简化实现，实际应根据聚合表达式进行查询
        return new HashMap<>();
    }

    @Override
    public PageResult<Map<String, Object>> aggregateWithPagination(List<String> aggregations, Criteria<CodeTemplate> criteria, List<String> groupBy, List<String> having, int pageNumber, int pageSize) {
        // 简化实现，实际应根据聚合表达式进行分页查询
        return PageResult.of(new ArrayList<>(), 0L, pageNumber, pageSize);
    }

    @Override
    public SqlExecutor getSqlExecutor() {
        // 简化实现，返回null
        return null;
    }
}
