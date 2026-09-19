package com.bone.metadata.sdk;

import com.bone.core.domain.entity.Entity;
import com.bone.core.domain.extension.Extensible;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.enums.Operator;
import com.bone.core.model.*;
import com.bone.core.tenant.context.BizIdentityContext;
import com.bone.core.tenant.context.TenantContext;
import com.bone.core.util.ReflectionUtil;
import com.bone.metadata.sdk.domain.enums.SortDirection;
import com.bone.metadata.sdk.domain.exception.MetadataException;
import com.bone.metadata.sdk.domain.exception.MissingTenantContextException;
import com.bone.metadata.sdk.domain.exception.MultipleResultsException;
import com.bone.metadata.sdk.domain.exception.OptimisticLockingFailureException;
import com.bone.metadata.sdk.domain.exception.PersistenceException;
import com.bone.metadata.sdk.domain.exception.UndefinedFieldException;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.ColumnMetadata;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.BatchCompiledQuery;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.domain.spec.TableMetadataResolver;
import com.bone.metadata.sdk.extension.ExtensionContext;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.query.builder.DeleteBuilder;
import com.bone.metadata.sdk.query.context.DeleteContext;
import com.bone.metadata.sdk.query.converter.QueryObjectConverter;
import com.bone.metadata.sdk.query.criteria.Condition;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.metadata.sdk.sql.executor.TypeConverter;
import com.bone.metadata.sdk.support.cache.FieldCache;
import com.bone.metadata.sdk.support.config.MetadataSdkContext;
import jakarta.validation.Valid;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
public abstract class BaseRepository<T extends Entity<ID>, ID> implements Repository<T, ID> {

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
  public BaseRepository(
      SqlBuilder sqlBuilder,
      SqlExecutor sqlExecutor,
      Class<T> entityClass,
      ExtensionCoordinator extensionCoordinator) {
    this.sqlBuilder = Objects.requireNonNull(sqlBuilder, "SQL builder must not be null");
    this.sqlExecutor = Objects.requireNonNull(sqlExecutor, "SQL executor must not be null");
    this.entityClass = Objects.requireNonNull(entityClass, "Entity class must not be null");
    this.extensionCoordinator =
        Objects.requireNonNull(extensionCoordinator, "ExtensionCoordinator must not be null");
    this.cachedFieldNames =
        Collections.unmodifiableSet(FieldCache.getCachedFields(entityClass).keySet());
  }

  @Override
  public SqlExecutor getSqlExecutor() {
    return this.sqlExecutor;
  }

  @Override
  public Class<T> getEntityClass() {
    return this.entityClass;
  }

  // ========== 基础 CRUD ==========

  @Override
  @Transactional(readOnly = true)
  public T findById(ID id) {
    Assert.notNull(id, "ID must not be null");
    TableMetadata tableMetadata = TableMetadataResolver.load(entityClass);
    Criteria<T> criteria =
        Criteria.<T>create()
            .entityClass(entityClass)
            .eq(tableMetadata.getPrimaryKey().getName(), id);
    // 默认不包含已删除记录
    CompiledQuery query = sqlBuilder.buildSelect(entityClass, criteria, false);
    T entity = sqlExecutor.querySingle(query, entityClass);
    if (entity != null) loadExtensionFields(entity);
    return entity;
  }

  @Override
  @Transactional(readOnly = true)
  public List<T> findByIds(List<ID> idList) {
    if (idList == null || idList.isEmpty()) return Collections.emptyList();
    Assert.noNullElements(idList, "ID list must not contain null elements");
    TableMetadata tableMetadata = TableMetadataResolver.load(entityClass);
    Criteria<T> criteria =
        Criteria.<T>create()
            .entityClass(entityClass)
            .in(tableMetadata.getPrimaryKey().getName(), idList);
    // 默认不包含已删除记录
    CompiledQuery query = sqlBuilder.buildSelect(entityClass, criteria, false);
    List<T> list = sqlExecutor.query(query, entityClass);
    list.forEach(this::loadExtensionFields);
    return list;
  }

