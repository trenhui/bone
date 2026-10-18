package com.bone.metadata.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.domain.gateway.TemplateReadPort;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.gateway.UiSchemaRefCheckPort;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.model.meta.MetaField;
import com.bone.metadata.catalog.domain.model.ui.UiSchemaDef;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import com.bone.metadata.catalog.domain.repository.UiSchemaDefRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 前后端校验规则对账契约（2026-10-18 诊断 P0-2 的防复发闸门）。
 *
 * <p>背景：前端 lint（{@code uiSchema/lint.ts}）与后端 {@code validateNodes} 是同一份协议的两个校验器——
 * 前端管「保存前快速反馈」，后端管「发布闸门」。诊断实证的事故形态是<b>规则作用域漂移</b>： 前端对 base 与变体两个作用域都报「虚拟控件碰撞」，后端只查 base ⇒ 碰撞型
 * schema 直通发布上线。
 *
 * <p>本契约把「前端 lint 的 error 级规则 ⇒ 后端同码拦截」固化为机器断言：矩阵中每条违规都跑 <b>base
 * 与变体两个作用域</b>。任何人删掉后端任一侧检查（或新规则只加前端），对应用例即变红—— 规则漂移从此有机器防线，不再依赖 reviewer 记住「后端也要同步改」。
 *
 * <p>所有权划分（与 lint.ts 头部注释一致，互补不重叠）：
 *
 * <ul>
 *   <li>后端发布闸门（本契约覆盖，base/变体同口径）：控件缺 fieldCode、字段不存在、virtual 碰撞、 联动 then 目标、动作码、提交函数、tabs $ 变量、tabs
 *       字段存在性、dataScope 策略码；
 *   <li>前端独有（本地可判，后端零校验，本契约不断言）：props 白名单越界、字段重复出现、 pageHeader 悬空引用、列表列/搜索项空码、字典覆写越界。
 * </ul>
 *
 * <p>fixture 与 {@link UiSchemaApplicationServiceTest} 同口径：实体 9001（exp），字段 title/amount， 动作
 * submit/approve，行策略空集（policyCodes=∅ ⇒ 任意 policyCode 即违规）。
 */
class UiSchemaRuleParityTest {

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

  /** 对账用例：违规 schema ⇒ 后端必须产出的问题码与文案关键片段。 */
  private record ParityCase(String name, String doc, String code, String hint) {}

  @BeforeEach
  void setUp() {
    MetaEntity entity =
        MetaEntity.create(9001L, 7L, "报销", "exp", "报销", null, "t_exp", 0, 1, null, 1L);
    when(tenantProvider.currentTenantId()).thenReturn(7L);
    when(metaEntityRepository.findById(9001L)).thenReturn(entity);
    when(metaFieldRepository.pageFields(eq(9001L), eq(null), eq(1), eq(500)))
        .thenReturn(PageResult.of(List.of(field("title"), field("amount")), 2L, 1, 500));
    when(refCheckPort.actionCodesOf(7L, "exp")).thenReturn(Set.of("submit", "approve"));
  }

  private static MetaField field(String code) {
    return MetaField.create(null, 7L, 9001L, code, code, code, "STRING");
  }

  private UiSchemaDef row(String schemaJson) {
    UiSchemaDef def = UiSchemaDef.create(7L, 9001L, schemaJson, 1);
    def.setId(55L);
    return def;
  }

