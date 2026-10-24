package com.bone.workflow.starter.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.starter.sdk.WorkflowSdkTestContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 流程定义仓储的 H2 端到端测试：生产 SQL（{@code WfProcessDefSdkRepository}）经 SDK {@code @Sql} 代理 在 H2 上真实执行，种子行走裸
 * JDBC（{@code named.getJdbcOperations()}）——v1 无发布写路径， 定义由迁移/种子数据落库，测试必须复刻同一条落库路径。
 *
 * <p><b>判别性设计（防假绿）</b>：{@link #listPublished_returnsEveryPublishedVersionAscending()} 钉死
 * 「列举不得退化成仅取最新版」——若 {@code listPublishedRows} 误用 LATEST/LIMIT 1，全集语义静默丢失， 版本回溯（在途实例钉旧版）随之失效；{@link
 * #corruptedDefinitionJson_failsLoudly()} 钉死损坏 JSON 必须显式炸出——静默返回空会让调用方以为「没有定义」，与「定义坏了」是两种完全不同的故障。
 */
class SdkWorkflowDefinitionRepositoryH2Test {

  private static final long TENANT = 1L;
  private static final String DEF_KEY = "expense_reimbursement";

  private AnnotationConfigApplicationContext context;
  private NamedParameterJdbcTemplate named;
  private SdkWorkflowDefinitionRepository repository;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(TENANT);
    context = WorkflowSdkTestContext.create();
    named = context.getBean(NamedParameterJdbcTemplate.class);
    repository = context.getBean(SdkWorkflowDefinitionRepository.class);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    if (context != null) {
      context.close();
    }
  }

  @Test
  @DisplayName("listPublished 返回全部已发布版本且按版本号升序（LIMIT 1 退化回归防护）")
  void listPublished_returnsEveryPublishedVersionAscending() throws Exception {
    seedDefinition(1, true);
    seedDefinition(2, true);
    seedDefinition(3, true);

    List<WorkflowDefinition> all = repository.listPublished(DEF_KEY);

    assertThat(all).as("全版本列举不得退化为仅最新版——误用 LATEST（LIMIT 1）时此处为 1").hasSize(3);
    assertThat(all)
        .extracting(WorkflowDefinition::version)
        .as("必须按版本号升序，供版本回溯定位")
        .containsExactly(1, 2, 3);
  }

  @Test
  @DisplayName("findLatestPublished 只取最大版本——与列举的全集语义互为护栏")
  void findLatestPublished_returnsMaxVersionOnly() throws Exception {
    seedDefinition(1, true);
    seedDefinition(2, true);

    Optional<WorkflowDefinition> latest = repository.findLatestPublished(DEF_KEY);

    assertThat(latest).as("已发布定义必须可取").isPresent();
    assertThat(latest.orElseThrow().version()).as("启动路径必须拿到最新版，不得返回全集第一条（v1）").isEqualTo(2);
  }

  @Test
  @DisplayName("已下线（enabled=0）的版本不出现在列举与最新版里")
  void disabledVersion_isInvisible() throws Exception {
    seedDefinition(1, true);
    seedDefinition(2, false);

    List<WorkflowDefinition> all = repository.listPublished(DEF_KEY);
    assertThat(all).as("下线版本不得进列举").hasSize(1);
    assertThat(all.get(0).version()).isEqualTo(1);

    Optional<WorkflowDefinition> latest = repository.findLatestPublished(DEF_KEY);
    assertThat(latest).as("最新版必须跳过已下线版本回落到 v1").isPresent();
    assertThat(latest.orElseThrow().version()).isEqualTo(1);
  }

  @Test
  @DisplayName("定义 JSON 损坏必须显式炸出（不得静默返回空让调用方以为没有定义）")
  void corruptedDefinitionJson_failsLoudly() {
    // 绕过 ObjectMapper 直写非法 JSON：模拟迁移/人工改库写坏的 definition_json
    named
        .getJdbcOperations()
        .update(
            "INSERT INTO wf_process_def (id, tenant_id, def_key, def_name, def_version, status,"
                + " enabled, definition_json, created_by, created_at, updated_by, updated_at,"
                + " deleted, version)"
                + " VALUES (?, ?, ?, ?, 1, 'PUBLISHED', 1, '{not-json', 'u1', NOW(), 'u1', NOW(), 0, 0)",
            "D-" + DEF_KEY + "-v9",
            TENANT,
            DEF_KEY,
            "D-broken");

    assertThatThrownBy(() -> repository.findLatestPublished(DEF_KEY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("流程定义 JSON 无法解析");
  }

  // ====================== 夹具 ======================

  /** 落一行已发布定义（{@code enabled} 控制是否下线），definition_json 经 ObjectMapper 真实序列化。 */
  private void seedDefinition(int version, boolean enabled) throws Exception {
    WorkflowDefinition.Node start =
        new WorkflowDefinition.Node("start", WorkflowDefinition.NodeType.START, null, "发起");
    WorkflowDefinition.Node end =
        new WorkflowDefinition.Node("end", WorkflowDefinition.NodeType.END, null, "结束");
    WorkflowDefinition def =
        new WorkflowDefinition(
            DEF_KEY,
            version,
            List.of(start, end),
            List.of(new WorkflowDefinition.Edge("start", "end", null, null)),
            Map.of());

    named
        .getJdbcOperations()
        .update(
            "INSERT INTO wf_process_def (id, tenant_id, def_key, def_name, def_version, status,"
                + " enabled, definition_json, created_by, created_at, updated_by, updated_at,"
                + " deleted, version)"
                + " VALUES (?, ?, ?, ?, ?, 'PUBLISHED', ?, ?, 'u1', NOW(), 'u1', NOW(), 0, 0)",
            "D-" + DEF_KEY + "-v" + version,
            TENANT,
            DEF_KEY,
            "报销审批",
            version,
            enabled ? 1 : 0,
            mapper.writeValueAsString(def));
  }
}
