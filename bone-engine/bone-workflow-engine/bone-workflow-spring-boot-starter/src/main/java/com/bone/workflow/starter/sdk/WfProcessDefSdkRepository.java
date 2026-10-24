package com.bone.workflow.starter.sdk;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.Sql;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.util.List;
import java.util.Optional;

/**
 * 流程定义（{@code wf_process_def}）的 SDK {@code @Sql} 仓储。
 *
 * <p>只读（v1 无发布写路径，定义由迁移/种子数据落库）：取最新发布版 / 版本列表 / 按版本取。 {@code definition_json} 的 JSON 原文原样返回，反序列化与损坏
 * fail-loudly 由适配器负责。
 *
 * <p><b>租户口径</b>：接口级 {@link TenantScopeMode#MANUAL}，每条 SQL 显式写 {@code tenant_id} 谓词。
 */
@TenantScope(TenantScopeMode.MANUAL)
public interface WfProcessDefSdkRepository extends Repository<WfProcessDefRow, String> {

  /**
   * 取最新发布版：{@code status='PUBLISHED' AND enabled=1} 中 {@code max(def_version)}。
   *
   * <p><b>不得用 updated_at 决胜</b>：改版重发不保证 updated_at 与版本号同序（补丁式修订会改旧行）， 版本号才是权威序。
   */
  @Sql(
      "SELECT def_key, def_name, def_version, definition_json FROM wf_process_def"
          + " WHERE def_key = #{defKey} AND tenant_id = #{tenantId} AND status = 'PUBLISHED'"
          + "   AND enabled = 1 AND deleted = 0 ORDER BY def_version DESC LIMIT 1")
  Optional<WfProcessDefRow> findLatestPublishedRow(
      @Param("defKey") String defKey, @Param("tenantId") Long tenantId);

  /** 全部发布版（升序，供版本列表/审计）；下线版（{@code enabled=0}）跳过。 */
  @Sql(
      "SELECT def_key, def_name, def_version, definition_json FROM wf_process_def"
          + " WHERE def_key = #{defKey} AND tenant_id = #{tenantId} AND status = 'PUBLISHED'"
          + "   AND enabled = 1 AND deleted = 0 ORDER BY def_version")
  List<WfProcessDefRow> listPublishedRows(
      @Param("defKey") String defKey, @Param("tenantId") Long tenantId);

  /** 按版本取（在途实例钉启动时的版本快照，不随改版漂移）。 */
  @Sql(
      "SELECT def_key, def_name, def_version, definition_json FROM wf_process_def"
          + " WHERE def_key = #{defKey} AND def_version = #{version} AND tenant_id = #{tenantId}"
          + "   AND deleted = 0")
  Optional<WfProcessDefRow> findByVersionRow(
      @Param("defKey") String defKey,
      @Param("version") int version,
      @Param("tenantId") Long tenantId);
}
