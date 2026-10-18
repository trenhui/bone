package com.bone.metadata.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.application.query.dto.MergePreview;
import com.bone.metadata.catalog.application.query.dto.UiSchemaResp;
import com.bone.metadata.catalog.common.CatalogErrorCodes;
import com.bone.metadata.catalog.domain.gateway.TemplateReadPort;
import com.bone.metadata.catalog.domain.gateway.TemplateUiSnapshot;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.gateway.UiSchemaRefCheckPort;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.model.meta.MetaField;
import com.bone.metadata.catalog.domain.model.ui.UiSchemaDef;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import com.bone.metadata.catalog.domain.repository.UiSchemaDefRepository;
import com.bone.metadata.sdk.domain.exception.OptimisticLockingFailureException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;

/**
 * 界面 schema 应用服务单测（迁移 0046 · P0-3）。
 *
 * <p>覆盖：存取删、If-Match 并发（409/412）、引用完整性 UI-REF-01~09、发布快照、实例化拷贝命名空间重写、 跟随三步（检查/三路归并/两路降级/合入冲突）。
 */
class UiSchemaApplicationServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final UiSchemaDefRepository uiSchemaDefRepository = mock(UiSchemaDefRepository.class);
  private final MetaEntityRepository metaEntityRepository = mock(MetaEntityRepository.class);
  private final MetaFieldRepository metaFieldRepository = mock(MetaFieldRepository.class);
  private final UiSchemaRefCheckPort refCheckPort = mock(UiSchemaRefCheckPort.class);
  private final TemplateReadPort templateReadPort = mock(TemplateReadPort.class);
  private final TenantProvider tenantProvider = mock(TenantProvider.class);

  private final UiSchemaApplicationService service =
      new UiSchemaApplicationService(
          uiSchemaDefRepository,
          metaEntityRepository,
          metaFieldRepository,
          refCheckPort,
          templateReadPort,
          tenantProvider);

  private MetaEntity entity;

  @BeforeEach
  void setUp() {
    entity = MetaEntity.create(9001L, 7L, "报销", "exp", "报销", null, "t_exp", 0, 1, null, 1L);
    when(tenantProvider.currentTenantId()).thenReturn(7L);
    when(metaEntityRepository.findById(9001L)).thenReturn(entity);
    when(metaFieldRepository.pageFields(eq(9001L), eq(null), eq(1), eq(500)))
        .thenReturn(PageResult.of(List.of(field("title"), field("amount")), 2L, 1, 500));
    when(refCheckPort.actionCodesOf(7L, "exp")).thenReturn(Set.of("submit", "approve"));
  }

  private static MetaField field(String code) {
    return MetaField.create(null, 7L, 9001L, code, code, code, "STRING");
  }

  private static JsonNode json(String raw) {
    try {
      return MAPPER.readTree(raw);
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static String schema(String inner) {
    return "{\"schemaVersion\":1,\"entityCode\":\"exp\",\"views\":{\"form\":{\"sections\":[]}}"
        + (inner == null || inner.isEmpty() ? "" : "," + inner)
        + "}";
  }

  private UiSchemaDef row(String schemaJson, long lock) {
    UiSchemaDef def = UiSchemaDef.create(7L, 9001L, schemaJson, 1);
    def.setId(55L);
    return def;
  }

  @Test
  @DisplayName("无行读草稿返回 empty（前端回落列投影，非错误）")
  void getDraftSchema_shouldReturnEmpty_whenNoRow() {
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L)).thenReturn(Optional.empty());

    assertThat(service.getDraftSchema(9001L)).isEmpty();
  }

  @Test
  @DisplayName("首存建行（无行时缺 If-Match 亦可建，412 只防误覆盖已存在行）")
  void saveDraftSchema_shouldInsert_whenNoRow() {
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L)).thenReturn(Optional.empty());

    UiSchemaResp resp = service.saveDraftSchema(9001L, json(schema("")), null);

    assertThat(resp.getJson()).contains("schemaVersion");
    assertThat(resp.getLockVersion()).isEqualTo(0L);
    verify(uiSchemaDefRepository).insert(any(UiSchemaDef.class));
  }

  @Test
  @DisplayName("锁版本一致更新草稿；SDK 乐观锁冲突翻译为 409")
  void saveDraftSchema_shouldUpdate_whenLockMatches() {
    UiSchemaDef def = row(schema(""), 3L);
    // 模拟 SDK 回写后的锁（update 成功 lock_version+1 由 DB 层完成，此处只断言调用）
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L)).thenReturn(Optional.of(def));

    service.saveDraftSchema(9001L, json(schema("")), 0L);

    verify(uiSchemaDefRepository).update(def);
  }

  @Test
  @DisplayName("锁版本不一致 → 409（业务码 UI_SCHEMA_VERSION_CONFLICT）")
  void saveDraftSchema_shouldConflict409_whenLockMismatch() {
    UiSchemaDef def = row(schema(""), 3L);
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L)).thenReturn(Optional.of(def));

    assertThatThrownBy(() -> service.saveDraftSchema(9001L, json(schema("")), 9L))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(CatalogErrorCodes.UI_SCHEMA_VERSION_CONFLICT));
  }

  @Test
  @DisplayName("SDK 层乐观锁失败（并发 PUT）→ 409，不抛 500")
  void saveDraftSchema_shouldConflict409_whenSdkOptimisticLockFails() {
    UiSchemaDef def = row(schema(""), 3L);
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L)).thenReturn(Optional.of(def));
    when(uiSchemaDefRepository.update(any(UiSchemaDef.class)))
        .thenThrow(new OptimisticLockingFailureException("UiSchemaDef", 55L, 0L));

    assertThatThrownBy(() -> service.saveDraftSchema(9001L, json(schema("")), 0L))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(CatalogErrorCodes.UI_SCHEMA_VERSION_CONFLICT));
  }

  @Test
  @DisplayName("缺 If-Match 且行已存在 → 412 防误覆盖")
  void saveDraftSchema_shouldRequirePrecondition_whenNoIfMatch() {
    UiSchemaDef def = row(schema(""), 3L);
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L)).thenReturn(Optional.of(def));

    assertThatThrownBy(() -> service.saveDraftSchema(9001L, json(schema("")), null))
        .isInstanceOf(BizException.class);
  }

  @Test
  @DisplayName("结构非法（大版本非 1）→ 400 UI_SCHEMA_INVALID")
  void saveDraftSchema_shouldReject400_whenBadMajor() {
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.saveDraftSchema(9001L, json("{\"schemaVersion\":2,\"views\":{}}"), null))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(CatalogErrorCodes.UI_SCHEMA_INVALID));
  }

  @Test
  @DisplayName("UI-REF-01：控件引用未知字段 → ERROR")
  void validateSchema_shouldFlagUnknownField_whenControlRefMissing() {
    String bad =
        schema(
            "\"linkageRules\":[],\"submitRules\":[],\"actionBar\":[],"
                + "\"views\":{\"form\":{\"sections\":[{\"id\":\"s\",\"controls\":[{\"fieldCode\":\"nope\"}]}]}}");
    // schema() 已带空 form；此处 views 被覆盖以注入坏控件
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L))
        .thenReturn(Optional.of(row(bad, 0L)));

    var issues = service.validateSchema(9001L);

    assertThat(issues).anyMatch(i -> UiSchemaRefCodes.UI_REF_01.equals(i.getCode()));
  }

  @Test
  @DisplayName("UI-REF-02：actionCode 不在动作定义中 → ERROR")
  void validateSchema_shouldFlagUnknownAction_whenActionCodeMissing() {
    String bad =
        "{\"schemaVersion\":1,\"entityCode\":\"exp\",\"views\":{\"form\":{\"sections\":[]}},"
            + "\"actionBar\":[{\"actionCode\":\"ghost\"}]}";
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L))
        .thenReturn(Optional.of(row(bad, 0L)));

    var issues = service.validateSchema(9001L);

    assertThat(issues).anyMatch(i -> UiSchemaRefCodes.UI_REF_02.equals(i.getCode()));
  }

  @Test
  @DisplayName("UI-REF-05/06/09：深层嵌套 + 未知函数 + 缺 code 警告")
  void validateSchema_shouldFlagDepthFunctionAndCode() {
    String deep =
        "{\"all\":[{\"all\":[{\"all\":[{\"all\":[{\"fieldCode\":\"title\",\"operator\":\"eq\",\"value\":\"x\"}]}]}]}]}";
    String bad =
        "{\"schemaVersion\":1,\"entityCode\":\"exp\",\"views\":{\"form\":{\"sections\":[]}},"
            + "\"linkageRules\":[{\"when\":"
            + deep
            + ",\"then\":[{\"fieldCode\":\"title\"}]}],"
            + "\"submitRules\":[{\"name\":\"坏函数\",\"function\":\"avg\",\"fields\":[\"amount\"],"
            + "\"operator\":\"eq\",\"compare\":{\"valueType\":\"fixed\",\"value\":1},"
            + "\"verifyType\":\"ERROR\",\"message\":\"x\"}]}";
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L))
        .thenReturn(Optional.of(row(bad, 0L)));

    var issues = service.validateSchema(9001L);

    assertThat(issues).anyMatch(i -> UiSchemaRefCodes.UI_REF_05.equals(i.getCode()));
    assertThat(issues).anyMatch(i -> UiSchemaRefCodes.UI_REF_06.equals(i.getCode()));
    assertThat(issues)
        .anyMatch(
            i -> UiSchemaRefCodes.UI_REF_09.equals(i.getCode()) && "WARNING".equals(i.getLevel()));
  }

  @Test
  @DisplayName("合法 schema 零问题（含 when 前置的 submit 规则）")
  void validateSchema_shouldPass_whenValid() {
    String ok =
        "{\"schemaVersion\":1,\"entityCode\":\"exp\",\"views\":{\"form\":{\"sections\":["
            + "{\"id\":\"s\",\"controls\":[{\"fieldCode\":\"title\"},{\"fieldCode\":\"amount\"}]}]}},"
            + "\"linkageRules\":[{\"code\":\"LINK-01\",\"when\":{\"fieldCode\":\"title\",\"operator\":\"eq\",\"value\":\"x\"},"
            + "\"then\":[{\"fieldCode\":\"amount\",\"required\":true}]}],"
            + "\"submitRules\":[{\"code\":\"SUB-01\",\"function\":\"sum\",\"fields\":[\"amount\"],"
            + "\"operator\":\"eq\",\"compare\":{\"valueType\":\"fixed\",\"value\":1},"
            + "\"when\":{\"fieldCode\":\"title\",\"operator\":\"isNotNull\"},"
            + "\"verifyType\":\"ERROR\",\"message\":\"合计不对\"}],"
            + "\"actionBar\":[{\"actionCode\":\"submit\"}]}";
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L))
        .thenReturn(Optional.of(row(ok, 0L)));

    assertThat(service.validateSchema(9001L)).isEmpty();
  }

  @Test
  @DisplayName("行策略对象形态（引擎 0038 rules[].name）可被 dataScope.policyCode 引用")
  void validateSchema_shouldAcceptObjectFormRowPolicies() {
    MetaEntity policyEntity = mock(MetaEntity.class);
    when(policyEntity.getId()).thenReturn(9001L);
    when(policyEntity.getCode()).thenReturn("exp");
    when(policyEntity.getRowPolicies())
        .thenReturn(
            "{\"owner_field\":\"created_by\",\"rules\":[{\"name\":\"owner_or_approver\",\"effect\":\"allow\"}]}");
    when(metaEntityRepository.findById(9001L)).thenReturn(policyEntity);
    String doc =
        "{\"schemaVersion\":1,\"entityCode\":\"exp\","
            + "\"views\":{\"form\":{\"sections\":[]},"
            + "\"list\":{\"dataScope\":{\"policyCode\":\"owner_or_approver\"}}},"
            + "\"linkageRules\":[],\"submitRules\":[],\"actionBar\":[]}";
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L))
        .thenReturn(Optional.of(row(doc, 0L)));

    assertThat(service.validateSchema(9001L)).isEmpty();
  }

  @Test
  @DisplayName("UI-REF-04：变体虚拟控件与真实字段码碰撞 → ERROR（复刻线上实证：payable_amount 既真字段又 COMPUTED 虚拟控件）")
  void validateSchema_shouldFlagVirtualCollision_whenVariantControlShadowsRealField() {
    // 与 2026-10-18 诊断实证同构：变体「核赔工作台」的 COMPUTED 虚拟控件 fieldCode=amount
    // （真实字段），base 视图正常引用 title。修复前此后端循环不查碰撞 ⇒ 直通发布闸门。
    String bad =
        "{\"schemaVersion\":1,\"entityCode\":\"exp\","
            + "\"views\":{\"form\":{\"sections\":[{\"id\":\"s\",\"controls\":[{\"fieldCode\":\"title\"}]}]}},"
            + "\"variants\":[{\"key\":\"reviewer_console\",\"label\":\"核赔工作台\","
            + "\"match\":{\"roles\":[\"reviewer\",\"admin\"]},\"priority\":1,"
            + "\"views\":{\"form\":{\"sections\":[{\"id\":\"v\",\"controls\":["
            + "{\"fieldCode\":\"amount\",\"controlKind\":\"COMPUTED\",\"virtual\":true,"
            + "\"props\":{\"computedFn\":\"payableAmount\",\"computedFrom\":[\"title\"]}}]}]}}}]}";
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L))
        .thenReturn(Optional.of(row(bad, 0L)));

    var issues = service.validateSchema(9001L);

    assertThat(issues)
        .anyMatch(
            i ->
                UiSchemaRefCodes.UI_REF_04.equals(i.getCode())
                    && "ERROR".equals(i.getLevel())
                    && i.getMessage().contains("amount")
                    && i.getMessage().contains("reviewer_console"));
  }

  @Test
  @DisplayName("变体虚拟控件用非字段码（decision_trace）→ 零误报（回归：防校验过宽误杀合法 TIMELINE）")
  void validateSchema_shouldNotFlagVirtual_whenCodeNotRealField() {
    String ok =
        "{\"schemaVersion\":1,\"entityCode\":\"exp\","
            + "\"views\":{\"form\":{\"sections\":[{\"id\":\"s\",\"controls\":[{\"fieldCode\":\"title\"}]}]}},"
            + "\"variants\":[{\"key\":\"reviewer_console\",\"label\":\"核赔工作台\","
            + "\"match\":{\"roles\":[\"reviewer\"]},"
            + "\"views\":{\"form\":{\"sections\":[{\"id\":\"v\",\"controls\":["
            + "{\"fieldCode\":\"decision_trace\",\"controlKind\":\"TIMELINE\",\"virtual\":true},"
            + "{\"fieldCode\":\"amount\"}]}]}}}]}";
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L))
        .thenReturn(Optional.of(row(ok, 0L)));

    assertThat(service.validateSchema(9001L)).isEmpty();
  }

  @Test
  @DisplayName("UI-REF-01：变体控件缺 fieldCode → ERROR（与 base 视图同口径）")
  void validateSchema_shouldFlagMissingFieldCode_whenVariantControlUnbound() {
    String bad =
        "{\"schemaVersion\":1,\"entityCode\":\"exp\","
            + "\"views\":{\"form\":{\"sections\":[{\"id\":\"s\",\"controls\":[{\"fieldCode\":\"title\"}]}]}},"
            + "\"variants\":[{\"key\":\"v1\",\"match\":{},\"views\":{\"form\":{\"sections\":["
            + "{\"id\":\"v\",\"controls\":[{\"controlKind\":\"TEXT\"}]}]}}}]}";
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L))
        .thenReturn(Optional.of(row(bad, 0L)));

    assertThat(service.validateSchema(9001L))
        .anyMatch(
            i ->
                UiSchemaRefCodes.UI_REF_01.equals(i.getCode())
                    && i.getMessage().contains("缺 fieldCode"));
  }

  @Test
  @DisplayName("UI-REF-01：列表页签绑定不存在字段 → ERROR（页签是硬过滤，悬空即恒空；与前端 lint 同口径）")
  void validateSchema_shouldFlagUnknownField_whenListTabDangling() {
    // 复刻线上 fixture 形态：待我审核页签绑 reviewer_id（实体无此字段）。修复前该形态前端仅
    // warning、后端不查 ⇒ 保存/发布都不拦，用户线上点到该页签恒空。
    String bad =
        "{\"schemaVersion\":1,\"entityCode\":\"exp\","
            + "\"views\":{\"form\":{\"sections\":[{\"id\":\"s\",\"controls\":[{\"fieldCode\":\"title\"}]}]},"
            + "\"list\":{\"tabs\":[{\"title\":\"待我审核\",\"fieldCode\":\"reviewer_id\",\"value\":\"$me\"}]}}}";
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L))
        .thenReturn(Optional.of(row(bad, 0L)));

    assertThat(service.validateSchema(9001L))
        .anyMatch(
            i ->
                UiSchemaRefCodes.UI_REF_01.equals(i.getCode())
                    && "ERROR".equals(i.getLevel())
                    && i.getMessage().contains("tabs.fieldCode 引用未知字段")
                    && i.getMessage().contains("reviewer_id"));
  }

  @Test
  @DisplayName("列表页签绑真实字段 + $me 变量 ⇒ 零误报（回归：防把合法页签当悬空）")
  void validateSchema_shouldNotFlag_whenListTabBoundToRealField() {
    String ok =
        "{\"schemaVersion\":1,\"entityCode\":\"exp\","
            + "\"views\":{\"form\":{\"sections\":[{\"id\":\"s\",\"controls\":[{\"fieldCode\":\"title\"}]}]},"
            + "\"list\":{\"tabs\":[{\"title\":\"待我审批\",\"fieldCode\":\"amount\",\"value\":\"$me\"}]}}}";
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L))
        .thenReturn(Optional.of(row(ok, 0L)));

    assertThat(service.validateSchema(9001L)).isEmpty();
  }

  private static BadSqlGrammarException missingTable() {
    return new BadSqlGrammarException(
        "find", "SELECT * FROM meta_entity_ui_schema", new SQLException("Table doesn't exist"));
  }

  @Test
  @DisplayName("0046 未执行：读/批量/发布/删联动静默降级，写路径 503（前置诚实门 §5.2）")
  void missingTable_shouldDegradeReadsAnd503Writes() {
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L)).thenThrow(missingTable());
    when(uiSchemaDefRepository.findByTenantAndEntities(7L, List.of(9001L)))
        .thenThrow(missingTable());

    // 读：空草稿 + 空批量（前端回落列投影，不炸 500）
    assertThat(service.getDraftSchema(9001L)).isEmpty();
    assertThat(service.schemaEntityIds(List.of(9001L))).isEmpty();
    // 发布/删联动：no-op 不抛
    service.snapshotOnPublish(9001L, 5);
    service.deleteByEntity(9001L);

    // 写：503 + 资产缺失码（thenThrow 后重 stub 须用 do* 形态，否则 stub 表达式本身先抛）
    doReturn(Optional.empty()).when(uiSchemaDefRepository).findByTenantAndEntity(7L, 9001L);
    doThrow(missingTable()).when(uiSchemaDefRepository).insert(any(UiSchemaDef.class));
    assertThatThrownBy(() -> service.saveDraftSchema(9001L, json(schema("")), null))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(CatalogErrorCodes.UI_SCHEMA_STORE_UNAVAILABLE));
  }

  @Test
  @DisplayName("发布快照：无行 no-op；有行冻结草稿 + 对账版本")
  void snapshotOnPublish_shouldFreezeDraft_whenRowExists() {
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L)).thenReturn(Optional.empty());
    service.snapshotOnPublish(9001L, 5);
    verify(uiSchemaDefRepository, org.mockito.Mockito.never()).update(any());

    UiSchemaDef def = row(schema(""), 0L);
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L)).thenReturn(Optional.of(def));
    service.snapshotOnPublish(9001L, 5);

    assertThat(def.getPublishedVersion()).isEqualTo(5);
    assertThat(def.getPublishedSchemaJson()).isEqualTo(def.getSchemaJson());
    verify(uiSchemaDefRepository).update(def);
  }

  @Test
  @DisplayName("实例化拷贝：namespace 服务端重写 + base 指针；空资产跳过不阻断")
  void copyFromTemplate_shouldRewriteNamespaceAndPointer() {
    service.copyFromTemplate(
        9001L,
        "exp_copy",
        "EXPENSE",
        "v3",
        "{\"schemaVersion\":1,\"entityCode\":\"t\",\"views\":{},"
            + "\"i18n\":{\"namespace\":\"bone.ui.EXPENSE_TPL\"}}");

    var captor = org.mockito.ArgumentCaptor.forClass(UiSchemaDef.class);
    verify(uiSchemaDefRepository).insert(captor.capture());
    UiSchemaDef def = captor.getValue();
    assertThat(def.getSchemaJson()).contains("\"namespace\":\"bone.ui.exp_copy\"");
    assertThat(def.getBaseTemplateCode()).isEqualTo("EXPENSE");
    assertThat(def.getBaseVersion()).isEqualTo("v3");

    service.copyFromTemplate(9001L, "exp_copy", "EMPTY", "v1", "  ");
    verify(uiSchemaDefRepository, org.mockito.Mockito.times(1)).insert(any());
  }

  @Test
  @DisplayName("跟随检查：无指针 → NO_BASE_POINTER；资产缺失 → TEMPLATE_ASSET_INCOMPLETE")
  void checkBaseUpdate_shouldDegradeHonestly() {
    UiSchemaDef plain = row(schema(""), 0L);
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L)).thenReturn(Optional.of(plain));
    assertThat(service.checkBaseUpdate(9001L).getReason()).isEqualTo("NO_BASE_POINTER");

    UiSchemaDef based = row(schema(""), 0L);
    based.markFromTemplate("EXPENSE", "v2");
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L)).thenReturn(Optional.of(based));
    when(templateReadPort.findUiSnapshotByCode("EXPENSE")).thenReturn(Optional.empty());
    assertThat(service.checkBaseUpdate(9001L).getReason()).isEqualTo("TEMPLATE_ASSET_INCOMPLETE");
  }

  @Test
  @DisplayName("跟随检查：模板发新版 → hasUpdate + digest 计数")
  void checkBaseUpdate_shouldDetectUpdate_whenTemplateMoved() {
    String oldSection =
        "{\"id\":\"s1\",\"controls\":[{\"fieldCode\":\"title\"}]},{\"id\":\"s2\",\"controls\":[]}";
    String instance =
        "{\"schemaVersion\":1,\"entityCode\":\"exp\",\"views\":{\"form\":{\"sections\":["
            + oldSection
            + "]}},\"linkageRules\":[],\"submitRules\":[],\"actionBar\":[]}";
    String baseNew =
        "{\"schemaVersion\":1,\"entityCode\":\"t\",\"views\":{\"form\":{\"sections\":["
            + oldSection
            + ",{\"id\":\"s3\",\"controls\":[]}]}}}";
    UiSchemaDef based = row(instance, 0L);
    based.markFromTemplate("EXPENSE", "v2");
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L)).thenReturn(Optional.of(based));
    when(templateReadPort.findUiSnapshotByCode("EXPENSE"))
        .thenReturn(Optional.of(new TemplateUiSnapshot("v3", baseNew)));

    var check = service.checkBaseUpdate(9001L);

    assertThat(check.isHasUpdate()).isTrue();
    assertThat(check.getCurrentVersion()).isEqualTo("v3");
    assertThat(check.getSectionAdded()).isEqualTo(1);
  }

  @Test
  @DisplayName("三路归并：租户未改段自动取新版；双方都改 → 冲突")
  void previewMerge_shouldAutoMergeUntouched_whenThreeWay() {
    String sec1 = "{\"id\":\"s1\",\"controls\":[{\"fieldCode\":\"title\"}]}";
    String sec2old = "{\"id\":\"s2\",\"controls\":[]}";
    String sec2new = "{\"id\":\"s2\",\"controls\":[{\"fieldCode\":\"amount\"}]}";
    String sec2mine = "{\"id\":\"s2\",\"controls\":[{\"fieldCode\":\"title\"}]}";
    String doc =
        "{\"schemaVersion\":1,\"entityCode\":\"e\",\"views\":{\"form\":{\"sections\":[%s]}},"
            + "\"linkageRules\":[],\"submitRules\":[],\"actionBar\":[]}";
    UiSchemaDef based = row(String.format(doc, sec1 + "," + sec2mine), 0L);
    based.markFromTemplate("EXPENSE", "v2");
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L)).thenReturn(Optional.of(based));
    when(templateReadPort.findUiSnapshotByCode("EXPENSE"))
        .thenReturn(
            Optional.of(new TemplateUiSnapshot("v3", String.format(doc, sec1 + "," + sec2new))));
    when(templateReadPort.findUiHistorySnapshot("EXPENSE", "v2"))
        .thenReturn(Optional.of(String.format(doc, sec1 + "," + sec2old)));

    MergePreview preview = service.previewMerge(9001L);

    assertThat(preview.isDegradedTwoWay()).isFalse();
    assertThat(preview.getConflicts()).extracting("path").contains("section:form:s2");
    assertThat(preview.getAutoMerged()).isEmpty();
  }

  @Test
  @DisplayName("无历史快照 → 两路降级（冲突面全量标出 + degradedTwoWay）")
  void previewMerge_shouldDegradeTwoWay_whenNoHistory() {
    UiSchemaDef based = row(schema(""), 0L);
    based.markFromTemplate("EXPENSE", "v2");
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L)).thenReturn(Optional.of(based));
    when(templateReadPort.findUiSnapshotByCode("EXPENSE"))
        .thenReturn(Optional.of(new TemplateUiSnapshot("v3", schema(""))));
    when(templateReadPort.findUiHistorySnapshot("EXPENSE", "v2")).thenReturn(Optional.empty());

    MergePreview preview = service.previewMerge(9001L);

    assertThat(preview.isDegradedTwoWay()).isTrue();
  }

  @Test
  @DisplayName("合入确认：模板又发版 → 409（旧 preview 作废重跑）")
  void confirmMerge_shouldConflict409_whenTemplateMoved() {
    UiSchemaDef based = row(schema(""), 0L);
    based.markFromTemplate("EXPENSE", "v2");
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L)).thenReturn(Optional.of(based));
    when(templateReadPort.findUiSnapshotByCode("EXPENSE"))
        .thenReturn(Optional.of(new TemplateUiSnapshot("v4", schema(""))));

    assertThatThrownBy(() -> service.confirmMerge(9001L, true, "v3", 0L))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(CatalogErrorCodes.UI_SCHEMA_MERGE_CONFLICT));
  }

  @Test
  @DisplayName("列表 hasUiSchema 批量：空入参空出（禁 N+1 的前置）")
  void schemaEntityIds_shouldReturnEmpty_whenNoIds() {
    assertThat(service.schemaEntityIds(List.of())).isEmpty();
    assertThat(service.schemaEntityIds(null)).isEmpty();
  }

  @Test
  @DisplayName("删实体联动删 schema 行")
  void deleteByEntity_shouldDeleteRow_whenExists() {
    UiSchemaDef def = row(schema(""), 0L);
    def.setId(55L);
    when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L)).thenReturn(Optional.of(def));

    service.deleteByEntity(9001L);

    verify(uiSchemaDefRepository).deleteById(55L);
  }
}
