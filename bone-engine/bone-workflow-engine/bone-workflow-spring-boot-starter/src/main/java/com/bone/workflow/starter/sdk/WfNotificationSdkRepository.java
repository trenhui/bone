package com.bone.workflow.starter.sdk;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.Sql;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.time.Instant;
import java.util.List;

/**
 * 站内信通知（{@code wf_notification}）的 SDK {@code @Sql} 仓储。
 *
 * <p>SQL 与并发口径随方法文档沉淀在此——H2 测试（{@code wf-schema-h2.sql}）原样执行这些语句， 避免「测试另写一份 SQL → 测试绿、生产死锁」。
 *
 * <p><b>租户口径</b>：接口级 {@link TenantScopeMode#MANUAL}，每条 SQL 显式写 {@code tenant_id} 谓词—— SDK 的
 * {@code @Sql} 通道不经过租户过滤器，漏谓词即多租户串数据，且单租户单测完全看不出来（P1-1 静态门禁强约束）。
 */
@TenantScope(TenantScopeMode.MANUAL)
public interface WfNotificationSdkRepository extends Repository<WfNotificationRow, String> {

  /**
   * 写入一条通知。{@code read_flag} 默认 0（未读）；审计列 {@code created_by}/{@code updated_by} 均由调用方经 {@code
   * operatorId} 显式提供。
   */
  @Sql(
      "INSERT INTO wf_notification (id, tenant_id, recipient_id, type, instance_id, entity_code,"
          + " record_id, node_code, title, body, read_flag, created_at, updated_at, created_by,"
          + " updated_by, deleted, version)"
          + " VALUES (#{id}, #{tenantId}, #{recipientId}, #{type}, #{instanceId}, #{entityCode},"
          + " #{recordId}, #{nodeCode}, #{title}, #{body}, 0, #{now}, #{now}, #{operatorId},"
          + " #{operatorId}, 0, 0)")
  int insertNotification(
      @Param("id") String id,
      @Param("tenantId") Long tenantId,
      @Param("recipientId") String recipientId,
      @Param("type") String type,
      @Param("instanceId") String instanceId,
      @Param("entityCode") String entityCode,
      @Param("recordId") String recordId,
      @Param("nodeCode") String nodeCode,
      @Param("title") String title,
      @Param("body") String body,
      @Param("now") Instant now,
      @Param("operatorId") String operatorId);

  /**
   * 收件箱查询（全部）：按接收人取所有未软删通知。
   *
   * <p>排序按 {@code created_at DESC, id DESC}（id 在 UUID 宿主下字典序≠时间序，按 id 决胜会取错最新）。
   */
  @Sql(
      "SELECT id, tenant_id, recipient_id, type, instance_id, entity_code, record_id, node_code,"
          + " title, body, read_flag, created_at, updated_at FROM wf_notification"
          + " WHERE tenant_id = #{tenantId} AND recipient_id = #{recipientId} AND deleted = 0"
          + " ORDER BY created_at DESC, id DESC")
  List<WfNotificationRow> selectInboxAll(
      @Param("tenantId") Long tenantId, @Param("recipientId") String recipientId);

  /** 收件箱查询（仅未读）：在 {@link #selectInboxAll} 基础上加 {@code read_flag = 0} 谓词。 */
  @Sql(
      "SELECT id, tenant_id, recipient_id, type, instance_id, entity_code, record_id, node_code,"
          + " title, body, read_flag, created_at, updated_at FROM wf_notification"
          + " WHERE tenant_id = #{tenantId} AND recipient_id = #{recipientId} AND read_flag = 0"
          + " AND deleted = 0 ORDER BY created_at DESC, id DESC")
  List<WfNotificationRow> selectInboxUnread(
      @Param("tenantId") Long tenantId, @Param("recipientId") String recipientId);

  /**
   * 某实例最近一条指定类型的通知（挂起告警诊断用）。
   *
   * <p>{@code type} 取 {@code SUSPENDED} 即「为什么被挂起」的真源文案：引擎挂起时把具体原因（解析不出审批人 / 审批人含发起人本人 / 分支不可裁决…）写进
   * {@code body}，单据详情页读回来展示，现场无需翻日志。
   *
   * <p>排序 {@code created_at DESC, id DESC}：同一实例可能因「挂起 → 指派 → 再挂起」留下多条，必须取最新一条而非任意一条。
   */
  @Sql(
      "SELECT id, tenant_id, recipient_id, type, instance_id, entity_code, record_id, node_code,"
          + " title, body, read_flag, created_at, updated_at FROM wf_notification"
          + " WHERE tenant_id = #{tenantId} AND instance_id = #{instanceId} AND type = #{type}"
          + " AND deleted = 0 ORDER BY created_at DESC, id DESC LIMIT 1")
  WfNotificationRow selectLatestByInstanceAndType(
      @Param("tenantId") Long tenantId,
      @Param("instanceId") String instanceId,
      @Param("type") String type);

  /** 标记已读（收件箱前端动作；与任务决策无关）。 */
  @Sql(
      "UPDATE wf_notification SET read_flag = 1, updated_at = #{now}, updated_by = #{operatorId},"
          + " version = version + 1"
          + " WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
  int markRead(
      @Param("id") String id,
      @Param("tenantId") Long tenantId,
      @Param("now") Instant now,
      @Param("operatorId") String operatorId);
}
