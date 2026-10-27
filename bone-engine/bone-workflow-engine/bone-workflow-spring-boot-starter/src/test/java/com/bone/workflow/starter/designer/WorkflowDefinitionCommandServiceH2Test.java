package com.bone.workflow.starter.designer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.domain.WorkflowDefinitionValidator;
import com.bone.workflow.starter.sdk.WfProcessDefSdkRepository;
import com.bone.workflow.starter.sdk.WfProcessDraftSdkRepository;
import com.bone.workflow.starter.sdk.WorkflowSdkTestContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 设计态写路径的 H2 回归：草稿 CAS、发布 fail-closed、版本号水位、{@code ui} 段保真。
 *
 * <p><b>本类钉住的四条纪律</b>（任一条被放松，对应用例必红）：
 *
 * <ol>
 *   <li>草稿可脏：不完整定义能存草稿，但违规清单必须一并回传；
 *   <li>发布必净：有违规时 {@code wf_process_def} 不得新增行（fail-closed，不是「先发后补」）；
 *   <li>revision CAS：拿旧 revision 覆盖必须 409，不允许静默覆盖他人修改；
 *   <li>JSON 原样：{@code ui} 段必须原封不动落库并读回——一旦改成「反序列化后重新序列化」，版式会每次发布后归零。
 * </ol>
 */
class WorkflowDefinitionCommandServiceH2Test {

  private static final long TENANT = 1L;
  private static final String DEF_KEY = "expense_designer";

  private AnnotationConfigApplicationContext context;
  private NamedParameterJdbcTemplate named;
  private WorkflowDefinitionCommandService service;

  /** 合法定义（含 ui 段：布局是引擎不读、但设计器必须保真的部分）。 */
  private static final String VALID_JSON =
      """
      {"key":"expense_designer","name":"费用报销（设计器）","version":1,"engineSchemaVersion":1,
       "nodes":[{"code":"start","type":"START","label":"发起"},
                {"code":"mgr","type":"APPROVE","mode":"SINGLE","label":"直属主管"},
                {"code":"end","type":"END","label":"结束"}],
       "edges":[{"from":"start","to":"mgr"},{"from":"mgr","to":"end"}],
       "assignments":{"mgr":[{"strategy":"LINE_MANAGER","params":{}}]},
       "ui":{"layout":{"start":{"x":0,"y":0},"mgr":{"x":0,"y":120}}}}
      """;

