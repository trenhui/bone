package com.bone.metadata.sdk;

import com.bone.core.domain.entity.Entity;
import com.bone.core.domain.extension.Extensible;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.enums.Operator;
import com.bone.core.model.PageParam;
import com.bone.core.model.PageResult;
import com.bone.core.model.Query;
import com.bone.core.model.QueryParam;
import com.bone.core.model.SortablePageParam;
import com.bone.core.model.SortableParam;
import com.bone.core.model.SortingField;
import com.bone.core.tenant.context.TenantContext;
import com.bone.core.tenant.context.BizIdentityContext;
import com.bone.metadata.sdk.domain.enums.SortDirection;
import com.bone.metadata.sdk.domain.exception.*;
import com.bone.metadata.sdk.domain.exception.QueryExecutionException;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.ColumnMetadata;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.BatchCompiledQuery;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.domain.spec.TableMetadataResolver;
import com.bone.metadata.sdk.extension.ExtensionContext;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.query.converter.QueryObjectConverter;
import com.bone.metadata.sdk.query.criteria.Condition;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.metadata.sdk.sql.executor.TypeConverter;
import com.bone.metadata.sdk.support.cache.FieldCache;
import com.bone.metadata.sdk.support.config.MetadataSdkContext;
import com.bone.metadata.sdk.support.util.ParamConvertUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class BaseRepository<T extends Entity<ID>, ID> implements Repository<T, ID> {
    private static final Logger log = LoggerFactory.getLogger(BaseRepository.class);

    @Value("${jdbc.batch.size:1000}")
    private final int maxBatchSize = 1000;

    private static final int MAX_PAGINATION_THRESHOLD = 1000;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int DEFAULT_PAGE_NUMBER = 1;
    private static final String ORDER_BY_PARAM = "orderBy";

    private final SqlBuilder sqlBuilder;
    private final SqlExecutor sqlExecutor;
    private final Class<T> entityClass;
    private final ExtensionCoordinator extensionCoordinator;
    private final Set<String> cachedFieldNames;

    @Autowired
    public BaseRepository(SqlBuilder sqlBuilder,
                          SqlExecutor sqlExecutor,
                          Class<T> entityClass,
                          ExtensionCoordinator extensionCoordinator) {
        this.sqlBuilder = Objects.requireNonNull(sqlBuilder, "SQL builder must not be null");
        this.sqlExecutor = Objects.requireNonNull(sqlExecutor, "SQL executor must not be null");
        this.entityClass = Objects.requireNonNull(entityClass, "Entity class must not be null");
        this.extensionCoordinator = Objects.requireNonNull(extensionCoordinator, "ExtensionCoordinator must not be null");
        this.cachedFieldNames = Collections.unmodifiableSet(FieldCache.getCachedFields(entityClass).keySet());
    }

    // ========== 基础 CRUD ==========

    /**
     * 内部通用方法：根据ID查询实体
     * @param id 实体ID
     * @param includeDeleted 是否包含已删除实体
     * @return 查询到的实体，如果未找到则返回null
     */
    @Transactional(readOnly = true)
    private T findByIdInternal(ID id, boolean includeDeleted) {
        Assert.notNull(id, "ID must not be null");
        try {
            TableMetadata tableMetadata = TableMetadataResolver.load(entityClass);
            Criteria<T> criteria = Criteria.<T>create().eq(tableMetadata.getPrimaryKey().getName(), id);
            
            CompiledQuery query;
            if (includeDeleted) {
                // 包含软删除的版本
                query = sqlBuilder.buildSelect(entityClass, criteria, true);
            } else {
                // 正常查询版本
                AllocationContext context = Extensible.class.isAssignableFrom(entityClass) ? getAllocationContext() : null;
                query = sqlBuilder.buildSelect(entityClass, criteria, context);
            }
            
            T entity = sqlExecutor.executeSingleQuery(query, entityClass);
            if (entity != null && Extensible.class.isAssignableFrom(entityClass)) {
                loadExtensionFields(entity);
            }
            return entity;
        } catch (Exception e) {
            throw new QueryExecutionException(String.format("Failed to query %s with id %s", 
                    entityClass.getSimpleName(), id), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public T findById(ID id) {
        return findByIdInternal(id, false);
    }

    /**
     * 内部通用方法：根据ID列表查询实体
     * @param idList 实体ID列表
     * @param includeDeleted 是否包含已删除实体
     * @return 查询到的实体列表
     */
    @Transactional(readOnly = true)
    private List<T> findByIdsInternal(List<ID> idList, boolean includeDeleted) {
        if (idList == null || idList.isEmpty()) return Collections.emptyList();
        Assert.noNullElements(idList, "ID list must not contain null elements");
        try {
            TableMetadata tableMetadata = TableMetadataResolver.load(entityClass);
            Criteria<T> criteria = Criteria.<T>create().in(tableMetadata.getPrimaryKey().getName(), idList);
            
            CompiledQuery query;
            if (includeDeleted) {
                // 包含软删除的版本
                query = sqlBuilder.buildSelect(entityClass, criteria, true);
            } else {
                // 正常查询版本
                AllocationContext context = Extensible.class.isAssignableFrom(entityClass) ? getAllocationContext() : null;
                query = sqlBuilder.buildSelect(entityClass, criteria, context);
            }
            
            List<T> list = sqlExecutor.executeQuery(query, entityClass);
            // 只有在实体实现Extensible接口时才加载扩展字段
            if (Extensible.class.isAssignableFrom(entityClass)) {
                list.forEach(this::loadExtensionFields);
            }
            return list;
        } catch (Exception e) {
            throw new QueryExecutionException(
                String.format("Failed to find entities %s by IDs", entityClass.getSimpleName()), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<T> findByIds(List<ID> idList) {
        return findByIdsInternal(idList, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<T> findByIdsIncludingDeleted(List<ID> idList) {
        return findByIdsInternal(idList, true);
    }

    @Override
    @Transactional(readOnly = true)
    public T findByIdIncludingDeleted(ID id) {
        return findByIdInternal(id, true);
    }

    @Override
    @Transactional
    public ID insert(@Valid T entity) {
        Assert.notNull(entity, "Entity must not be null");
        try {
            TableMetadata tableMetadata = TableMetadataResolver.load(entityClass);
            ColumnMetadata primaryKey = tableMetadata.getPrimaryKey();
            GenerationStrategy strategy = primaryKey.getGenerationStrategy();

            if (strategy != GenerationStrategy.IDENTITY) {
                // 非自增：先生成ID，再批量插入（单条）
                Object generatedId = sqlExecutor.generateId(strategy, entity);
                setEntityId(entity, generatedId);
                BatchCompiledQuery batch = sqlBuilder.buildBatchInsert(entityClass, Collections.singletonList(entity));
                sqlExecutor.executeBatchUpdate(batch);
            } else {
                // 自增：将 BatchCompiledQuery 收敛为单条 CompiledQuery 再取回主键
                BatchCompiledQuery batch = sqlBuilder.buildBatchInsert(entityClass, Collections.singletonList(entity));
                Map<String, Object> firstParams = batch.getBatchParameters().get(0);
                CompiledQuery singleInsert = new CompiledQuery(batch.getSql(), firstParams);
                Object newId = sqlExecutor.executeInsert(singleInsert, entityClass);
                setEntityId(entity, newId);
            }
            saveExtensionFields(entity);
            return entity.getId();
        } catch (Exception e) {
            throw new QueryExecutionException(
                String.format("Failed to insert entity %s", entityClass.getSimpleName()), e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public void batchInsert(List<T> entities) {
        if (entities == null || entities.isEmpty()) return;
        Assert.noNullElements(entities, "Entities list must not contain null elements");

        TableMetadata tableMetadata = TableMetadataResolver.load(entityClass);
        GenerationStrategy strategy = tableMetadata.getPrimaryKey().getGenerationStrategy();

        if (strategy != GenerationStrategy.IDENTITY) {
            entities.forEach(e -> {
                if (e.getId() == null) {
                    Object generatedId = sqlExecutor.generateId(strategy, e);
                    setEntityId(e, generatedId);
                }
            });
        }

        partition(entities, maxBatchSize).forEach(batch -> {
            sqlExecutor.executeBatchUpdate(sqlBuilder.buildBatchInsert(entityClass, batch));
            batch.forEach(this::saveExtensionFields);
        });
    }

    @Override
    @Transactional
    public boolean update(@Valid T entity) {
        Assert.notNull(entity, "Entity must not be null");
        Assert.notNull(entity.getId(), "Entity ID must not be null for update");
        try {
            CompiledQuery query = sqlBuilder.buildDynamicUpdate(entityClass, entity);
            int affectedRows = sqlExecutor.executeUpdate(query);
            saveExtensionFields(entity);
            if (affectedRows == 0) {
                log.warn("No rows updated for entity id={}", entity.getId());
                return false;
            }
            return true;
        } catch (Exception e) {
            throw new QueryExecutionException(
                String.format("Failed to update entity %s", entityClass.getSimpleName()), e);
        }
    }

    @Override
    @Transactional
    public int updateByCriteria(@Valid T entity, Criteria<T> criteria) {
        Assert.notNull(entity, "Entity must not be null");
        Assert.notNull(criteria, "Criteria must not be null");
        CompiledQuery query = sqlBuilder.buildConditionalUpdate(entityClass, entity, criteria);
        int affectedRows = sqlExecutor.executeUpdate(query);
        saveExtensionFields(entity);
        return affectedRows;
    }

    @Override
    @Transactional
    public ID save(@Valid T entity) {
        Assert.notNull(entity, "Entity must not be null");
        ensureIdInitialized(entity);
        ID id = entity.getId();
        boolean exists = (id != null && findByIdIncludingDeleted(id) != null);
        if (exists) {
            update(entity);
        } else {
            // insert 内部已设置主键
            insert(entity);
        }
        return entity.getId();
    }

//    @Override
//    @Transactional
//    public ID save(@Valid T entity) {
//        // 1. 确保ID初始化（非自增策略且ID为空时生成）
//        ensureIdInitialized(entity);
//
//        // 2. 判断记录是否存在（包含软删除的记录）
//        ID entityId = entity.getId();
//        boolean exists = (entityId != null && findByIdIncludingDeleted(entityId) != null);
//
//        if (exists) {
//            // 3. 记录存在：执行更新
//            update(entity);
//        } else {
//            // 4. 记录不存在且不支持UPSERT：执行插入
//            setEntityId(entity, insert(entity));
//        }
//
//        // 5. 统一保存扩展字段
//        saveExtensionFields(entity);
//        return entity.getId();
//    }

    @Override
    @Transactional
    public void batchSave(List<T> entities) {
        // 参数验证
        if (entities == null || entities.isEmpty()) return;
        Assert.noNullElements(entities, "Entities list must not contain null elements");

        // 初始化实体ID并分组
        List<T> toInsert = new ArrayList<>();
        List<T> toUpdate = new ArrayList<>();
        List<ID> idsToCheck = new ArrayList<>();

        // 预处理阶段：初始化ID并收集需要检查的ID
        entities.forEach(entity -> {
            // 确保ID初始化（非自增策略且ID为空时生成）
            ensureIdInitialized(entity);
            ID entityId = entity.getId();
            
            if (entityId == null) {
                // ID仍为null的实体直接插入（通常是自增ID）
                toInsert.add(entity);
            } else {
                // 收集有ID的实体以便后续检查存在性
                idsToCheck.add(entityId);
            }
        });

        // 检查ID存在性
        final Set<ID> existingIds = idsToCheck.isEmpty() 
            ? Collections.emptySet() 
            : findByIds(idsToCheck).stream()
                  .map(Entity::getId)
                  .collect(Collectors.toSet());

        // 最终分类：基于ID存在性决定插入或更新
        entities.forEach(entity -> {
            ID entityId = entity.getId();
            if (entityId == null || !existingIds.contains(entityId)) {
                toInsert.add(entity);
            } else {
                toUpdate.add(entity);
            }
        });

        // 执行批量操作
        if (!toInsert.isEmpty()) {
            // 对于少量实体，直接逐个插入可能更简单
            if (toInsert.size() <= smallBatchThreshold) {
                toInsert.forEach(this::insertAndSaveExtensions);
            } else {
                batchInsert(toInsert);
                // 保存所有插入实体的扩展字段
                toInsert.forEach(this::saveExtensionFields);
            }
        }
        
        if (!toUpdate.isEmpty()) {
            batchUpdate(toUpdate);
        }
    }
    
    /**
     * 小批量阈值，低于此值的实体集合使用非批量方式处理
     */
    private static final int smallBatchThreshold = 5;
    
    /**
     * 插入单个实体并保存扩展字段
     * @param entity 实体对象
     */
    private void insertAndSaveExtensions(T entity) {
        insert(entity);
        saveExtensionFields(entity);
    }

    private void batchUpdate(List<T> entities) {
        if (entities == null || entities.isEmpty()) return;
        partition(entities, maxBatchSize).forEach(batch -> {
            BatchCompiledQuery query = sqlBuilder.buildBatchUpdate(entityClass, batch);
            sqlExecutor.executeBatchUpdate(query);
            batch.forEach(this::saveExtensionFields);
        });
    }

    /**
     * 内部通用方法：创建基于主键的删除查询
     * @param criteria 基于主键的查询条件
     * @return 编译后的删除查询
     */
    private CompiledQuery createDeleteQueryByPrimaryKey(Criteria<T> criteria) {
        AllocationContext context = Extensible.class.isAssignableFrom(entityClass) ? getAllocationContext() : null;
        return sqlBuilder.buildDelete(entityClass, criteria, context);
    }

    @Override
    @Transactional
    public boolean deleteById(ID id) {
        Assert.notNull(id, "ID must not be null");
        try {
            TableMetadata tableMetadata = TableMetadataResolver.load(entityClass);
            Criteria<T> criteria = Criteria.<T>create().eq(tableMetadata.getPrimaryKey().getName(), id);
            CompiledQuery query = createDeleteQueryByPrimaryKey(criteria);
            int affectedRows = sqlExecutor.executeUpdate(query);
            return affectedRows > 0;
        } catch (Exception e) {
            throw new QueryExecutionException(
                String.format("Failed to delete entity %s by ID: %s", entityClass.getSimpleName(), id), e);
        }
    }

    @Override
    @Transactional
    public void deleteByIds(List<ID> ids) {
        if (ids == null || ids.isEmpty()) return;
        Assert.noNullElements(ids, "ID list must not contain null elements");
        try {
            TableMetadata tableMetadata = TableMetadataResolver.load(entityClass);
            Criteria<T> criteria = Criteria.<T>create().in(tableMetadata.getPrimaryKey().getName(), ids);
            CompiledQuery query = createDeleteQueryByPrimaryKey(criteria);
            sqlExecutor.executeUpdate(query);
        } catch (Exception e) {
            throw new QueryExecutionException(
                String.format("Failed to delete entities %s by IDs", entityClass.getSimpleName()), e);
        }
    }

    @Transactional
    public void deleteByCriteria(Criteria criteria) {
        Assert.notNull(criteria, "Criteria must not be null");
        try {
            TableMetadata tableMetadata = TableMetadataResolver.load(entityClass);
            AllocationContext context = Extensible.class.isAssignableFrom(entityClass) ? getAllocationContext() : null;
            CompiledQuery query = sqlBuilder.buildDelete(entityClass, criteria, context);
            sqlExecutor.executeUpdate(query);
        } catch (Exception e) {
            throw new QueryExecutionException(
                String.format("Failed to delete entities %s by criteria", entityClass.getSimpleName()), e);
        }
    }

    // ========== 条件查询 / 统计 ==========
    protected void validateCriteriaFields(Criteria<T> criteria) {
        for (Condition condition : criteria.getMainConditions()) {
            if (FieldCache.getFieldByName(entityClass, condition.getFieldName()) == null) {
                throw new UndefinedFieldException(
                        String.format("Field '%s' is not defined in entity %s",
                                condition.getFieldName(), entityClass.getSimpleName()));
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<T> findByCriteria(Criteria<T> criteria) {
        Assert.notNull(criteria, "Criteria must not be null");
        try {
            validateCriteriaFields(criteria);
            AllocationContext context = Extensible.class.isAssignableFrom(entityClass) ? getAllocationContext() : null;
            CompiledQuery query = sqlBuilder.buildSelect(entityClass, criteria, context);
            List<T> list = sqlExecutor.executeQuery(query, entityClass);
            list.forEach(this::loadExtensionFields);
            return list;
        } catch (Exception e) {
            throw new QueryExecutionException(
                String.format("Failed to find entities %s by criteria", entityClass.getSimpleName()), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public T findOneByCriteria(Criteria<T> criteria) throws MultipleResultsException {
        Assert.notNull(criteria, "Criteria must not be null");
        CompiledQuery query = sqlBuilder.buildSelect(entityClass, criteria);
        List<T> results = sqlExecutor.executeQuery(query, entityClass);
        if (results.size() > 1) {
            throw new MultipleResultsException("Expected single model, found: " + results.size());
        }
        T entity = results.stream().findFirst().orElse(null);
        if (entity != null) loadExtensionFields(entity);
        return entity;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<T> pageByCriteria(Criteria<T> criteria) {
        Assert.notNull(criteria, "Criteria must not be null");
        if (criteria.getPageNo() > MAX_PAGINATION_THRESHOLD) {
            log.warn("Large page number: {} (consider cursor pagination).", criteria.getPageNo());
        }

        // 查询当前页
        CompiledQuery select = sqlBuilder.buildSelect(entityClass, criteria);
        List<T> content = sqlExecutor.executeQuery(select, entityClass);
        content.forEach(this::loadExtensionFields);

        // 计数（若你的 CountBuilderSql 已忽略分页，可直接用 countByCriteria(criteria)）
        Integer originalPageNumber = criteria.getPageNo();
        Integer originalPageSize = criteria.getPageSize();
        Long total;
        try {
            total = countByCriteria(criteria);
        } finally {
            criteria.setPageNo(originalPageNumber);
            criteria.setPageSize(originalPageSize);
        }
        int pageNo = originalPageNumber != null ? originalPageNumber : DEFAULT_PAGE_NUMBER;
        int pageSize = originalPageSize != null ? originalPageSize : DEFAULT_PAGE_SIZE;
        return PageResult.of(content, total, pageNo, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public Long countByCriteria(Criteria<T> criteria) {
        Assert.notNull(criteria, "Criteria must not be null");
        try {
            AllocationContext context = getAllocationContext();
            CompiledQuery query = sqlBuilder.buildCount(entityClass, criteria, context);
            return sqlExecutor.queryForObject(query, Long.class);
        } catch (Exception e) {
            throw new QueryExecutionException(
                String.format("Failed to count entities %s by criteria", entityClass.getSimpleName()), e);
        }
    }

    // ========== 命名查询 ==========

    @SuppressWarnings("unchecked")
    @Override
    public <R> R executeNamedStatement(String statementId, Map<String, Object> parameters) {
        Assert.hasText(statementId, "Statement ID must not be null or empty");
        Assert.notNull(parameters, "Parameters must not be null");
        return sqlExecutor.execute(statementId, parameters, entityClass);
    }

    /**
     * 非接口方法：Bean 参数版本（可选用）
     */
    public <R> R executeNamedStatement(String statementId, Object paramBean) {
        Assert.hasText(statementId, "Statement ID must not be null or empty");
        Assert.notNull(paramBean, "Parameter bean must not be null");
        if (paramBean instanceof SortableParam sortableParam) {
            Map<String, Object> map = ParamConvertUtil.toParamMap(paramBean);
            if (sortableParam.getSortingFields() != null && !sortableParam.getSortingFields().isEmpty()) {
                map.put(ORDER_BY_PARAM, convertSortingFieldsToOrderBy(sortableParam.getSortingFields()));
            }
            return executeNamedStatement(statementId, map);
        }
        throw new IllegalArgumentException("Parameter must be of type SortableParam");
    }

    @Override
    public <R> List<R> executeNamedStatement(String statementId, Map<String, Object> parameters, RowMapper<R> rowMapper) {
        Assert.hasText(statementId, "Statement ID must not be null or empty");
        Assert.notNull(parameters, "Parameters must not be null");
        Assert.notNull(rowMapper, "RowMapper must not be null");
        return sqlExecutor.execute(statementId, parameters, entityClass, rowMapper);
    }

    @Override
    public <R> PageResult<R> executePagedNamedStatement(String statementId,
                                                        Map<String, Object> parameters,
                                                        RowMapper<R> rowMapper,
                                                        int pageNumber,
                                                        int pageSize) {
        Assert.hasText(statementId, "Statement ID must not be null or empty");
        Assert.notNull(parameters, "Parameters must not be null");
        Assert.notNull(rowMapper, "RowMapper must not be null");
        Assert.isTrue(pageNumber >= 1, "Page number must be >= 1");
        Assert.isTrue(pageSize >= 1, "Page size must be >= 1");
        return sqlExecutor.executePaged(statementId, parameters, entityClass, rowMapper, pageNumber, pageSize);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <R> PageResult<R> executePagedNamedStatement(String statementId, Object paramBean) {
        Assert.hasText(statementId, "Statement ID must not be null or empty");
        Assert.notNull(paramBean, "Parameter bean must not be null");
        if (paramBean instanceof SortablePageParam pageParam) {
            Map<String, Object> map = ParamConvertUtil.toParamMap(paramBean);
            if (pageParam.getSortingFields() != null && !pageParam.getSortingFields().isEmpty()) {
                map.put(ORDER_BY_PARAM, convertSortingFieldsToOrderBy(pageParam.getSortingFields()));
            }
            PageResult<T> result = sqlExecutor.executePaged(
                    statementId, map, entityClass, pageParam.getPage(), pageParam.getSize());
            // 双重擦除：满足接口签名 <R>
            return (PageResult<R>) result;
        }
        throw new IllegalArgumentException("Parameter must be of type SortablePageParam");
    }

    @Override
    public List<Map<String, Object>> executeNamedStatementForMap(String statementId, Map<String, Object> parameters) {
        Assert.hasText(statementId, "Statement ID must not be null or empty");
        Assert.notNull(parameters, "Parameters must not be null");
        return sqlExecutor.executeForMap(statementId, parameters, entityClass);
    }

    // ========== 通用查询 ==========

    /**
     * 处理分页参数，设置默认值
     * @param pageNo 页码
     * @param pageSize 每页大小
     * @return 包含处理后页码和每页大小的数组 [pageNo, pageSize]
     */
    private int[] processPaginationParams(Integer pageNo, Integer pageSize) {
        int processedPageNo = (pageNo != null && pageNo > 0) ? pageNo : DEFAULT_PAGE_NUMBER;
        int processedPageSize = (pageSize != null && pageSize > 0) ? pageSize : DEFAULT_PAGE_SIZE;
        return new int[]{processedPageNo, processedPageSize};
    }





    @Override
    @Transactional(readOnly = true)
    public PageResult<T> queryByCondition(List<QueryParam> queryParams,
                                          List<SortingField> sortingFields,
                                          Integer pageNo,
                                          Integer pageSize,
                                          String bizIdentityCode) {
        Criteria<T> criteria = buildCriteria(queryParams, bizIdentityCode);
        addSortingToCriteria(criteria, sortingFields);
        
        int[] paginationParams = processPaginationParams(pageNo, pageSize);
        criteria.setPageNo(paginationParams[0]);
        criteria.setPageSize(paginationParams[1]);
        
        return pageByCriteria(criteria);
    }

    @Override
    @Transactional(readOnly = true)
    public List<T> query(Query queryParam) {
        if (queryParam == null) return Collections.emptyList();

        // 使用通用转换器将Query对象转换为Criteria
        Criteria<T> criteria = QueryObjectConverter.convert(queryParam, entityClass);
        return findByCriteria(criteria);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<T> queryPage(PageParam pageParam) {
        Assert.notNull(pageParam, "Page parameter must not be null");

        // 使用通用转换器将PageParam对象转换为Criteria
        Criteria<T> criteria = QueryObjectConverter.convert(pageParam, entityClass);
        return pageByCriteria(criteria);
    }

    // ========== 聚合 ==========

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aggregate(List<String> aggregations, Criteria<T> criteria, List<String> groupBy) {
        return aggregate(aggregations, criteria, groupBy, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aggregate(List<String> aggregations,
                                               Criteria<T> criteria,
                                               List<String> groupBy,
                                               List<String> having) {
        validateAggregations(aggregations);
        CompiledQuery query = sqlBuilder.buildAggregation(entityClass, aggregations, criteria, groupBy, having);
        return sqlExecutor.executeQueryForMap(query);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> aggregate(List<String> aggregations, Criteria<T> criteria) {
        List<Map<String, Object>> results = aggregate(aggregations, criteria, null, null);
        return results.isEmpty() ? Collections.emptyMap() : results.get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> aggregateWithPagination(
            List<String> aggregations,
            Criteria<T> criteria,
            List<String> groupBy,
            List<String> having,
            int pageNumber,
            int pageSize) {
        validateAggregations(aggregations);

        Long total;
        if (groupBy == null || groupBy.isEmpty()) {
            // 无分组情况：使用简单的计数查询
            total = countByCriteria(criteria);
        } else {
            // 有分组情况：使用专用计数构建器
            total = countGroupByResultsWithHaving(criteria, groupBy, having);
        }

        // 获取当前页数据
        criteria.setPageNo(pageNumber);
        criteria.setPageSize(pageSize);
        List<Map<String, Object>> content = aggregate(aggregations, criteria, groupBy, null);

        return PageResult.of(content, total, pageNumber, pageSize);
    }

    /**
     * 使用专用计数构建器计算带HAVING条件的分组结果总数
     */
    private Long countGroupByResultsWithHaving(Criteria<T> criteria, List<String> groupBy, List<String> having) {
        CompiledQuery countQuery = sqlBuilder.buildCountAggregation(
                entityClass, criteria, groupBy, having);
        return sqlExecutor.queryForObject(countQuery, Long.class);
    }

    // ========== 辅助方法 ==========

    private void ensureIdInitialized(T entity) {
        TableMetadata tableMetadata = TableMetadataResolver.load(entityClass);
        ColumnMetadata primaryKey = tableMetadata.getPrimaryKey();
        GenerationStrategy strategy = primaryKey.getGenerationStrategy();
        if (entity.getId() == null && strategy != GenerationStrategy.IDENTITY) {
            Object rawId = sqlExecutor.generateId(strategy, entity);
            setEntityId(entity, rawId);
        }
    }

    @SuppressWarnings("unchecked")
    private void setEntityId(T entity, Object rawId) {
        try {
            if (rawId == null) return;
            TableMetadata tableMetadata = TableMetadataResolver.load(entityClass);
            Class<?> targetType = tableMetadata.getPrimaryKey().getType();
            Object converted = convertToTargetType(rawId, targetType);
            entity.setId((ID) converted);
        } catch (Exception e) {
            throw new PersistenceException("ID 字段赋值失败: " + entityClass.getSimpleName(), e);
        }
    }

    private Object convertToTargetType(Object rawId, Class<?> targetType) {
        try {
            // 使用TypeConverter进行类型转换，提供统一的类型转换机制
            return TypeConverter.convert(rawId, targetType);
        } catch (Exception e) {
            throw new IllegalArgumentException("ID类型转换失败: "
                    + rawId.getClass().getSimpleName() + " → " + targetType.getSimpleName(), e);
        }
    }

    private AllocationContext getAllocationContext() {
        String bizIdentityCode = "pukang";
        if (BizIdentityContext.getBizIdentityCode() != null) {
            bizIdentityCode = BizIdentityContext.getBizIdentityCode();
        }
        return AllocationContext.of(
                TenantContext.getTenantId(),
                MetadataSdkContext.getAppCode(),
                bizIdentityCode,
                entityClass.getSimpleName()
        );
    }

    private Criteria<T> buildCriteria(List<QueryParam> queryParams, String bizIdentityCode) {
        Criteria<T> criteria = Criteria.create();

        // 只有当实体类包含bizIdentityCode字段时才添加这个条件
        if (StringUtils.hasText(bizIdentityCode) && cachedFieldNames.contains("bizIdentityCode")) {
            criteria.eq("bizIdentityCode", bizIdentityCode);
        }

        if (queryParams != null) {
            for (QueryParam param : queryParams) {
                String fieldName = param.getField();
                if (!cachedFieldNames.contains(fieldName)) {
                    throw new IllegalArgumentException("无效的字段名: " + fieldName);
                }
                Object value = param.getValue();
                Operator operator = param.getType();

                if (StringUtils.hasText(fieldName) && value != null) {
                    switch (operator) {
                        case LIKE:
                        case FULL_LIKE:
                            criteria.like(fieldName, value.toString());
                            break;
                        case GT:
                            criteria.gt(fieldName, value);
                            break;
                        case LT:
                            criteria.lt(fieldName, value);
                            break;
                        case IN:
                            if (!(value instanceof List<?>)) {
                                throw new MetadataException("IN 运算符需要一个值列表");
                            }
                            criteria.in(fieldName, value);
                            break;
                        case BETWEEN:
                            if (!(value instanceof List<?> range) || range.size() != 2) {
                                throw new MetadataException("BETWEEN 运算符需要两个值的列表");
                            }
                            criteria.between(fieldName, range.get(0), range.get(1));
                            break;
                        case EQ:
                        default:
                            criteria.eq(fieldName, value);
                            break;
                    }
                }
            }
        }
        return criteria;
    }

    private void addSortingToCriteria(Criteria<T> criteria, List<SortingField> sortingFields) {
        if (sortingFields == null || sortingFields.isEmpty()) return;
        for (SortingField field : sortingFields) {
            String fieldName = field.getField();
            if (!cachedFieldNames.contains(fieldName)) {
                throw new IllegalArgumentException("无效的排序字段: " + fieldName);
            }
            try {
                SortDirection direction = SortDirection.valueOf(field.getOrder().toUpperCase());
                criteria.addSort(fieldName, direction);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("无效的排序方向: " + field.getOrder(), e);
            }
        }
    }

    private String convertSortingFieldsToOrderBy(List<SortingField> sortingFields) {
        return sortingFields.stream()
                .map(f -> validateColumnName(f.getField()) + " " + validateOrderDirection(f.getOrder()))
                .collect(Collectors.joining(", "));
    }

    private String validateColumnName(String column) {
        if (column == null || !column.matches("^[a-zA-Z0-9_\\.]+$")) {
            throw new IllegalArgumentException("Invalid column name: " + column);
        }
        return column;
    }

    private String validateOrderDirection(String direction) {
        if ("asc".equalsIgnoreCase(direction) || "desc".equalsIgnoreCase(direction)) {
            return direction.toUpperCase();
        }
        throw new IllegalArgumentException("Invalid order direction: " + direction);
    }

    private void saveExtensionFields(T entity) {
        if (entity instanceof Extensible extensible && entity.getId() != null) {
            Map<String, Object> extra = extensible.getExtraProperties();
            if (extra != null && !extra.isEmpty()) {
                ExtensionContext context = ExtensionContext.of(
                        TenantContext.getTenantId(),
                        MetadataSdkContext.getAppCode(),
                        extensible.getBizIdentityCode(),
                        entityClass.getSimpleName(),
                        entity.getId(),
                        extra);
                extensionCoordinator.save(context);
            }
        }
    }

    private void loadExtensionFields(T entity) {
        if (entity instanceof Extensible extensible && entity.getId() != null) {
            ExtensionContext context = ExtensionContext.of(
                    TenantContext.getTenantId(),
                    MetadataSdkContext.getAppCode(),
                    extensible.getBizIdentityCode(),
                    entityClass.getSimpleName(),
                    entity.getId());
            extensible.mergeExtraProperties(extensionCoordinator.load(context));
        }
    }

    protected <E> List<List<E>> partition(List<E> list, int size) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        return IntStream.iterate(0, i -> i < list.size(), i -> i + size)
                .mapToObj(i -> list.subList(i, Math.min(i + size, list.size())))
                .collect(Collectors.toList());
    }


    // ========== 校验聚合表达式（防空/字符白名单） ==========

    private void validateAggregations(List<String> aggregations) {
        Assert.notNull(aggregations, "Aggregations must not be null");
        Assert.isTrue(!aggregations.isEmpty(), "Aggregations list must not be empty");
        for (String agg : aggregations) {
            if (!StringUtils.hasText(agg)) {
                throw new IllegalArgumentException("Aggregation expression must not be blank");
            }
            // 允许: 字母数字/_/*/空白/,/()/+/-//./引号 以及可选 AS
            if (!agg.matches("^[A-Za-z0-9_\\*\\s,()\\+\\-\\/\\.\"']+(?i)(\\s+AS\\s+[A-Za-z0-9_\"']+)?$")) {
                throw new IllegalArgumentException("Illegal characters in aggregation expression: " + agg);
            }
        }
    }
}