package com.bone.metadata.catalog.infrastructure.gateway;

import static com.bone.metadata.catalog.domain.model.physical.PhysicalStructurePlan.STATUS_READY;
import static com.bone.metadata.catalog.domain.model.physical.PhysicalStructurePlan.STATUS_RECONCILED;
import static com.bone.metadata.catalog.domain.model.physical.PhysicalStructurePlan.STATUS_REFUSED;
import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.tenant.context.TenantContext;
import com.bone.metadata.MetadataApplication;
import com.bone.metadata.catalog.domain.gateway.PhysicalStructureGateway;
import com.bone.metadata.catalog.domain.model.meta.MetaDeliveryMode;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.model.meta.MetaField;
import com.bone.metadata.catalog.domain.model.physical.PhysicalStructurePlan;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 物理结构网关「清列」破坏性护栏集成测试（元数据服务 test profile，真实本地 MySQL）。
 *
 * <p>覆盖：① 在用列 / 保留列删除被拒（REFUSED）；② 删字段（软删）后遗留的孤儿物理列被 {@code dropDriftedColumns} 清理（RECONCILED）；③
 * 不存在的列 no-op（READY）。
 *
 * <p>注：本模块未配置独立 H2 全上下文底座（{@code MetadataSdkContext} 为静态单例、且依赖 catalog 表与 Redis），故复用既有 MySQL 测试底座。
 */
@SpringBootTest(classes = MetadataApplication.class)
@ActiveProfiles("test")
class JdbcPhysicalStructureGatewayTest {

  private static final long TENANT = 1L;

  @Autowired private PhysicalStructureGateway gateway;
  @Autowired private MetaEntityRepository entityRepository;
  @Autowired private MetaFieldRepository fieldRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tableName;

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("BONE_DB_PASSWORD", "mysql123"));
    registry.add("security.enabled", () -> "false");
  }

  @BeforeEach
  void setupTenant() {
    // catalog 物理结构对齐按租户隔离；测试中模拟 JWT 提供的租户上下文（TenantContext 为空会触发 SDK 失败关闭）
    TenantContext.setTenantId(TENANT);
  }

  @AfterEach
  void cleanup() {
    TenantContext.clear();
    if (tableName != null) {
      jdbcTemplate.execute("DROP TABLE IF EXISTS `" + tableName + "`");
    }
  }

  @Test
  void dropColumn_refusesActiveAndReservedColumns_and_dropDriftedCleansOrphans() {
    String code = "e2e_drop_" + System.nanoTime();
    tableName = "meta_e2e_drop_" + System.nanoTime();

    MetaEntity entity =
        MetaEntity.create(
            null,
            TENANT,
            "E2E清列实体",
            code,
            "E2E清列实体",
            "desc",
            tableName,
            0,
            MetaDeliveryMode.RUNTIME.getCode(),
            "icon");
    entityRepository.insert(entity);

    MetaField f1 = MetaField.create(null, TENANT, entity.getId(), "字段一", "f1code", "字段一", "STRING");
    fieldRepository.insert(f1);
    MetaField f2 = MetaField.create(null, TENANT, entity.getId(), "字段二", "f2code", "字段二", "STRING");
    fieldRepository.insert(f2);

    // align 建物理表并加上两列
    PhysicalStructurePlan align = gateway.align(TENANT, code);
    assertThat(align.status()).isIn("CREATED", "ALIGNED");
    assertThat(columnExists("f1code")).isTrue();
    assertThat(columnExists("f2code")).isTrue();

    // 1) 在用列（仍被模型引用）删除被拒
    PhysicalStructurePlan refuseActive = gateway.dropColumn(TENANT, code, "f1code");
    assertThat(refuseActive.status()).isEqualTo(STATUS_REFUSED);
    assertThat(columnExists("f1code")).isTrue();

    // 2) 保留列禁止删除
    PhysicalStructurePlan refuseReserved = gateway.dropColumn(TENANT, code, "id");
    assertThat(refuseReserved.status()).isEqualTo(STATUS_REFUSED);

    // 3) 不存在的列 no-op（READY）
    PhysicalStructurePlan noop = gateway.dropColumn(TENANT, code, "ghost_col");
    assertThat(noop.status()).isEqualTo(STATUS_READY);

    // 软删字段一 → 其物理列变为孤儿
    fieldRepository.deleteById(f1.getId());

    // 4) 清理孤儿列：f1code 被 DROP，f2code 保留
    PhysicalStructurePlan reconcile = gateway.dropDriftedColumns(TENANT, code);
    assertThat(reconcile.status()).isEqualTo(STATUS_RECONCILED);
    assertThat(reconcile.executed()).isEqualTo(1);
    assertThat(columnExists("f1code")).isFalse();
    assertThat(columnExists("f2code")).isTrue();
  }

  private boolean columnExists(String col) {
    Integer n =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
            Integer.class,
            tableName,
            col);
    return n != null && n > 0;
  }
}
