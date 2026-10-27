package com.bone.workflow.starter.sdk;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.Sql;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.time.Instant;
import java.util.Optional;

/**
 * 流程定义草稿（{@code wf_process_draft}）的 SDK {@code @Sql} 仓储。
 *
 * <p><b>一 key 一稿</b>：唯一键 {@code (tenant_id, def_key)} 保证同一定义只有一份草稿，避免「两个运营各存一稿、谁覆盖谁说不清」。
 *
 * <p><b>写并发靠 revision CAS</b>，不靠 {@code version} 列：{@code version} 是平台乐观锁惯例（自增），而草稿的冲突语义是 「我读到的是第 N
 * 版，若已被改成 N+1 就拒绝保存」。用 {@code revision = #{expectedRevision}} 做谓词， 冲突时返回 0 行由服务层翻译成
 * 409——这是唯一能让前端提示「他人已修改」的方式。
 *
 * <p><b>删除是物理删除</b>：草稿被丢弃后不能留 {@code deleted=1} 的行占着唯一键，否则同一定义再也无法重新起草。
 *
 * <p><b>租户口径</b>：接口级 {@link TenantScopeMode#MANUAL}，每条 SQL 显式写 {@code tenant_id} 谓词。
 */
@TenantScope(TenantScopeMode.MANUAL)
public interface WfProcessDraftSdkRepository extends Repository<WfProcessDraftRow, String> {

  /**
   * 新建草稿（首稿）。与已有草稿撞唯一键即说明已被他人创建，由服务层转 409。
   *
   * <p>首稿 {@code revision} 记为 <b>1</b>（不是 0）：revision 的语义是「已保存次数」，前端读到的值直接用于下一次覆盖的 CAS 谓词；从 1
   * 起算可以让「0 = 从未保存」保持为可判别的哨兵值。
   */
  @Sql(
      "INSERT INTO wf_process_draft (id, tenant_id, def_key, def_name, definition_json, revision,"
          + " created_by, created_at, updated_by, updated_at, deleted, version)"
          + " VALUES (#{id}, #{tenantId}, #{defKey}, #{defName}, #{definitionJson}, 1,"
          + " #{operatorId}, #{now}, #{operatorId}, #{now}, 0, 0)")
  int insertDraft(
      @Param("id") String id,
      @Param("tenantId") Long tenantId,
      @Param("defKey") String defKey,
      @Param("defName") String defName,
      @Param("definitionJson") String definitionJson,
      @Param("operatorId") String operatorId,
      @Param("now") Instant now);

  /** 覆盖保存：{@code revision} 必须等于调用方读到的值，否则 0 行（并发冲突）。 */
  @Sql(
      "UPDATE wf_process_draft SET def_name = #{defName}, definition_json = #{definitionJson},"
          + " revision = revision + 1, updated_by = #{operatorId}, updated_at = #{now},"
          + " version = version + 1"
          + " WHERE tenant_id = #{tenantId} AND def_key = #{defKey} AND revision = #{expectedRevision}")
  int updateDraft(
      @Param("tenantId") Long tenantId,
      @Param("defKey") String defKey,
      @Param("defName") String defName,
      @Param("definitionJson") String definitionJson,
      @Param("expectedRevision") int expectedRevision,
      @Param("operatorId") String operatorId,
      @Param("now") Instant now);

  @Sql(
      "SELECT id, def_key, def_name, definition_json, revision, updated_by FROM wf_process_draft"
          + " WHERE tenant_id = #{tenantId} AND def_key = #{defKey} AND deleted = 0")
  Optional<WfProcessDraftRow> findDraftRow(
      @Param("tenantId") Long tenantId, @Param("defKey") String defKey);

  /** 丢弃草稿（发布成功后同事务清理，或运营显式放弃）。物理删除，理由见类注释。 */
  @Sql("DELETE FROM wf_process_draft WHERE tenant_id = #{tenantId} AND def_key = #{defKey}")
  int deleteDraft(@Param("tenantId") Long tenantId, @Param("defKey") String defKey);
}
