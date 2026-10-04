package com.bone.metadata.sdk.query.dsl.builder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.tenant.context.TenantContext;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.spec.TableMetadataResolver;
import com.bone.metadata.sdk.query.context.SelectContext;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.dsl.context.QueryContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 软删谓词在两条读通道上的一致性回归测试。
 *
 * <p><b>缺陷背景（2026-10-03 实测发现）</b>：SDK 有两条并列读通道——
 *
 * <ul>
 *   <li><b>Criteria 通道</b>（{@code findById} / {@code findByCriteria} / {@code pageByCriteria} /
 *       {@code countByCriteria}）经 {@code query.builder.SelectBuilder} / {@code
 *       CountBuilder}，其中<b>已</b>按 {@code isSoftDeletable()} 追加 {@code deleted = false}；
 *   <li><b>DSL 通道</b>（{@code QueryBuilder.from(X.class)}，即 FluentQuery）经 {@code
 *       query.dsl.builder.SqlBuilder}，此前<b>只</b>注入租户谓词、<b>不</b>注入软删谓词。
 * </ul>
 *
 * 后果：同一张软删表，Criteria 查不到已删行、DSL 却能查出——<b>同一实体两条读路径结论相反</b>。生产代码中 {@code
 * MasterDataFieldRepository#findAllFields()} 正是走 DSL 通道，故 0017 迁移软删清理掉的孤儿字段仍会被捞回。
 *
 * <p>本测试不连数据库、不依赖 Spring 上下文，直接断言两条通道生成的 SQL 均含软删谓词。
 */
class SoftDeletePredicateConsistencyTest {

  /** 继承聚合根 +<b>自行</b>声明 {@code @Deleted} ⇒ 软删表（与 AbstractEntity 无关）。 */
  @com.bone.metadata.sdk.domain.annotation.Table("dsl_soft_del_probe")
  static class SoftDeletableEntity extends TenantAggregateRoot<Long> {
    @Id
    @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
    private Long id;

    @Column(name = "entity_name")
    private String entityName;

    @Deleted private Boolean deleted = false;
  }

  /** 继承聚合根且<b>不</b>声明 {@code @Deleted} ⇒ 非软删表，查询不得注入 deleted 谓词。 */
  @com.bone.metadata.sdk.domain.annotation.Table("dsl_physical_del_probe")
  static class PhysicalDeleteEntity extends TenantAggregateRoot<Long> {
    @Id
    @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
    private Long id;

    @Column(name = "entity_name")
    private String entityName;
  }

  @BeforeEach
  void setUpTenantContext() {
    // 探针实体声明了 tenantId ⇒ 属租户表，DSL 通道按 ADR-0029 失败关闭要求可信上下文
    TenantContext.setTenantId(1001L);
  }

  @AfterEach
  void tearDownTenantContext() {
    TenantContext.clear();
  }

  /** 用实体类构造一个空条件的 DSL 查询上下文（无租户条件、无软删条件）。 */
  private static <T> QueryContext<T> emptyContext(Class<T> entityClass) {
    return new QueryContext<>(entityClass, "t", null);
  }

  @Test
  @DisplayName("前置：TableMetadataResolver 按 @Deleted 字段识别软删表")
  void resolverRecognizesSoftDeleteByAnnotation() {
    assertTrue(
        TableMetadataResolver.load(SoftDeletableEntity.class).isSoftDeletable(),
        "声明了 @Deleted 字段的实体应被识别为软删表");
    assertFalse(
        TableMetadataResolver.load(PhysicalDeleteEntity.class).isSoftDeletable(),
        "未声明 @Deleted 的实体不应被识别为软删表");
  }

  @Test
  @DisplayName("DSL SELECT：空条件时也要追加 deleted = false（不能漏掉 WHERE）")
  void dslSelectAppendsSoftDeletePredicate() {
    String sql = new SqlBuilder<>(emptyContext(SoftDeletableEntity.class)).buildSelectSql();
    assertTrue(sql.contains("deleted = false"), "DSL SELECT 应含软删谓词，否则已软删行会被查出。实际 SQL: " + sql);
    assertTrue(sql.contains("t.deleted = false"), "软删列应带表别名限定。实际 SQL: " + sql);
    assertTrue(sql.contains("WHERE"), "空条件 + 软删谓词仍须有 WHERE 子句。实际 SQL: " + sql);
  }

  @Test
  @DisplayName("DSL COUNT：空条件时也要追加 deleted = false（分页总数不得含已删行）")
  void dslCountAppendsSoftDeletePredicate() {
    String sql = new SqlBuilder<>(emptyContext(SoftDeletableEntity.class)).buildCountSql();
    assertTrue(sql.contains("deleted = false"), "DSL COUNT 应含软删谓词，否则分页总数与实际列表不一致。实际 SQL: " + sql);
    assertTrue(sql.contains("WHERE"), "空条件 + 软删谓词仍须有 WHERE 子句。实际 SQL: " + sql);
  }

  @Test
  @DisplayName("DSL 聚合：空条件时也要追加 deleted = false（统计不得把已删行算进去）")
  void dslAggregateAppendsSoftDeletePredicate() {
    String sql =
        new SqlBuilder<>(emptyContext(SoftDeletableEntity.class))
            .buildAggregateSql("COUNT", "entityName");
    assertTrue(sql.contains("deleted = false"), "DSL 聚合应含软删谓词，否则统计数字虚高。实际 SQL: " + sql);
  }

  @Test
  @DisplayName("DSL 投影：同样追加软删谓词")
  void dslProjectionAppendsSoftDeletePredicate() {
    String sql =
        new SqlBuilder<>(emptyContext(SoftDeletableEntity.class)).buildProjectionSql("entityName");
    assertTrue(sql.contains("deleted = false"), "实际 SQL: " + sql);
  }

  @Test
  @DisplayName("非软删表不得注入 deleted 谓词（否则 Unknown column 直接报错）")
  void dslDoesNotAppendPredicateForPhysicalDeleteTable() {
    String sql = new SqlBuilder<>(emptyContext(PhysicalDeleteEntity.class)).buildSelectSql();
    assertFalse(sql.contains("deleted"), "非软删表注入 deleted 谓词会报 Unknown column。实际 SQL: " + sql);
  }

  @Test
  @DisplayName("Criteria 通道对照：SelectContext 的软删过滤开关语义（不依赖 Spring DI）")
  void criteriaContextCarriesIncludeDeletedFlag() {
    TableMetadata table = TableMetadataResolver.load(SoftDeletableEntity.class);
    Criteria<SoftDeletableEntity> criteria =
        Criteria.<SoftDeletableEntity>create().entityClass(SoftDeletableEntity.class);
    // includeDeleted=false ⇒ SelectBuilder 会追加 deleted = false；此处只验证上下文构造正确
    SelectContext ctx = new SelectContext(table, criteria, false);
    assertFalse(ctx.isIncludeDeleted(), "默认读路径应排除已删行");
  }
}
