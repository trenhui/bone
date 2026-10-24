package com.bone.workflow.starter.sdk;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.Sql;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.util.Optional;

/**
 * 业务实体 → 流程定义绑定（{@code wf_binding}）的 SDK {@code @Sql} 仓储。
 *
 * <p>v1 只读（写路径随建模界面排期）。查询以 {@code (entity_code, tenant_id)} 期望<b>唯一命中</b>： {@code enabled = 1 AND
 * deleted = 0} 过滤后若同实体配了多条生效绑定，SDK 对多行单结果查询抛 {@code IncorrectResultSizeDataAccessException}——<b>刻意
 * fail-fast，不静默挑一条</b>（配错流程比报错更危险）。
 *
 * <p><b>租户口径</b>：{@link TenantScopeMode#MANUAL} + 显式谓词；宿主 {@code WorkflowBindingRepository}
 * 在无租户上下文时拒绝查询（fail-closed）。
 */
@TenantScope(TenantScopeMode.MANUAL)
public interface WfBindingSdkRepository extends Repository<WfBindingRow, String> {

  @Sql(
      "SELECT id, entity_code, def_key, start_action FROM wf_binding"
          + " WHERE entity_code = #{entityCode} AND tenant_id = #{tenantId}"
          + "   AND enabled = 1 AND deleted = 0")
  Optional<WfBindingRow> findBindingRow(
      @Param("entityCode") String entityCode, @Param("tenantId") Long tenantId);
}
