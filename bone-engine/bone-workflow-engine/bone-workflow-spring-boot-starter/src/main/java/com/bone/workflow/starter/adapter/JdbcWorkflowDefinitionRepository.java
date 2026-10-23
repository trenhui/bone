package com.bone.workflow.starter.adapter;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.ports.spi.WorkflowDefinitionRepository;
import com.bone.workflow.starter.persistence.WfSql;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 流程定义仓储的平台实现（{@code wf_process_def.definition_json} 整图快照）。
 *
 * <p><b>为什么整图存 JSON 而不拆 node/transition/assignment 物理表</b>：流程定义是「整图版本化」对象，拆表只会增加 join 与版本一致性负担；
 * 运行期一次读取即可装配成不可变 {@link WorkflowDefinition}，也避免运行途中定义被改动导致同一单据前后走不同分支。
 */
public class JdbcWorkflowDefinitionRepository implements WorkflowDefinitionRepository {

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public JdbcWorkflowDefinitionRepository(
      NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  @Override
  public Optional<WorkflowDefinition> findLatestPublished(String definitionKey) {
    Long tenantId = requireTenant();
    List<WorkflowDefinition> list =
        jdbc.query(
            WfSql.SELECT_DEF_LATEST_PUBLISHED,
            Map.of("defKey", definitionKey, "tenantId", tenantId),
            (rs, i) -> mapRecord(rs));
    return list.stream().findFirst();
  }

  @Override
  public Optional<WorkflowDefinition> findByVersion(String definitionKey, int version) {
    Long tenantId = requireTenant();
    List<WorkflowDefinition> list =
        jdbc.query(
            WfSql.SELECT_DEF_BY_VERSION,
            Map.of("defKey", definitionKey, "version", version, "tenantId", tenantId),
            (rs, i) -> mapRecord(rs));
    return list.stream().findFirst();
  }

  @Override
  public List<WorkflowDefinition> listPublished(String definitionKey) {
    Long tenantId = requireTenant();
    return jdbc.query(
        WfSql.SELECT_DEF_ALL_PUBLISHED,
        Map.of("defKey", definitionKey, "tenantId", tenantId),
        (rs, i) -> mapRecord(rs));
  }

  private WorkflowDefinition mapRecord(ResultSet rs) throws SQLException {
    String json = rs.getString("definition_json");
    try {
      return objectMapper.readValue(json, WorkflowDefinition.class);
    } catch (Exception ex) {
      // 定义 JSON 损坏是「流程跑不了」的硬故障，必须显式炸出，不能静默返回 null 让调用方以为没有定义
      throw new IllegalStateException("流程定义 JSON 无法解析：defKey=" + rs.getString("def_key"), ex);
    }
  }

  private Long requireTenant() {
    Long tenantId = TenantContext.getTenantIdAsLong();
    if (tenantId == null) {
      throw new IllegalStateException("审批流桥接层缺少租户上下文（TenantContext）——拒绝在未知租户下读取流程定义");
    }
    return tenantId;
  }
}