  /** 结构上缺 END、且策略缺参：草稿可存，发布必须被拒。 */
  private static final String DIRTY_JSON =
      """
      {"key":"expense_designer","name":"费用报销（半成品）","version":1,
       "nodes":[{"code":"start","type":"START","label":"发起"},
                {"code":"mgr","type":"APPROVE","mode":"SINGLE","label":"主管"}],
       "edges":[{"from":"start","to":"mgr"}],
       "assignments":{"mgr":[{"strategy":"ROLE","params":{}}]}}
      """;

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(TENANT);
    context = WorkflowSdkTestContext.create();
    named = context.getBean(NamedParameterJdbcTemplate.class);
    AtomicInteger sequence = new AtomicInteger();
    service =
        new WorkflowDefinitionCommandService(
            context.getBean(WfProcessDraftSdkRepository.class),
            context.getBean(WfProcessDefSdkRepository.class),
            new WorkflowDefinitionValidator(),
            () -> "WF-ID-" + sequence.incrementAndGet(),
            context.getBean(ObjectMapper.class));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    if (context != null) {
      context.close();
    }
  }

  @Test
  @DisplayName("首存草稿 → revision=1；不完整定义也能存，但违规一并回传")
  void saveDraft_firstRevisionIsOne_andViolationsAreReturned() {
    WorkflowDefinitionCommandService.SaveDraftResult result =
        service.saveDraft(DEF_KEY, DIRTY_JSON, null, "u-designer");

    assertThat(result.draft().revision()).isEqualTo(1);
    assertThat(result.draft().defName()).isEqualTo("费用报销（半成品）");
    assertThat(result.violations()).isNotEmpty();
    assertThat(result.violations())
        .extracting(WorkflowDefinitionValidator.Violation::code)
        .contains("WF-020");
    assertThat(service.findDraft(DEF_KEY)).isPresent();
  }

  @Test
  @DisplayName("覆盖草稿：带对 revision 成功并自增；带旧 revision 必须 409")
  void saveDraft_isRevisionGuarded() {
    service.saveDraft(DEF_KEY, DIRTY_JSON, null, "u1");

    WorkflowDefinitionCommandService.SaveDraftResult second =
        service.saveDraft(DEF_KEY, VALID_JSON, 1, "u2");
    assertThat(second.draft().revision()).isEqualTo(2);

    assertThatThrownBy(() -> service.saveDraft(DEF_KEY, DIRTY_JSON, 1, "u3"))
        .isInstanceOf(WorkflowDefinitionConflictException.class)
        .hasMessageContaining("已被他人修改");

    assertThatThrownBy(() -> service.saveDraft(DEF_KEY, DIRTY_JSON, null, "u3"))
        .isInstanceOf(WorkflowDefinitionConflictException.class)
        .hasMessageContaining("revision");
  }

  @Test
  @DisplayName("发布成功 → v1 落库、草稿清空、definition_json 原样保留 ui 段")
  void publish_persistsImmutableSnapshotAndKeepsUiSegment() {
    service.saveDraft(DEF_KEY, VALID_JSON, null, "u1");
    WorkflowDefinitionCommandService.Published published = service.publish(DEF_KEY, 1, "u1");

    assertThat(published.version()).isEqualTo(1);
    assertThat(published.enabled()).isTrue();
    assertThat(service.findDraft(DEF_KEY)).isEmpty();
    assertThat(countPublished()).isEqualTo(1);

    WorkflowDefinitionCommandService.Published reloaded =
        service.findVersion(DEF_KEY, 1).orElseThrow();
    assertThat(reloaded.definitionJson()).contains("\"ui\"").contains("\"layout\"");
    assertThat(reloaded.definitionJson()).isEqualTo(VALID_JSON);
  }

  @Test
  @DisplayName("发布 fail-closed：草稿有违规 → 拒绝发布且不得落任何版本行")
  void publish_rejectsInvalidDraft_withoutTouchingVersionTable() {
    service.saveDraft(DEF_KEY, DIRTY_JSON, null, "u1");

    assertThatThrownBy(() -> service.publish(DEF_KEY, null, "u1"))
        .isInstanceOf(WorkflowDefinitionInvalidException.class)
        .hasMessageContaining("拒绝发布");

    assertThat(countPublished()).isZero();
    assertThat(service.findDraft(DEF_KEY)).as("发布失败不得吞掉草稿").isPresent();
  }

  @Test
  @DisplayName("版本号单调占位：下线 v1 后再发布得到 v2，不会回落撞唯一键")
  void versionWatermark_survivesDisable() {
    service.saveDraft(DEF_KEY, VALID_JSON, null, "u1");
    service.publish(DEF_KEY, null, "u1");
    service.setEnabled(DEF_KEY, 1, false, "u1");
    assertThat(service.findVersion(DEF_KEY, 1).orElseThrow().enabled()).isFalse();

    service.saveDraft(DEF_KEY, VALID_JSON, null, "u1");
    WorkflowDefinitionCommandService.Published second = service.publish(DEF_KEY, null, "u1");

    assertThat(second.version()).isEqualTo(2);
    assertThat(service.listVersions(DEF_KEY))
        .extracting(WorkflowDefinitionCommandService.Published::version)
        .containsExactly(1, 2);
  }

  @Test
  @DisplayName("没有草稿 → 发布报冲突；key 与 JSON 内的 key 不一致 → 拒绝保存")
  void publishWithoutDraft_andKeyMismatch_areRejected() {
    assertThatThrownBy(() -> service.publish(DEF_KEY, null, "u1"))
        .isInstanceOf(WorkflowDefinitionConflictException.class)
        .hasMessageContaining("没有草稿");

    assertThatThrownBy(() -> service.saveDraft("other_key", VALID_JSON, null, "u1"))
        .isInstanceOf(WorkflowDefinitionRequestException.class)
        .hasMessageContaining("不一致");
  }

  @Test
  @DisplayName("validate 只校验不落库：合法定义 valid=true；坏 JSON 给 WF-021")
  void validate_isSideEffectFree() {
    WorkflowDefinitionCommandService.ValidationReport ok = service.validate(VALID_JSON);
    assertThat(ok.valid()).isTrue();
    assertThat(ok.violations()).isEmpty();

    WorkflowDefinitionCommandService.ValidationReport broken = service.validate("{not-json");
    assertThat(broken.valid()).isFalse();
    assertThat(broken.violations())
        .extracting(WorkflowDefinitionValidator.Violation::code)
        .containsExactly("WF-021");

    assertThat(countPublished()).isZero();
    assertThat(service.findDraft(DEF_KEY)).isEmpty();
  }

  @Test
  @DisplayName("定义总览只取每个 key 的当前生效版本（下线后不再出现在总览里）")
  void listLatest_reflectsEnabledState() {
    service.saveDraft(DEF_KEY, VALID_JSON, null, "u1");
    service.publish(DEF_KEY, null, "u1");
    assertThat(service.listLatest())
        .extracting(WorkflowDefinitionCommandService.Published::version)
        .containsExactly(1);

    service.setEnabled(DEF_KEY, 1, false, "u1");
    assertThat(service.listLatest()).isEmpty();
  }

  private int countPublished() {
    Integer count =
        named
            .getJdbcOperations()
            .queryForObject(
                "SELECT COUNT(*) FROM wf_process_def WHERE tenant_id = ? AND def_key = ?",
                Integer.class,
                TENANT,
                DEF_KEY);
    return count == null ? 0 : count;
  }
}
