package com.bone.workflow.starter.sdk;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.Sql;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.time.Instant;

/**
 * 活动实例指针（{@code wf_active_instance}）的 SDK {@code @Sql} 仓储。
 *
 * <p><b>只写不读</b>：指针是「一单据一活动实例」的原子闸门——插入撞主键（{@code (tenant_id, entity_code, record_id)}
 * 自然主键）即占用失败，判据在数据库层，读路径从不需要指针行内容。
 *
 * <p><b>物理删除而非软删</b>：活动槽位必须<b>物理删除</b>才能释放 PK 供重提使用——软删会在表里留下 {@code deleted=1}
 * 的同行，主键冲突依旧，终态后的单据将永久无法重新提交。
 *
 * <p>写入/删除必须与 {@code wf_instance} 同事务（由适配器 {@code @Transactional claimAndSaveInstance} 保证），
 * 否则指针与实例漂移。
 */
@TenantScope(TenantScopeMode.MANUAL)
public interface WfActiveInstanceSdkRepository extends Repository<WfActiveInstanceRow, String> {

  @Sql(
      "INSERT INTO wf_active_instance (tenant_id, entity_code, record_id, instance_id, created_at,"
          + " updated_at, deleted) VALUES (#{tenantId}, #{entityCode}, #{recordId}, #{instanceId},"
          + " #{now}, #{now}, 0)")
  int insertActivePointer(
      @Param("tenantId") Long tenantId,
      @Param("entityCode") String entityCode,
      @Param("recordId") String recordId,
      @Param("instanceId") String instanceId,
      @Param("now") Instant now);

  /** 按单据释放（终态 phase CAS 同事务内联释放，允许同一单据重新提交）。 */
  @Sql(
      "DELETE FROM wf_active_instance"
          + " WHERE tenant_id = #{tenantId} AND entity_code = #{entityCode} AND record_id = #{recordId}")
  int deleteActivePointer(
      @Param("tenantId") Long tenantId,
      @Param("entityCode") String entityCode,
      @Param("recordId") String recordId);

  /** 按实例释放（陈旧指针自愈：指针指向的实例已终态时清理后重试一次，否则上次流程结束后永久无法重提）。 */
  @Sql(
      "DELETE FROM wf_active_instance"
          + " WHERE tenant_id = #{tenantId} AND instance_id = #{instanceId}")
  int deleteActivePointerByInstance(
      @Param("tenantId") Long tenantId, @Param("instanceId") String instanceId);
}
