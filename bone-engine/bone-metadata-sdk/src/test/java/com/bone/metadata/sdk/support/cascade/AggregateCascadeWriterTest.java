package com.bone.metadata.sdk.support.cascade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.annotation.Transient;
import com.bone.core.domain.entity.Entity;
import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.domain.annotation.Cascade;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.model.CascadeRelation;
import com.bone.metadata.sdk.domain.spec.TableMetadataResolver;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.dialect.DatabaseDialect;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.Test;

/** {@code @Cascade} 写路径：根 insert 后回填外键并插入子行。 */
class AggregateCascadeWriterTest {

  @Test
  void metadata_resolvesCascadeOnTransientCollection() {
    List<CascadeRelation> cascades = TableMetadataResolver.load(Parent.class).getCascades();
    assertEquals(1, cascades.size());
    assertEquals("items", cascades.get(0).getFieldName());
    assertEquals("parentId", cascades.get(0).getForeignKeyField());
    assertEquals(Child.class, cascades.get(0).getChildType());
  }

  @Test
  void insert_writesChildAndFillsForeignKey() {
    SqlExecutor se = mock(SqlExecutor.class);
    when(se.querySingle(any(), any())).thenReturn(null);
    when(se.batchUpdate(any())).thenReturn(new int[] {1});

    Child child = new Child();
    child.setId(2L);
    Parent parent = new Parent();
    parent.setId(1L);
    parent.setItems(List.of(child));

    repository(se).insert(parent);

    assertEquals(1L, child.getParentId());
    verify(se, times(2)).batchUpdate(any());
  }

  /**
   * update 路径的孤儿清除：当内存子集合为空（聚合重载不回填子集合的典型场景，如订单明细）、 而 DB 中已存在该 root 的子行时，默认 {@code
   * orphanRemoval=true} 必须软删/硬删这些“孤儿”。 此用例锁住既有契约，防止被无意改回“不清空”。
   */
  @Test
  void update_removesOrphansByDefault_whenInMemoryCollectionEmpty() {
    SqlExecutor se = mock(SqlExecutor.class);
    Child existing = new Child();
    existing.setId(99L);
    doReturn(List.of(existing)).when(se).query(any(), any());

    Parent parent = new Parent();
    parent.setId(1L);
    parent.setItems(List.of()); // 内存态为空，但 DB 实际有子行

    writer(se).persistChildren(parent, true); // removeOrphans=true 模拟 repository.update

    verify(se, times(1)).delete(anyString(), any());
  }

  /**
   * 反向锁：显式 {@code orphanRemoval = false}（如订单明细）时，即使内存子集合为空、 DB 有既有子行，update
   * 也绝不能删除既有子行——这正是修复“订单详情无商品信息”的根因开关。
   */
  @Test
  void update_keepsExistingChildren_whenOrphanRemovalFalse() {
    SqlExecutor se = mock(SqlExecutor.class);
    Child existing = new Child();
    existing.setId(99L);
    doReturn(List.of(existing)).when(se).query(any(), any());

    ParentNoOrphan parent = new ParentNoOrphan();
    parent.setId(1L);
    parent.setItems(List.of());

    writer(se).persistChildren(parent, true);

    verify(se, never()).delete(anyString(), any());
  }

  private AggregateCascadeWriter writer(SqlExecutor se) {
    SqlBuilder sqlBuilder = new SqlBuilder(mock(MetadataService.class), new DatabaseDialect());
    sqlBuilder.init();
    return new AggregateCascadeWriter(sqlBuilder, se);
  }

  private BaseRepository<Parent, Long> repository(SqlExecutor se) {
    SqlBuilder sqlBuilder = new SqlBuilder(mock(MetadataService.class), new DatabaseDialect());
    sqlBuilder.init();
    return new BaseRepository<>(sqlBuilder, se, Parent.class, mock(ExtensionCoordinator.class)) {};
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @Table("t_cascade_parent")
  static class Parent extends Entity<Long> {
    @Transient
    @Cascade(foreignKey = "parentId")
    private List<Child> items;
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @Table("t_cascade_parent_no_orphan")
  static class ParentNoOrphan extends Entity<Long> {
    @Transient
    @Cascade(foreignKey = "parentId", orphanRemoval = false)
    private List<Child> items;
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @Table("t_cascade_child")
  static class Child extends Entity<Long> {
    private Long parentId;
  }
}