  @Test
  @DisplayName("规则对账矩阵：前端 lint 的 error 级规则，后端在 base 与变体两个作用域均同码拦截")
  void errorRules_shouldBeEnforcedByBackendGate_inBothScopes() {
    List<ParityCase> cases =
        List.of(
            /* ---------- base 作用域 ---------- */
            new ParityCase(
                "base 控件缺 fieldCode",
                "{\"schemaVersion\":1,\"entityCode\":\"exp\",\"views\":{\"form\":{\"sections\":[{\"id\":\"s\","
                    + "\"controls\":[{\"controlKind\":\"INPUT\"}]}]}}}",
                "UI-REF-01",
                "缺 fieldCode"),
            new ParityCase(
                "base 控件引用不存在字段",
                "{\"schemaVersion\":1,\"entityCode\":\"exp\",\"views\":{\"form\":{\"sections\":[{\"id\":\"s\","
                    + "\"controls\":[{\"fieldCode\":\"ghost\"}]}]}}}",
                "UI-REF-01",
                "字段不存在"),
            new ParityCase(
                "base 虚拟控件撞真实字段",
                "{\"schemaVersion\":1,\"entityCode\":\"exp\",\"views\":{\"form\":{\"sections\":[{\"id\":\"s\","
                    + "\"controls\":[{\"fieldCode\":\"amount\",\"virtual\":true,\"controlKind\":\"COMPUTED\"}]}]}}}",
                "UI-REF-04",
                "碰撞"),
            new ParityCase(
                "base 联动 then 目标未知",
                "{\"schemaVersion\":1,\"entityCode\":\"exp\",\"views\":{\"form\":{\"sections\":[{\"id\":\"s\","
                    + "\"controls\":[{\"fieldCode\":\"title\"}]}]}},"
                    + "\"linkageRules\":[{\"then\":[{\"fieldCode\":\"ghost\",\"value\":1}]}]}",
                "UI-REF-01",
                "then 目标未知"),
            new ParityCase(
                "base 动作码未注册",
                "{\"schemaVersion\":1,\"entityCode\":\"exp\",\"views\":{\"form\":{\"sections\":[]}},"
                    + "\"actionBar\":[{\"actionCode\":\"ghost_act\"}]}",
                "UI-REF-02",
                "未在动作定义中"),
            new ParityCase(
                "base 提交函数未知",
                "{\"schemaVersion\":1,\"entityCode\":\"exp\",\"views\":{\"form\":{\"sections\":[]}},"
                    + "\"submitRules\":[{\"function\":\"ghost_fn\",\"fields\":[\"title\"]}]}",
                "UI-REF-06",
                "未知函数"),
            new ParityCase(
                "base tabs $ 变量未知",
                "{\"schemaVersion\":1,\"entityCode\":\"exp\",\"views\":{\"form\":{\"sections\":[]},"
                    + "\"list\":{\"tabs\":[{\"fieldCode\":\"title\",\"value\":\"$ghost\"}]}}}",
                "UI-REF-07",
                "未知上下文变量"),
            new ParityCase(
                "base tabs 绑定不存在字段",
                "{\"schemaVersion\":1,\"entityCode\":\"exp\",\"views\":{\"form\":{\"sections\":[]},"
                    + "\"list\":{\"tabs\":[{\"fieldCode\":\"ghost\",\"value\":\"$me\"}]}}}",
                "UI-REF-01",
                "tabs.fieldCode 引用未知字段"),
            new ParityCase(
                "base dataScope 策略码未声明",
                "{\"schemaVersion\":1,\"entityCode\":\"exp\",\"views\":{\"form\":{\"sections\":[]},"
                    + "\"list\":{\"dataScope\":{\"policyCode\":\"ghost_policy\"}}}}",
                "UI-REF-03",
                "未在 row_policies 声明"),
            /* ---------- 变体作用域（事故原爆点：前端查、后端曾漏） ---------- */
            new ParityCase(
                "变体控件缺 fieldCode",
                variantDoc("[{\"fieldCode\":\"title\"}]", "[{\"controlKind\":\"INPUT\"}]"),
                "UI-REF-01",
                "变体 v1 控件缺 fieldCode"),
            new ParityCase(
                "变体控件引用不存在字段",
                variantDoc("[{\"fieldCode\":\"title\"}]", "[{\"fieldCode\":\"ghost\"}]"),
                "UI-REF-01",
                "变体 v1 控件引用的字段不存在"),
            new ParityCase(
                "变体虚拟控件撞真实字段",
                variantDoc(
                    "[{\"fieldCode\":\"title\"}]",
                    "[{\"fieldCode\":\"amount\",\"virtual\":true,\"controlKind\":\"COMPUTED\"}]"),
                "UI-REF-04",
                "变体 v1 控件与真实字段码碰撞"),
            new ParityCase(
                "变体联动 then 目标未知",
                variantDocWithRule(
                    "linkageRules", "[{\"then\":[{\"fieldCode\":\"ghost\",\"value\":1}]}]"),
                "UI-REF-01",
                "变体 v1 linkage[?] then 目标未知"),
            new ParityCase(
                "变体动作码未注册",
                variantDocWithRule("actionBar", "[{\"actionCode\":\"ghost_act\"}]"),
                "UI-REF-02",
                "变体 v1 actionCode 未在动作定义中"),
            new ParityCase(
                "变体提交函数未知",
                variantDocWithRule(
                    "submitRules", "[{\"function\":\"ghost_fn\",\"fields\":[\"title\"]}]"),
                "UI-REF-06",
                "变体 v1 submit[?] 未知函数"),
            new ParityCase(
                "变体 tabs $ 变量未知",
                variantListDoc("{\"tabs\":[{\"fieldCode\":\"title\",\"value\":\"$ghost\"}]}"),
                "UI-REF-07",
                "变体 v1 tabs.value 未知上下文变量"),
            new ParityCase(
                "变体 tabs 绑定不存在字段",
                variantListDoc("{\"tabs\":[{\"fieldCode\":\"ghost\",\"value\":\"$me\"}]}"),
                "UI-REF-01",
                "变体 v1 tabs.fieldCode 引用未知字段"),
            new ParityCase(
                "变体 dataScope 策略码未声明",
                variantListDoc("{\"dataScope\":{\"policyCode\":\"ghost_policy\"}}"),
                "UI-REF-03",
                "变体 v1 dataScope.policyCode 未在 row_policies 声明"));

    List<String> drift = new ArrayList<>();
    for (ParityCase c : cases) {
      when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L))
          .thenReturn(Optional.of(row(c.doc())));
      boolean caught =
          service.validateSchema(9001L).stream()
              .anyMatch(i -> c.code().equals(i.getCode()) && i.getMessage().contains(c.hint()));
      if (!caught) {
        drift.add(c.name() + "：后端未按 " + c.code() + " 拦截（前端 lint 该规则为 error，发布闸门漏拦）");
      }
    }
    assertThat(drift).isEmpty();
  }

  /** 变体（key=v1）与 base 各自声明 form 视图；base 用合法控件（隔离变量，确保只有变体作用域违规）。 */
  private static String variantDoc(String baseControls, String variantControls) {
    return "{\"schemaVersion\":1,\"entityCode\":\"exp\",\"views\":{\"form\":{\"sections\":[{\"id\":\"s\",\"controls\":"
        + baseControls
        + "}]}},\"variants\":[{\"key\":\"v1\",\"label\":\"核赔工作台\",\"match\":{\"roles\":[\"reviewer\"]},"
        + "\"views\":{\"form\":{\"sections\":[{\"id\":\"v\",\"controls\":"
        + variantControls
        + "}]}}}]}";
  }

  /** 变体在顶层声明规则块（linkage/submit/actionBar），form 视图合法——隔离出规则块本身的违规。 */
  private static String variantDocWithRule(String ruleKey, String ruleJson) {
    return "{\"schemaVersion\":1,\"entityCode\":\"exp\",\"views\":{\"form\":{\"sections\":[{\"id\":\"s\","
        + "\"controls\":[{\"fieldCode\":\"title\"}]}]}},"
        + "\"variants\":[{\"key\":\"v1\",\"label\":\"核赔工作台\",\"match\":{\"roles\":[\"reviewer\"]},"
        + "\""
        + ruleKey
        + "\":"
        + ruleJson
        + ",\"views\":{\"form\":{\"sections\":[{\"id\":\"v\",\"controls\":[{\"fieldCode\":\"title\"}]}]}}}]}";
  }

  /** 变体只声明 list 视图（内容为入参 JSON 对象），base 不声明 list——隔离出变体 list 检查。 */
  private static String variantListDoc(String listJson) {
    return "{\"schemaVersion\":1,\"entityCode\":\"exp\",\"views\":{\"form\":{\"sections\":[]}},"
        + "\"variants\":[{\"key\":\"v1\",\"label\":\"核赔工作台\",\"match\":{\"roles\":[\"reviewer\"]},"
        + "\"views\":{\"list\":"
        + listJson
        + "}}]}";
  }
}
