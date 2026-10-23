package com.bone.workflow.starter.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.domain.WorkflowDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * 定义仓储的<b>端到端</b>测试：H2 真实执行 {@link com.bone.workflow.starter.persistence.WfSql} 里的生产 SQL。
 *
 * <p><b>为什么必须有这一层</b>：{@code listPublished}（全版本列举）与 {@code findLatestPublished} （取最新版）在内存 stub
 * 里都只是「返回预设列表」，分不出「全集 vs 最新」的语义差。二者曾共用同一条 {@code LIMIT 1} 的
 * SQL，全版本列举静默退化为单版本，且无任何测试变红——因为根本没有用例断言版本个数。
 *
 * <p><b>判别性设计（防假绿）</b>：{@link #listPublished_returnsEveryPublishedVersionAscending()} 断言 3 个
 * 版本全部返回（误用 LATEST 则 size=1 红）；{@link #findLatestPublished_returnsMaxVersionOnly()} 反向断言
 * 启动路径只拿最大版本（若把 LATEST 改成全集语义则取到 v1 红）——两条用例互为护栏，单独绿一条不构成证明。
 */
class JdbcWorkflowDefinitionRepositoryH2Test {

  private static final long TENANT = 1L;
  private static final String DEF_KEY = "expense_reimbursement";

  private EmbeddedDatabase db;
  private NamedParameterJdbcTemplate named;
  private JdbcWorkflowDefinitionRepository repository;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    db =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .addScript("classpath:wf-schema-h2.sql")
            .build();
    named = new NamedParameterJdbcTemplate(db);
    repository = new JdbcWorkflowDefinitionRepository(named, mapper);
    TenantContext.setTenantId(TENANT);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    db.shutdown();
  }

  @Test
  @DisplayName("listPublished 返回全部已发布版本且按版本号升序（LIMIT 1 退化回归防护）")
  void listPublished_returnsEveryPublishedVersionAscending() throws Exception {
    seedDefinition(1, true);
    seedDefinition(2, true);
    seedDefinition(3, true);

    List<WorkflowDefinition> all = repository.listPublished(DEF_KEY);

    assertEquals(3, all.size(), "全版本列举不得退化为仅最新版——误用 LATEST（LIMIT 1）时此处为 1");
    assertEquals(
        List.of(1, 2, 3),
        all.stream().map(WorkflowDefinition::version).toList(),
        "必须按版本号升序，供版本回溯定位");
  }

  @Test
  @DisplayName("findLatestPublished 只取最大版本——与列举的全集语义互为护栏")
  void findLatestPublished_returnsMaxVersionOnly() throws Exception {
    seedDefinition(1, true);
    seedDefinition(2, true);

    Optional<WorkflowDefinition> latest = repository.findLatestPublished(DEF_KEY);

    assertTrue(latest.isPresent(), "已发布定义必须可取");
    assertEquals(2, latest.get().version(), "启动路径必须拿到最新版，不得返回全集第一条（v1）");
  }

  @Test
  @DisplayName("已下线（enabled=0）的版本不出现在列举与最新版里")
  void disabledVersion_isInvisible() throws Exception {
    seedDefinition(1, true);
    seedDefinition(2, false);

    List<WorkflowDefinition> all = repository.listPublished(DEF_KEY);

    assertEquals(1, all.size(), "下线版本不得进列举");
    assertEquals(1, all.get(0).version());
    assertTrue(
        repository.findLatestPublished(DEF_KEY).orElseThrow().version() == 1, "最新版必须跳过已下线版本回落到 v1");
  }

  private void seedDefinition(int version, boolean enabled) throws Exception {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            DEF_KEY,
            "报销审批",
            version,
            List.of(
                new WorkflowDefinition.Node("start", WorkflowDefinition.NodeType.START, null, "发起"),
                new WorkflowDefinition.Node("end", WorkflowDefinition.NodeType.END, null, "结束")),
            List.of(new WorkflowDefinition.Edge("start", "end", null, null)),
            java.util.Map.of());
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
            mapper.writeValueAsString(definition));
  }
}
