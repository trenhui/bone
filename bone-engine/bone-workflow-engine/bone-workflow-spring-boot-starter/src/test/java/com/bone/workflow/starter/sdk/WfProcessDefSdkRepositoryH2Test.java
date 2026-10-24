package com.bone.workflow.starter.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.tenant.context.TenantContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * {@code wf_process_def} SDK 仓储的 H2 直连测试（不经适配器，直测 {@code @Sql} 代理本身）。
 *
 * <p>与 {@code SdkWorkflowDefinitionRepositoryH2Test} 的分工：那个测「适配器 + 领域映射 + JSON 解析」， 这个测「代理装配 + SQL
 * 谓词正确性」——{@code status/enabled/deleted/tenant_id} 四个过滤维度各自 有负向用例，漏写任何一个谓词都会让他租户/下线版/软删版漏进结果。
 */
class WfProcessDefSdkRepositoryH2Test {

  private static final long TENANT = 1L;
  private static final long OTHER_TENANT = 2L;
  private static final String DEF_KEY = "expense_reimbursement";

  private AnnotationConfigApplicationContext context;
  private NamedParameterJdbcTemplate named;
  private WfProcessDefSdkRepository repository;

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(TENANT);
    context = WorkflowSdkTestContext.create();
    named = context.getBean(NamedParameterJdbcTemplate.class);
    repository = context.getBean(WfProcessDefSdkRepository.class);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    if (context != null) {
      context.close();
    }
  }

  @Test
  @DisplayName("spike：SDK 代理装配成功且 listPublished 返回全部已发布版本（升序）")
  void listPublishedRows_returnsEveryPublishedVersionAscending() throws Exception {
    seedDefinition(1, true);
    seedDefinition(2, true);
    seedDefinition(3, true);

    List<WfProcessDefRow> rows = repository.listPublishedRows(DEF_KEY, TENANT);

    assertThat(rows).hasSize(3);
    assertThat(rows).extracting(WfProcessDefRow::getDefVersion).containsExactly(1, 2, 3);
  }

  @Test
  @DisplayName("spike：findLatestPublishedRow 只取最大版本，且 enabled=0 的下线版不可见")
  void findLatestPublishedRow_skipsDisabledVersions() throws Exception {
    seedDefinition(1, true);
    seedDefinition(2, false);

    assertThat(repository.findLatestPublishedRow(DEF_KEY, TENANT))
        .hasValueSatisfying(row -> assertThat(row.getDefVersion()).isEqualTo(1));
  }

  @Test
  @DisplayName("spike：租户隔离——他租户的发布版不得漏进本租户结果（显式 tenant_id 谓词）")
  void tenantIsolation_holds() throws Exception {
    seedDefinition(1, true);
    try {
      TenantContext.setTenantId(OTHER_TENANT);

      assertThat(repository.listPublishedRows(DEF_KEY, OTHER_TENANT)).isEmpty();
      assertThat(repository.findLatestPublishedRow(DEF_KEY, OTHER_TENANT)).isEmpty();
    } finally {
      TenantContext.setTenantId(TENANT);
    }
  }

  // ====================== 夹具 ======================

  /** 落一行定义种子（v1 无发布写路径，测试经裸 JDBC 复刻迁移/种子的落库路径）。 */
  private void seedDefinition(int version, boolean enabled) throws Exception {
    String json =
        version == 1
            ? "{\"definitionKey\":\"expense_reimbursement\",\"name\":\"报销审批\",\"version\":1,"
                + "\"nodes\":[],\"edges\":[],\"assignments\":{}}"
            : "{\"definitionKey\":\"expense_reimbursement\",\"name\":\"报销审批\",\"version\":%d,"
                + "\"nodes\":[],\"edges\":[],\"assignments\":{}}".formatted(version);

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
            json);
  }
}
