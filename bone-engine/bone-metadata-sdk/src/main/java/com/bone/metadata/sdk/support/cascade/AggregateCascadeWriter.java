package com.bone.metadata.sdk.support.cascade;

import com.bone.core.domain.entity.Entity;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.tenant.TenantContext;
import com.bone.core.util.ReflectionUtil;
import com.bone.metadata.sdk.domain.exception.MissingTenantContextException;
import com.bone.metadata.sdk.domain.model.CascadeRelation;
import com.bone.metadata.sdk.domain.model.ColumnMetadata;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.BatchCompiledQuery;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.domain.spec.TableMetadataResolver;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.metadata.sdk.sql.executor.TypeConverter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.util.Assert;

/**
 * 聚合级联写：根落盘后同步子集合（insert/update + 更新时孤儿清除）。
 *
 * <p>不负责读回填（明确不做）；集合字段保持 {@code @Transient}。批量写路径不级联。
 */
public final class AggregateCascadeWriter {

  private final SqlBuilder sqlBuilder;
  private final SqlExecutor sqlExecutor;

  public AggregateCascadeWriter(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor) {
    this.sqlBuilder = Objects.requireNonNull(sqlBuilder, "sqlBuilder");
    this.sqlExecutor = Objects.requireNonNull(sqlExecutor, "sqlExecutor");
  }

  public void persistChildren(Object root, boolean removeOrphans) {
    Assert.notNull(root, "root must not be null");
    Class<?> rootClass = root.getClass();
    TableMetadata rootMeta = TableMetadataResolver.load(rootClass);
    List<CascadeRelation> cascades = rootMeta.getCascades();
    if (cascades.isEmpty()) {
      return;
    }
    Object rootId = ((Entity<?>) root).getId();
    Assert.notNull(rootId, "root id must not be null before cascade");

    for (CascadeRelation rel : cascades) {
      Object raw = ReflectionUtil.getFieldValue(root, rel.getFieldName());
      @SuppressWarnings("unchecked")
      Collection<Entity<?>> children =
          raw == null ? Collections.emptyList() : (Collection<Entity<?>>) raw;
      persistRelation(rel, rootId, children, removeOrphans);
    }
  }

  private void persistRelation(
      CascadeRelation rel, Object rootId, Collection<Entity<?>> children, boolean removeOrphans) {
    Class<?> childType = rel.getChildType();
    TableMetadata childMeta = TableMetadataResolver.load(childType);
    ColumnMetadata childPk = childMeta.getPrimaryKey();

    Set<Object> keepIds = new HashSet<>();
    for (Entity<?> child : children) {
      ReflectionUtil.setFieldValue(child, rel.getForeignKeyField(), rootId);
      Object childId = upsertChild(childType, childMeta, childPk, child);
      if (childId != null) {
        keepIds.add(childId);
      }
    }

    if (removeOrphans) {
      removeMissingChildren(
          childType, childMeta, childPk, rel.getForeignKeyField(), rootId, keepIds);
    }
  }

  private Object upsertChild(
      Class<?> childType, TableMetadata childMeta, ColumnMetadata childPk, Entity<?> child) {
    GenerationStrategy strategy = childPk.getGenerationStrategy();
    if (child.getId() == null && strategy != null && strategy != GenerationStrategy.IDENTITY) {
      Object generated = sqlExecutor.generateId(strategy, child);
      ReflectionUtil.setFieldValue(
          child, childPk.getFieldName(), TypeConverter.convert(generated, childPk.getType()));
    }

    Object id = child.getId();
    boolean exists = false;
    if (id != null) {
      Criteria<?> criteria =
          Criteria.create().entityClass(childType).eq(childPk.getFieldName(), id).page(1, 0);
      @SuppressWarnings("unchecked")
      CompiledQuery select = sqlBuilder.buildSelect((Class) childType, (Criteria) criteria, true);
      exists = sqlExecutor.querySingle(select, childType) != null;
    }

    if (exists) {
      @SuppressWarnings("unchecked")
      CompiledQuery update = sqlBuilder.buildDynamicUpdate((Class) childType, child);
      sqlExecutor.update(update);
      return child.getId();
    }

    if (strategy == GenerationStrategy.IDENTITY && child.getId() == null) {
      @SuppressWarnings("unchecked")
      BatchCompiledQuery batch =
          sqlBuilder.buildBatchInsert((Class) childType, Collections.singletonList(child));
      Map<String, Object> firstParams = batch.getBatchParameters().get(0);
      CompiledQuery singleInsert = new CompiledQuery(batch.getSql(), firstParams);
      Object newId = sqlExecutor.insert(singleInsert, childType);
      ReflectionUtil.setFieldValue(
          child, childPk.getFieldName(), TypeConverter.convert(newId, childPk.getType()));
      return child.getId();
    }

    @SuppressWarnings("unchecked")
    BatchCompiledQuery batch =
        sqlBuilder.buildBatchInsert((Class) childType, Collections.singletonList(child));
    sqlExecutor.batchUpdate(batch);
    return child.getId();
  }

  private void removeMissingChildren(
      Class<?> childType,
      TableMetadata childMeta,
      ColumnMetadata childPk,
      String foreignKeyField,
      Object rootId,
      Set<Object> keepIds) {
    Criteria<?> criteria =
        Criteria.create().entityClass(childType).eq(foreignKeyField, rootId).page(1, 0);
    @SuppressWarnings("unchecked")
    CompiledQuery select = sqlBuilder.buildSelect((Class) childType, (Criteria) criteria, false);
    @SuppressWarnings("unchecked")
    List<Entity<?>> existing = (List<Entity<?>>) (List<?>) sqlExecutor.query(select, childType);
    if (existing.isEmpty()) {
      return;
    }

    String tableName = childMeta.getName();
    String pkCol = childPk.getName();
    String tenantGuard = "";
    Long tenantId = null;
    if (childMeta.isTenantScoped()) {
      tenantId = TenantContext.getTenantIdAsLong();
      if (tenantId == null) {
        throw new MissingTenantContextException(tableName);
      }
      tenantGuard = " AND " + childMeta.getTenantIdColumn().getName() + " = :p1";
    }
    for (Entity<?> old : existing) {
      Object oldId = old.getId();
      if (oldId == null || keepIds.contains(oldId)) {
        continue;
      }
      Object[] params = tenantId == null ? new Object[] {oldId} : new Object[] {oldId, tenantId};
      if (childMeta.isSoftDeletable()) {
        sqlExecutor.delete(
            "UPDATE " + tableName + " SET deleted = true WHERE " + pkCol + " = :p0" + tenantGuard,
            params);
      } else {
        sqlExecutor.delete(
            "DELETE FROM " + tableName + " WHERE " + pkCol + " = :p0" + tenantGuard, params);
      }
    }
  }
}
