package com.bone.metadata.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.application.command.cmd.CopyMetaEntityCommand;
import com.bone.metadata.catalog.application.query.dto.EntityValidationIssue;
import com.bone.metadata.catalog.application.query.dto.PublishPreviewDTO;
import com.bone.metadata.catalog.domain.gateway.PhysicalStructureGateway;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.model.meta.MetaEntityRelation;
import com.bone.metadata.catalog.domain.model.meta.MetaField;
import com.bone.metadata.catalog.domain.model.physical.PhysicalStructurePlan;
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
 * 建模工作台闭环的应用服务单测（UC-W2 复制 / UC-W5 校验 / UC-W7 摘要级预览）。
 *
 * <p>纯 Mockito 单测：校验规则分级（ERROR/WARNING/INFO）、RUNTIME 物理漂移上浮、复制的字段复制与缺省值契约。
 */
@ExtendWith(MockitoExtension.class)
class MetaEntityWorkbenchServiceTest {

  @Mock private MetaEntityRepository metaEntityRepository;
  @Mock private MetaFieldRepository metaFieldRepository;
  @Mock private MetaEntityRelationRepository metaEntityRelationRepository;
  @Mock private IamModuleValidator iamModuleValidator;
  @Mock private TenantProvider tenantProvider;
  @Mock private PhysicalStructureGateway physicalStructureGateway;

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
            () -> null,
            Optional.empty(),
            mock(PlatformTransactionManager.class));
  }

  // ===================== UC-W5 静态校验 =====================

  @Test
  @DisplayName("校验：非法编码/表名 → ERROR，未归属模块 → WARNING")
  void validate_shouldFlagInvalidBasicsAndUnassignedModule() {
    MetaEntity entity = entity(500L, "9bad-code", "9bad-table", 0, null);
    stubFields(entity.getId(), List.of());
    stubRelationsEmpty();

    List<EntityValidationIssue> issues = service.validateEntity(entity.getId());

    assertThat(codes(issues))
        .contains("ENTITY_CODE_INVALID", "ENTITY_TABLE_INVALID", "MODULE_UNASSIGNED");
    assertThat(levelOf(issues, "ENTITY_CODE_INVALID")).isEqualTo(EntityValidationIssue.LEVEL_ERROR);
    assertThat(levelOf(issues, "MODULE_UNASSIGNED")).isEqualTo(EntityValidationIssue.LEVEL_WARNING);
  }

  @Test
  @DisplayName("校验：字段编码重复 → ERROR；必填+唯一 / 字符串无长度 → WARNING")
  void validate_shouldFlagDuplicateAndRiskyFields() {
    MetaEntity entity = entity(501L, "bp_ok", "bp_ok", 1, 1L);
    stubFields(
        entity.getId(),
        List.of(
            MetaField.create(
                null, 1L, 501L, "amt", "amt", "金额", "STRING", null, true, true, null, null, 1, 1L),
            MetaField.create(
                null, 1L, 501L, "amt2", "amt", "金额2", "DECIMAL", 18, false, false, null, null, 2,
                1L)));
    stubRelationsEmpty();

    List<EntityValidationIssue> issues = service.validateEntity(entity.getId());

    assertThat(codes(issues))
        .contains("FIELD_CODE_DUPLICATE", "FIELD_REQUIRED_UNIQUE", "FIELD_LENGTH_MISSING");
    assertThat(levelOf(issues, "FIELD_CODE_DUPLICATE"))
        .isEqualTo(EntityValidationIssue.LEVEL_ERROR);
    assertThat(levelOf(issues, "FIELD_REQUIRED_UNIQUE"))
        .isEqualTo(EntityValidationIssue.LEVEL_WARNING);
  }

  @Test
  @DisplayName("校验：RUNTIME 实体类型漂移（validateForPublish 拒绝）→ ERROR，与发布期 409 同源")
  void validate_shouldFlagPhysicalDrift_forRuntime() {
    MetaEntity entity = entity(502L, "bp_rt", "bp_rt", 1, 1L);
    stubFields(entity.getId(), List.of());
    stubRelationsEmpty();
    org.mockito.Mockito.doThrow(BizException.of("检测到结构漂移：列 amount 类型不兼容"))
        .when(physicalStructureGateway)
        .validateForPublish(1L, "bp_rt");

    List<EntityValidationIssue> issues = service.validateEntity(entity.getId());

    assertThat(codes(issues)).contains("PHYSICAL_DRIFT");
    assertThat(levelOf(issues, "PHYSICAL_DRIFT")).isEqualTo(EntityValidationIssue.LEVEL_ERROR);
  }

  @Test
  @DisplayName("校验：RUNTIME 草稿缺表/缺列不报错（由发布 align 补齐），validateForPublish 放行")
  void validate_shouldNotFlagMissingTable_forDraft() {
    MetaEntity entity = entity(506L, "bp_draft_rt", "bp_draft_rt", 1, 1L);
    stubFields(entity.getId(), List.of());
    stubRelationsEmpty();
    // 缺表场景 validateForPublish 放行（no-op）
    org.mockito.Mockito.doNothing()
        .when(physicalStructureGateway)
        .validateForPublish(1L, "bp_draft_rt");

    List<EntityValidationIssue> issues = service.validateEntity(entity.getId());

    assertThat(codes(issues)).doesNotContain("PHYSICAL_DRIFT");
  }

  @Test
  @DisplayName("校验：GENERATIVE 实体不触发物理 inspect")
  void validate_shouldSkipPhysicalInspect_forGenerative() {
    MetaEntity entity = entity(503L, "bp_gen", "bp_gen", 0, 1L);
    stubFields(entity.getId(), List.of());
    stubRelationsEmpty();

    service.validateEntity(entity.getId());

    verify(physicalStructureGateway, never()).inspect(anyLong(), any());
  }

  // ===================== UC-W7 发布摘要预览 =====================

  @Test
  @DisplayName("预览：附字段清单/关系数/物理计划；无 ERROR 时 runnable=true")
  void preview_shouldAttachPlanAndRunnableFlag() {
    MetaEntity entity = entity(504L, "bp_prev", "bp_prev", 1, 1L);
    stubFields(entity.getId(), List.of(field(entity.getId(), "amt", "DECIMAL", 18)));
    stubRelationsEmpty();
    when(physicalStructureGateway.inspect(1L, "bp_prev"))
        .thenReturn(
            new PhysicalStructurePlan(
                "bp_prev",
                "bp_prev",
                true,
                List.of("CREATE TABLE bp_prev"),
                0,
                PhysicalStructurePlan.STATUS_READY,
                "整表不存在，将执行 CREATE TABLE"));

    PublishPreviewDTO preview = service.getPublishPreview(entity.getId());

    assertThat(preview.isRunnable()).isTrue();
    assertThat(preview.isHasBlockingErrors()).isFalse();
    assertThat(preview.getFields()).hasSize(1);
    assertThat(preview.getRelationCount()).isZero();
    assertThat(preview.getPhysical()).isNotNull();
    assertThat(preview.getPhysical().statements()).containsExactly("CREATE TABLE bp_prev");
  }

  @Test
  @DisplayName("预览：存在 ERROR（非法编码）时 runnable=false，物理计划仍返回供排查")
  void preview_shouldMarkNotRunnable_whenBlockingErrors() {
    MetaEntity entity = entity(505L, "9bad", "9bad", 1, 1L);
    stubFields(entity.getId(), List.of());
    stubRelationsEmpty();
    when(physicalStructureGateway.inspect(anyLong(), any()))
        .thenReturn(
            new PhysicalStructurePlan(
                "9bad", "9bad", true, List.of(), 0, PhysicalStructurePlan.STATUS_READY, ""));

    PublishPreviewDTO preview = service.getPublishPreview(entity.getId());

    assertThat(preview.isRunnable()).isFalse();
    assertThat(preview.isHasBlockingErrors()).isTrue();
  }

  // ===================== UC-W2 流程 B 实体复制 =====================

  @Test
  @DisplayName("复制：复制定义+全部字段为新草稿；名称缺省补 _copy；走 createEntity 统一校验（G1②）")
  void copy_shouldCopyFieldsWithDefaults() {
    MetaEntity source = entity(1L, "bp_src", "bp_src", 1, 7L);
    stubFields(1L, List.of(field(1L, "amt", "DECIMAL", 18), field(1L, "memo", "STRING", 64)));
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(metaEntityRepository.findByTenantAndCode(1L, "bp_new")).thenReturn(Optional.empty());
    when(metaEntityRepository.findByTenantAndTableName(1L, "t_new")).thenReturn(Optional.empty());
    doAnswer(
            inv -> {
              MetaEntity created = inv.getArgument(0);
              created.setId(9001L);
              return null;
            })
        .when(metaEntityRepository)
        .insert(any(MetaEntity.class));

    CopyMetaEntityCommand cmd = new CopyMetaEntityCommand();
    cmd.setCode("bp_new");
    cmd.setTableName("t_new");

    Long newId = service.copyEntity(1L, cmd);

    assertThat(newId).isEqualTo(9001L);
    // 复制走 createEntity 门面：目标模块建模准入（G1②）被统一调用
    verify(iamModuleValidator).requireModelingAllowed(eq(7L), any());
    ArgumentCaptor<MetaEntity> entityCaptor = ArgumentCaptor.forClass(MetaEntity.class);
    verify(metaEntityRepository).insert(entityCaptor.capture());
    assertThat(entityCaptor.getValue().getCode()).isEqualTo("bp_new");
    assertThat(entityCaptor.getValue().getTableName()).isEqualTo("t_new");
    assertThat(entityCaptor.getValue().getName()).isEqualTo("bp_src_copy");
    assertThat(entityCaptor.getValue().getDisplayName()).isEqualTo("bp_src 副本");
    // 字段逐条复制到新实体
    ArgumentCaptor<MetaField> fieldCaptor = ArgumentCaptor.forClass(MetaField.class);
    verify(metaFieldRepository, org.mockito.Mockito.times(2)).insert(fieldCaptor.capture());
    assertThat(fieldCaptor.getAllValues())
        .allMatch(f -> Long.valueOf(9001L).equals(f.getEntityId()));
    assertThat(fieldCaptor.getAllValues())
        .extracting(MetaField::getCode)
        .containsExactly("amt", "memo");
    assertThat(fieldCaptor.getAllValues()).allMatch(f -> Long.valueOf(7L).equals(f.getModuleId()));
  }

  @Test
  @DisplayName("复制：目标模块显式指定时按目标模块归属并校验建模角色")
  void copy_shouldUseTargetModule_whenExplicit() {
    MetaEntity source = entity(1L, "bp_src", "bp_src", 0, 7L);
    stubFields(1L, List.of());
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(metaEntityRepository.findByTenantAndCode(1L, "bp_new")).thenReturn(Optional.empty());
    when(metaEntityRepository.findByTenantAndTableName(1L, "t_new")).thenReturn(Optional.empty());
    doAnswer(
            inv -> {
              MetaEntity created = inv.getArgument(0);
              created.setId(9002L);
              return null;
            })
        .when(metaEntityRepository)
        .insert(any(MetaEntity.class));

    CopyMetaEntityCommand cmd = new CopyMetaEntityCommand();
    cmd.setCode("bp_new");
    cmd.setTableName("t_new");
    cmd.setTargetModuleId(9L);

    service.copyEntity(1L, cmd);

    verify(iamModuleValidator).requireModelingAllowed(eq(9L), any());
    verify(metaFieldRepository, never()).insert(any(MetaField.class));
    ArgumentCaptor<MetaEntity> entityCaptor = ArgumentCaptor.forClass(MetaEntity.class);
    verify(metaEntityRepository).insert(entityCaptor.capture());
    assertThat(entityCaptor.getValue().getModuleId()).isEqualTo(9L);
  }

  @Test
  @DisplayName("复制：缺编码/表名 → 400，且不触发任何写入")
  void copy_shouldRejectBlankCode() {
    CopyMetaEntityCommand cmd = new CopyMetaEntityCommand();
    cmd.setCode("  ");
    cmd.setTableName("t_x");

    assertThatThrownBy(() -> service.copyEntity(1L, cmd))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("新编码");
    verify(metaEntityRepository, never()).insert(any(MetaEntity.class));
    verify(metaFieldRepository, never()).insert(any(MetaField.class));
  }

  @Test
  @DisplayName("复制：源实体不存在 → 404")
  void copy_shouldReject404_whenSourceMissing() {
    when(metaEntityRepository.findById(404L)).thenReturn(null);
    CopyMetaEntityCommand cmd = new CopyMetaEntityCommand();
    cmd.setCode("bp_x");
    cmd.setTableName("t_x");
    assertThatThrownBy(() -> service.copyEntity(404L, cmd))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("实体不存在");
  }

  // ===================== 辅助 =====================

  private MetaEntity entity(
      long id, String code, String tableName, int deliveryMode, Long moduleId) {
    MetaEntity e =
        MetaEntity.create(
            null, 1L, code, code, code, null, tableName, 0, deliveryMode, null, moduleId);
    e.setId(id);
    when(metaEntityRepository.findById(id)).thenReturn(e);
    return e;
  }

  private MetaField field(long entityId, String code, String type, Integer length) {
    return MetaField.create(
        null, 1L, entityId, code, code, code, type, length, false, false, null, null, 1, 1L);
  }

  private void stubFields(long entityId, List<MetaField> fields) {
    when(metaFieldRepository.pageFields(eq(entityId), isNull(), eq(1), anyInt()))
        .thenReturn(PageResult.of(fields, (long) fields.size(), 1, 500));
  }

  private void stubRelationsEmpty() {
    when(metaEntityRelationRepository.pageRelations(
            anyLong(), any(), isNull(), isNull(), eq(1), anyInt()))
        .thenReturn(PageResult.of(List.<MetaEntityRelation>of(), 0L, 1, 500));
    when(metaEntityRelationRepository.pageRelations(
            anyLong(), isNull(), any(), isNull(), eq(1), anyInt()))
        .thenReturn(PageResult.of(List.<MetaEntityRelation>of(), 0L, 1, 500));
  }

  private static List<String> codes(List<EntityValidationIssue> issues) {
    return issues.stream().map(EntityValidationIssue::getCode).toList();
  }

  private static String levelOf(List<EntityValidationIssue> issues, String code) {
    return issues.stream()
        .filter(i -> code.equals(i.getCode()))
        .findFirst()
        .orElseThrow()
        .getLevel();
  }
}
