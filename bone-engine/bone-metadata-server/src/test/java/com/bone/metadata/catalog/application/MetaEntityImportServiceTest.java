package com.bone.metadata.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.application.command.cmd.ImportMetaEntityFromTableCommand;
import com.bone.metadata.catalog.application.support.PhysicalTypeMapper;
import com.bone.metadata.catalog.common.ImportMetaEntityResult;
import com.bone.metadata.catalog.domain.gateway.PhysicalStructureGateway;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.model.meta.MetaField;
import com.bone.metadata.catalog.domain.model.physical.PhysicalTableColumn;
import com.bone.metadata.catalog.domain.model.physical.PhysicalTableSnapshot;
import com.bone.metadata.catalog.domain.repository.MetaEntityRelationRepository;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import com.bone.metadata.catalog.domain.service.IamModuleValidator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 逆向建模（UC-IMP）应用服务单测：存量物理表 → 目录实体草稿。
 *
 * <p>覆盖真实纳管场景的四个契约：① 缺表拒绝（不建脱节空模型）；② 保留列跳过且回传跳过清单； ③ 类型/必填/精度取自物理列（保证导入后可零漂移发布）；④ dryRun 不落库。
 */
@ExtendWith(MockitoExtension.class)
class MetaEntityImportServiceTest {

  @Mock private MetaEntityRepository metaEntityRepository;
  @Mock private MetaFieldRepository metaFieldRepository;
  @Mock private MetaEntityRelationRepository metaEntityRelationRepository;
  @Mock private IamModuleValidator iamModuleValidator;
  @Mock private TenantProvider tenantProvider;
  @Mock private PhysicalStructureGateway physicalStructureGateway;
  @Mock private com.bone.core.domain.event.DomainEventPublisher domainEventPublisher;