  @Override
  @Transactional(readOnly = true)
  public List<T> findByIdsIncludingDeleted(List<ID> idList) {
    if (idList == null || idList.isEmpty()) return Collections.emptyList();
    Assert.noNullElements(idList, "ID list must not contain null elements");
    TableMetadata tableMetadata = TableMetadataResolver.load(entityClass);
    Criteria<T> criteria =
        Criteria.<T>create()
            .entityClass(entityClass)
            .in(tableMetadata.getPrimaryKey().getName(), idList);
    CompiledQuery query = sqlBuilder.buildSelect(entityClass, criteria, true);
    List<T> list = sqlExecutor.query(query, entityClass);
    list.forEach(this::loadExtensionFields);
    return list;
  }

  @Override
  @Transactional(readOnly = true)
  public T findByIdIncludingDeleted(ID id) {
    Assert.notNull(id, "ID must not be null");
    TableMetadata tableMetadata = TableMetadataResolver.load(entityClass);
    Criteria<T> criteria =
        Criteria.<T>create()
            .entityClass(entityClass)
            .eq(tableMetadata.getPrimaryKey().getName(), id);
    CompiledQuery query = sqlBuilder.buildSelect(entityClass, criteria, true);
    T entity = sqlExecutor.querySingle(query, entityClass);
    if (entity != null) loadExtensionFields(entity);
    return entity;
  }