  private MetaEntityApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaEntityApplicationService(
            metaEntityRepository,
            metaFieldRepository,
            metaEntityRelationRepository,
            iamModuleValidator,
            tenantProvider,
            physicalStructureGateway,
            mock(com.bone.metadata.sdk.metadata.api.MetadataService.class),
            () -> null,
            Optional.empty(),
            domainEventPublisher,
            mock(PlatformTransactionManager.class));
  }

  /** bone-blueprint t_order 的真实物理结构（含保留列与 DECIMAL 精度）。 */
  private static PhysicalTableSnapshot tOrderSnapshot() {
    return new PhysicalTableSnapshot(
        "t_order",
        true,
        List.of(
            col("id", "bigint", false, "雪花算法生成的全局唯一ID", true),
            col("tenant_id", "bigint", true, "租户ID", true),
            col("customer_id", "bigint", false, "", false),
            col("total_amount", "decimal", false, "", false),
            col("status", "varchar", false, "", false),
            col("created_at", "datetime", false, "", true),
            col("updated_at", "datetime", false, "", true)));
  }

  private static PhysicalTableColumn col(
      String name, String dataType, boolean nullable, String comment, boolean reserved) {
    return new PhysicalTableColumn(
        name,
        dataType,
        "varchar".equals(dataType) ? 64 : null,
        "decimal".equals(dataType) ? 10 : null,
        "decimal".equals(dataType) ? 2 : null,
        nullable,
        comment,
        reserved);
  }

  @Test
  @DisplayName("导入：存量表 → DRAFT 实体 + 业务字段；保留列跳过并回传")
  void import_shouldCreateDraftEntityAndSkipReservedColumns() {
    when(tenantProvider.currentTenantId()).thenReturn(1001L);
    when(physicalStructureGateway.readTableSnapshot("t_order")).thenReturn(tOrderSnapshot());
    when(metaEntityRepository.findByTenantAndCode(1001L, "t_order")).thenReturn(Optional.empty());
    when(metaEntityRepository.findByTenantAndTableName(1001L, "t_order"))
        .thenReturn(Optional.empty());

    ImportMetaEntityResult result = service.importEntityFromTable(cmd("t_order", false));

    assertThat(result.importedFields()).isEqualTo(3);
    assertThat(result.skippedColumns())
        .containsExactlyInAnyOrder("id", "tenant_id", "created_at", "updated_at");
    ArgumentCaptor<MetaEntity> entityCaptor = ArgumentCaptor.forClass(MetaEntity.class);
    verify(metaEntityRepository).insert(entityCaptor.capture());
    assertThat(entityCaptor.getValue().getStatus()).isEqualTo(0); // DRAFT：导入 ≠ 发布
    assertThat(entityCaptor.getValue().getDeliveryMode()).isEqualTo(1); // 存量表默认 RUNTIME
    ArgumentCaptor<MetaField> fieldCaptor = ArgumentCaptor.forClass(MetaField.class);
    verify(metaFieldRepository, org.mockito.Mockito.times(3)).insert(fieldCaptor.capture());
    assertThat(fieldCaptor.getAllValues())
        .extracting(MetaField::getCode)
        .containsExactly("customer_id", "total_amount", "status");
    assertThat(fieldCaptor.getAllValues())
        .extracting(MetaField::getType)
        .containsExactly("LONG", "DECIMAL", "STRING");
    // NOT NULL → required；列名无注释时显示名回落列名
    assertThat(fieldCaptor.getAllValues().get(0).getRequired()).isTrue();
    assertThat(fieldCaptor.getAllValues().get(0).getDisplayName()).isEqualTo("customer_id");
  }

  @Test
  @DisplayName("导入：缺表直接拒绝，且不落库")
  void import_shouldRejectMissingTable() {
    when(tenantProvider.currentTenantId()).thenReturn(1001L);
    when(physicalStructureGateway.readTableSnapshot("not_exists"))
        .thenReturn(PhysicalTableSnapshot.missing("not_exists"));

    assertThatThrownBy(() -> service.importEntityFromTable(cmd("not_exists", false)))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("物理表不存在");
    verify(metaEntityRepository, never()).insert(any());
    verify(metaFieldRepository, never()).insert(any());
  }

  @Test
  @DisplayName("导入：dryRun 只回统计，不写库")
  void import_dryRunShouldNotPersist() {
    when(tenantProvider.currentTenantId()).thenReturn(1001L);
    when(physicalStructureGateway.readTableSnapshot("t_order")).thenReturn(tOrderSnapshot());

    ImportMetaEntityResult result = service.importEntityFromTable(cmd("t_order", true));

    assertThat(result.dryRun()).isTrue();
    assertThat(result.entityId()).isNull();
    assertThat(result.importedFields()).isEqualTo(3);
    verify(metaEntityRepository, never()).insert(any());
    verify(metaFieldRepository, never()).insert(any());
  }

  @Test
  @DisplayName("导入：重复纳管同一表 → 拒绝（表名唯一键）")
  void import_shouldRejectDuplicateTable() {
    when(tenantProvider.currentTenantId()).thenReturn(1001L);
    when(physicalStructureGateway.readTableSnapshot("t_order")).thenReturn(tOrderSnapshot());
    when(metaEntityRepository.findByTenantAndCode(1001L, "t_order")).thenReturn(Optional.empty());
    when(metaEntityRepository.findByTenantAndTableName(1001L, "t_order"))
        .thenReturn(
            Optional.of(
                MetaEntity.create(
                    null, 1001L, "t_order", "t_order", "订单", null, "t_order", 0, 1, null, null)));

    assertThatThrownBy(() -> service.importEntityFromTable(cmd("t_order", false)))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("实体表名");
  }

  @Test
  @DisplayName("类型映射：物理类型 → 元数据模型类型，TINYINT(1) 视作布尔")
  void physicalTypeMapper_shouldMapToMetaTypes() {
    assertThat(PhysicalTypeMapper.toMetaType("BIGINT")).isEqualTo("LONG");
    assertThat(PhysicalTypeMapper.toMetaType("VARCHAR")).isEqualTo("STRING");
    assertThat(PhysicalTypeMapper.toMetaType("DECIMAL")).isEqualTo("DECIMAL");
    assertThat(PhysicalTypeMapper.toMetaType("DATETIME")).isEqualTo("DATETIME");
    assertThat(PhysicalTypeMapper.toMetaType("JSON")).isEqualTo("JSON");
    assertThat(
            PhysicalTypeMapper.toMetaType(
                new PhysicalTableColumn("flag", "TINYINT", null, 1, 0, true, "", false)))
        .isEqualTo("BOOLEAN");
    assertThat(
            PhysicalTypeMapper.toMetaType(
                new PhysicalTableColumn("qty", "TINYINT", null, 4, 0, true, "", false)))
        .isEqualTo("INT");
    // 字符串保留物理长度，非字符串无长度语义
    assertThat(
            PhysicalTypeMapper.toLength(
                new PhysicalTableColumn("name", "VARCHAR", 200, null, null, true, "", false)))
        .isEqualTo(200);
    assertThat(
            PhysicalTypeMapper.toLength(
                new PhysicalTableColumn("amt", "DECIMAL", null, 10, 2, true, "", false)))
        .isNull();
  }

  private static ImportMetaEntityFromTableCommand cmd(String tableName, boolean dryRun) {
    ImportMetaEntityFromTableCommand cmd = new ImportMetaEntityFromTableCommand();
    cmd.setTableName(tableName);
    cmd.setDryRun(dryRun);
    return cmd;
  }
}