  @Override
  @Transactional
  public ID insert(@Valid T entity) {
    Assert.notNull(entity, "Entity must not be null");

    TableMetadata tableMetadata = TableMetadataResolver.load(entityClass);
    ColumnMetadata primaryKey = tableMetadata.getPrimaryKey();
    GenerationStrategy strategy = primaryKey.getGenerationStrategy();

    if (strategy != GenerationStrategy.IDENTITY) {
      // 非自增：仅在 id 为空时生成（ADR-0019：尊重调用方预分配的非空 id，
      // 与 batchInsert()/ensureIdInitialized() 语义一致——空才生成，非空则尊重）。
      // 调用方对预置 id 的唯一性负责。
      if (entity.getId() == null) {
        Object generatedId = sqlExecutor.generateId(strategy, entity);
        setEntityId(entity, generatedId);
      }
      BatchCompiledQuery batch =
          sqlBuilder.buildBatchInsert(entityClass, Collections.singletonList(entity));
      sqlExecutor.batchUpdate(batch);
    } else {
      // 自增：将 BatchCompiledQuery 收敛为单条 CompiledQuery 再取回主键
      BatchCompiledQuery batch =
          sqlBuilder.buildBatchInsert(entityClass, Collections.singletonList(entity));
      Map<String, Object> firstParams = batch.getBatchParameters().get(0);
      CompiledQuery singleInsert = new CompiledQuery(batch.getSql(), firstParams);
      Object newId = sqlExecutor.insert(singleInsert, entityClass);
      setEntityId(entity, newId);
    }
    // 新插入行 version 应为 0：DB 有 DEFAULT 0，但 insert 显式给列；实体原本为 null 时与库保持一致（ADR-0031 D1）
    // 按字段声明类型写入（ADR-0031 D1 评审：兼容 Integer/int 等）
    if (tableMetadata.isVersioned()) {
      String vf = tableMetadata.getVersion().getFieldName();
      if (ReflectionUtil.getFieldValue(entity, vf) == null) {
        ReflectionUtil.setFieldValue(
            entity, vf, toVersionValue(tableMetadata.getVersion().getType(), 0L));
      }
    }
    saveExtensionFields(entity);
    return entity.getId();
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
  public void batchInsert(List<T> entities) {
    if (entities == null || entities.isEmpty()) return;
    Assert.noNullElements(entities, "Entities list must not contain null elements");

    TableMetadata tableMetadata = TableMetadataResolver.load(entityClass);
    GenerationStrategy strategy = tableMetadata.getPrimaryKey().getGenerationStrategy();

    if (strategy != GenerationStrategy.IDENTITY) {
      entities.forEach(
          e -> {
            if (e.getId() == null) {
              Object generatedId = sqlExecutor.generateId(strategy, e);
              setEntityId(e, generatedId);
            }
          });
    }

    partition(entities, maxBatchSize)
        .forEach(
            batch -> {
              sqlExecutor.batchUpdate(sqlBuilder.buildBatchInsert(entityClass, batch));
              batch.forEach(
                  e -> {
                    // 批量插入的 versioned 实体同样回写 0（与单条 insert 对称，否则同实例再 update 会误判冲突）
                    if (tableMetadata.isVersioned()
                        && ReflectionUtil.getFieldValue(
                                e, tableMetadata.getVersion().getFieldName())
                            == null) {
                      ReflectionUtil.setFieldValue(
                          e,
                          tableMetadata.getVersion().getFieldName(),
                          toVersionValue(tableMetadata.getVersion().getType(), 0L));
                    }
                    this.saveExtensionFields(e);
                  });
            });
  }

  /**
   * 按 {@code @Version} 字段声明类型，把增长后的值转成可写入对象的数值。
   *
   * <p>{@link TypeConverter} 不支持 Long→Integer 之类的窄化，而 version 递增通常落在 {Long, Integer, int, short}
   * 上，故按类型直接强转；其余数值类型（如 BigInteger）退化为 {@link TypeConverter}。
   */
  private static Number toVersionValue(Class<?> type, long value) {
    if (type == long.class || type == Long.class) {
      return value;
    }
    if (type == int.class || type == Integer.class) {
      return (int) value;
    }
    if (type == short.class || type == Short.class) {
      return (short) value;
    }
    if (type == byte.class || type == Byte.class) {
      return (byte) value;
    }
    return (Number) TypeConverter.convert(value, type);
  }

  @Override
  @Transactional
  public boolean update(@Valid T entity) {
    Assert.notNull(entity, "Entity must not be null");
    Assert.notNull(entity.getId(), "Entity ID must not be null for update");
    TableMetadata tableMetadata = TableMetadataResolver.load(entityClass);
    CompiledQuery query = sqlBuilder.buildDynamicUpdate(entityClass, entity);
    int affectedRows = sqlExecutor.update(query);
    saveExtensionFields(entity);
    if (affectedRows == 0) {
      // 原生乐观锁（ADR-0031 D1）：version 表 0 行 = 冲突/不存在/越租户 → 响亮失败，不静默返回 false
      if (tableMetadata.isVersioned()) {
        ColumnMetadata vc = tableMetadata.getVersion();
        Object oldVersion = ReflectionUtil.getFieldValue(entity, vc.getFieldName());
        if (oldVersion == null) {
          // 实体未加载 @Version（SELECT 已含 version 列），不应被误报成"并发冲突"
          throw new IllegalStateException(
              "Optimistic lock cannot proceed: @Version field is null on "
                  + entityClass.getSimpleName()
                  + " id="
                  + entity.getId()
                  + ". Load the entity first (SELECT includes version) before update.");
        }
        throw new OptimisticLockingFailureException(
            entityClass.getSimpleName(), entity.getId(), oldVersion);
      }
      log.warn("No rows updated for entity id={}", entity.getId());
      return false;
    }
    // 成功后回写实体 version = old + 1：否则同一实体连续两次写必然假冲突
    // 按字段声明类型写入，兼容 Integer/int/short 等（不只 Long）
    if (tableMetadata.isVersioned()) {
      ColumnMetadata vc = tableMetadata.getVersion();
      Object oldVersion = ReflectionUtil.getFieldValue(entity, vc.getFieldName());
      long base = (oldVersion instanceof Number) ? ((Number) oldVersion).longValue() : 0L;
      ReflectionUtil.setFieldValue(
          entity, vc.getFieldName(), toVersionValue(vc.getType(), base + 1));
    }
    return true;
  }

  @Override
  @Transactional
  public int updateByCriteria(@Valid T entity, Criteria<T> criteria) {
    Assert.notNull(entity, "Entity must not be null");
    Assert.notNull(criteria, "Criteria must not be null");
    CompiledQuery query = sqlBuilder.buildConditionalUpdate(entityClass, entity, criteria);
    int affectedRows = sqlExecutor.update(query);
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

  @Override
  @Transactional
  public void batchSave(List<T> entities) {
    if (entities == null || entities.isEmpty()) return;
    Assert.noNullElements(entities, "Entities list must not contain null elements");

    // 优化：使用并行流处理大数据量
    Map<Boolean, List<T>> partitionedEntities =
        entities.parallelStream().collect(Collectors.partitioningBy(e -> e.getId() == null));

    // 分离需要插入和需要检查更新的实体
    List<T> toInsert = partitionedEntities.get(true);
    List<T> maybeToUpdate = partitionedEntities.get(false);

    // 批量插入新实体
    if (!toInsert.isEmpty()) {
      batchInsert(toInsert);
    }

    // 优化更新逻辑：减少数据库查询
    if (!maybeToUpdate.isEmpty()) {
      // 分批处理大量实体，避免内存溢出
      partition(maybeToUpdate, Math.min(maxBatchSize, 1000))
          .forEach(
              batch -> {
                List<ID> ids = batch.stream().map(Entity::getId).collect(Collectors.toList());
                Set<ID> existingIds =
                    findByIds(ids).stream().map(Entity::getId).collect(Collectors.toSet());

                List<T> toUpdate = new ArrayList<>();
                List<T> toInsertFromUpdate = new ArrayList<>();

                // 分离真正需要更新和实际需要插入的实体
                batch.forEach(
                    e -> {
                      if (existingIds.contains(e.getId())) {
                        toUpdate.add(e);
                      } else {
                        // ID不为null但数据库中不存在，需要插入
                        toInsertFromUpdate.add(e);
                      }
                    });

                if (!toUpdate.isEmpty()) {
                  batchUpdate(toUpdate);
                }
                if (!toInsertFromUpdate.isEmpty()) {
                  batchInsert(toInsertFromUpdate);
                }
              });
    }
  }

  private void batchUpdate(List<T> entities) {
    if (entities == null || entities.isEmpty()) return;

    // 优化：根据实体大小动态调整批次大小
    int optimalBatchSize = calculateOptimalBatchSize(entities);

    // 使用并行流处理批次（如果实体数量足够多）
    if (entities.size() > optimalBatchSize * 2) {
      entities.parallelStream()
          .collect(
              Collectors.groupingBy(e -> e.hashCode() % (entities.size() / optimalBatchSize + 1)))
          .values()
          .forEach(
              batch -> {
                BatchCompiledQuery query = sqlBuilder.buildBatchUpdate(entityClass, batch);
                sqlExecutor.batchUpdate(query);
                batch.forEach(this::saveExtensionFields);
              });
    } else {
      partition(entities, optimalBatchSize)
          .forEach(
              batch -> {
                BatchCompiledQuery query = sqlBuilder.buildBatchUpdate(entityClass, batch);
                sqlExecutor.batchUpdate(query);
                batch.forEach(this::saveExtensionFields);
              });
    }
  }

  /** 根据实体大小和数量计算最佳批次大小 */
  private int calculateOptimalBatchSize(List<T> entities) {
    // 基础批次大小
    int baseBatchSize = maxBatchSize;

    // 对于大量小实体，可以使用更大的批次
    if (entities.size() > 10000 && entities.get(0) != null) {
      baseBatchSize = Math.min(maxBatchSize * 2, 5000);
    }

    // 对于少量大实体，使用更小的批次
    if (entities.size() < 100 && entities.get(0) != null) {
      baseBatchSize = Math.max(baseBatchSize / 2, 100);
    }

    return baseBatchSize;
  }

  @Override
  @Transactional
  public boolean deleteById(ID id) {
    Assert.notNull(id, "ID must not be null");
    TableMetadata tableMetadata = TableMetadataResolver.load(entityClass);
    String tableName = tableMetadata.getName();
    String primaryKey = tableMetadata.getPrimaryKey().getName();

    // 租户护栏（ADR-0029）：租户表须从可信 TenantContext 注入 tenant_id
    String tenantGuard = "";
    Object[] execParams = new Object[] {id};
    if (tableMetadata.isTenantScoped()) {
      Long tid = TenantContext.getTenantIdAsLong();
      if (tid == null) {
        throw new MissingTenantContextException(tableName);
      }
      tenantGuard = " AND " + tableMetadata.getTenantIdColumn().getName() + " = :p1";
      execParams = new Object[] {id, tid};
    }

    // 检查是否支持软删除
    if (tableMetadata.isSoftDeletable()) {
      // 执行软删除：将deleted字段设置为true
      int affectedRows =
          sqlExecutor.delete(
              "UPDATE "
                  + tableName
                  + " SET deleted = true WHERE "
                  + primaryKey
                  + " = :p0"
                  + tenantGuard,
              execParams);
      return affectedRows > 0;
    } else {
      // 使用测试专用的delete方法，跳过防注入检查
      // delete方法将参数绑定为:p0格式
      int affectedRows =
          sqlExecutor.delete(
              "DELETE FROM " + tableName + " WHERE " + primaryKey + " = :p0" + tenantGuard,
              execParams);
      return affectedRows > 0;
    }
  }

  @Override
  @Transactional
  public void deleteByIds(List<ID> ids) {
    if (ids == null || ids.isEmpty()) return;
    Assert.noNullElements(ids, "ID list must not contain null elements");
    TableMetadata tableMetadata = TableMetadataResolver.load(entityClass);
    String tableName = tableMetadata.getName();
    String primaryKey = tableMetadata.getPrimaryKey().getName();

    // 租户护栏（ADR-0029）：租户表须从可信 TenantContext 注入 tenant_id
    String tenantGuard = "";
    Object[] execParams = new Object[] {ids};
    if (tableMetadata.isTenantScoped()) {
      Long tid = TenantContext.getTenantIdAsLong();
      if (tid == null) {
        throw new MissingTenantContextException(tableName);
      }
      tenantGuard = " AND " + tableMetadata.getTenantIdColumn().getName() + " = :p1";
      execParams = new Object[] {ids, tid};
    }

    // 检查是否支持软删除
    if (tableMetadata.isSoftDeletable()) {
      // 执行软删除：将deleted字段设置为true
      sqlExecutor.delete(
          "UPDATE "
              + tableName
              + " SET deleted = true WHERE "
              + primaryKey
              + " IN (:p0)"
              + tenantGuard,
          execParams);
    } else {
      // 使用测试专用的delete方法，跳过防注入检查
      // delete方法将参数绑定为:p0格式
      sqlExecutor.delete(
          "DELETE FROM " + tableName + " WHERE " + primaryKey + " IN (:p0)" + tenantGuard,
          execParams);
    }
  }

  @Override
  @Transactional
  public int deleteByCriteria(Criteria<T> criteria) {
    Assert.notNull(criteria, "Criteria must not be null");
    validateCriteriaFields(criteria);
    AllocationContext context = getAllocationContext();
    TableMetadata tableMetadata = TableMetadataResolver.load(entityClass);

    DeleteContext deleteContext = new DeleteContext(tableMetadata, criteria, context);
    DeleteBuilder deleteBuilder = new DeleteBuilder();
    CompiledQuery query = deleteBuilder.build(deleteContext);
    return sqlExecutor.update(query);
  }

  // ========== 条件查询 / 统计 ==========
  protected void validateCriteriaFields(Criteria<T> criteria) {
    for (Condition condition : criteria.getMainConditions()) {
      if (FieldCache.getFieldByName(entityClass, condition.getFieldName()) == null) {
        throw new UndefinedFieldException(
            String.format(
                "Field '%s' is not defined in entity %s",
                condition.getFieldName(), entityClass.getSimpleName()));
      }
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<T> findByCriteria(Criteria<T> criteria) {
    Assert.notNull(criteria, "Criteria must not be null");
    validateCriteriaFields(criteria);
    AllocationContext context = getAllocationContext();
    CompiledQuery query = sqlBuilder.buildSelect(entityClass, criteria, context);
    List<T> list = sqlExecutor.query(query, entityClass);
    list.forEach(this::loadExtensionFields);
    return list;
  }

  @Override
  @Transactional(readOnly = true)
  public T findOneByCriteria(Criteria<T> criteria) throws MultipleResultsException {
    Assert.notNull(criteria, "Criteria must not be null");
    validateCriteriaFields(criteria);
    AllocationContext context = getAllocationContext();
    CompiledQuery query = sqlBuilder.buildSelect(entityClass, criteria, context);
    List<T> results = sqlExecutor.query(query, entityClass);
    if (results.size() > 1) {
      throw new MultipleResultsException("Expected single result, found: " + results.size());
    }
    T entity = results.stream().findFirst().orElse(null);
    if (entity != null) loadExtensionFields(entity);
    return entity;
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<T> pageByCriteria(Criteria<T> criteria) {
    Assert.notNull(criteria, "Criteria must not be null");
    validateCriteriaFields(criteria);

    // 验证分页参数
    Integer pageNoObj = criteria.getPageNo();
    Integer pageSizeObj = criteria.getPageSize();
    int pageNo = Math.max(1, pageNoObj != null ? pageNoObj : DEFAULT_PAGE_NUMBER);
    int pageSize =
        Math.min(
            Math.max(1, pageSizeObj != null ? pageSizeObj : DEFAULT_PAGE_SIZE),
            MAX_PAGINATION_THRESHOLD);

    if (pageNo > MAX_PAGINATION_THRESHOLD) {
      log.warn("Large page number: {} (consider cursor pagination).", pageNo);
    }

    // 保存原始参数
    Integer originalPageNumber = criteria.getPageNo();
    Integer originalPageSize = criteria.getPageSize();

    // 优化：对于大数据集，使用延迟计数
    Long total;
    List<T> content;

    // 快速路径：对于第一页且数据量不大时，可以先查询数据再决定是否需要精确计数
    if (pageNo == 1 && pageSize <= 100) {
      // 先查询数据
      criteria.setPageNo(pageNo);
      criteria.setPageSize(pageSize);
      CompiledQuery query = sqlBuilder.buildSelect(entityClass, criteria);
      content = sqlExecutor.query(query, entityClass);

      // 如果结果少于请求的页数，说明就是总数
      if (content.size() < pageSize) {
        total = (long) content.size();
      } else {
        // 否则需要精确计数
        try {
          total = countByCriteria(criteria);
        } finally {
          criteria.setPageNo(originalPageNumber);
          criteria.setPageSize(originalPageSize);
        }
      }
    } else {
      // 标准路径：先查询总数
      try {
        total = countByCriteria(criteria);
      } finally {
        criteria.setPageNo(originalPageNumber);
        criteria.setPageSize(originalPageSize);
      }

      // 如果总数为0，直接返回空结果
      if (total == 0) {
        return PageResult.of(Collections.emptyList(), total, (Integer) pageNo, (Integer) pageSize);
      }

      // 查询数据
      criteria.setPageNo(pageNo);
      criteria.setPageSize(pageSize);
      CompiledQuery query = sqlBuilder.buildSelect(entityClass, criteria);
      content = sqlExecutor.query(query, entityClass);
    }

    // 并行加载扩展字段（如果有足够多的实体）
    if (!content.isEmpty() && content.size() > 20) {
      content.parallelStream().forEach(this::loadExtensionFields);
    } else {
      content.forEach(this::loadExtensionFields);
    }

    return PageResult.of(content, total, (Integer) pageNo, (Integer) pageSize);
  }

  @Override
  @Transactional(readOnly = true)
  public Long countByCriteria(Criteria<T> criteria) {
    Assert.notNull(criteria, "Criteria must not be null");
    AllocationContext context = getAllocationContext();
    CompiledQuery query = sqlBuilder.buildCount(entityClass, criteria, context);
    return sqlExecutor.queryForObject(query, Long.class);
  }

  // ========== 通用查询 ==========

  @Override
  @Transactional(readOnly = true)
  public PageResult<T> queryByCondition(
      List<QueryParam> queryParams,
      List<SortingField> sortingFields,
      Integer pageNo,
      Integer pageSize,
      String bizIdentityCode) {
    List<QueryParam> processedQueryParams =
        (queryParams != null) ? queryParams : Collections.emptyList();
    List<SortingField> processedSortingFields =
        (sortingFields != null) ? sortingFields : Collections.emptyList();
    Criteria<T> criteria = buildCriteria(processedQueryParams, bizIdentityCode);
    addSortingToCriteria(criteria, processedSortingFields);
    int processedPageNo = (pageNo != null && pageNo > 0) ? pageNo : DEFAULT_PAGE_NUMBER;
    int processedPageSize = (pageSize != null && pageSize > 0) ? pageSize : DEFAULT_PAGE_SIZE;
    criteria.setPageNo(processedPageNo);
    criteria.setPageSize(processedPageSize);
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
    // 添加调试信息
    if (log.isDebugEnabled()) {
      log.debug("QueryPage - Criteria conditions: {}", criteria.getMainConditions());
      log.debug(
          "QueryPage - Page params: pageNo={}, pageSize={}",
          criteria.getPageNo(),
          criteria.getPageSize());
    }
    return pageByCriteria(criteria);
  }

  // ========== 聚合 ==========

  @Override
  @Transactional(readOnly = true)
  public List<Map<String, Object>> aggregate(
      List<String> aggregations, Criteria<T> criteria, List<String> groupBy) {
    return aggregate(aggregations, criteria, groupBy, null);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Map<String, Object>> aggregate(
      List<String> aggregations, Criteria<T> criteria, List<String> groupBy, List<String> having) {
    validateAggregations(aggregations);
    CompiledQuery query =
        sqlBuilder.buildAggregation(entityClass, aggregations, criteria, groupBy, having);
    return sqlExecutor.queryForMap(query);
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

  /** 使用专用计数构建器计算带HAVING条件的分组结果总数 */
  private Long countGroupByResultsWithHaving(
      Criteria<T> criteria, List<String> groupBy, List<String> having) {
    CompiledQuery countQuery =
        sqlBuilder.buildCountAggregation(entityClass, criteria, groupBy, having);
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
      ColumnMetadata primaryKey = tableMetadata.getPrimaryKey();
      Class<?> targetType = primaryKey.getType();
      Object converted = TypeConverter.convert(rawId, targetType);
      // 使用反射直接设置主键字段，避免子类重新声明 id 字段时
      // entity.setId() 仅设置父类 Entity.id 而非子类 id 的问题
      ReflectionUtil.setFieldValue(entity, primaryKey.getFieldName(), converted);
    } catch (Exception e) {
      throw new PersistenceException("ID 字段赋值失败: " + entityClass.getSimpleName(), e);
    }
  }

  private AllocationContext getAllocationContext() {
    String bizIdentityCode = "bone";
    if (BizIdentityContext.getBizIdentityCode() != null) {
      bizIdentityCode = BizIdentityContext.getBizIdentityCode();
    }
    return AllocationContext.of(
        TenantContext.getTenantIdAsLong(),
        MetadataSdkContext.getAppCode(),
        bizIdentityCode,
        entityClass.getSimpleName());
  }

  private Criteria<T> buildCriteria(List<QueryParam> queryParams, String bizIdentityCode) {
    Criteria<T> criteria = Criteria.<T>create().entityClass(entityClass);

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
              criteria.in(fieldName, (List<?>) value);
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
        ExtensionContext context =
            ExtensionContext.of(
                TenantContext.getTenantIdAsLong(),
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
      ExtensionContext context =
          ExtensionContext.of(
              TenantContext.getTenantIdAsLong(),
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
      if (!agg.matches(
          "^[A-Za-z0-9_\\*\\s,()\\+\\-\\/\\.\"']+(?i)(\\s+AS\\s+[A-Za-z0-9_\"']+)?$")) {
        throw new IllegalArgumentException("Illegal characters in aggregation expression: " + agg);
      }
    }
  }
}
