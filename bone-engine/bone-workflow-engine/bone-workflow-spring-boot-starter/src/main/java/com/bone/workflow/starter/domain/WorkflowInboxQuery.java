package com.bone.workflow.starter.domain;

import java.util.List;

/**
 * 站内信收件箱查询端口（读能力）。
 *
 * <p>与 {@code NotificationPort}（写能力，由引擎在派单/挂起/终态时调用）分离：审批流「通知」是尽力而为的投影，
 * 而「我的待办/待阅入口」是办理人主动拉取，二者生命周期不同，故拆成独立端口。
 *
 * <p>宿主也可自行实现本接口覆盖（例如通知走 IM/邮件网关时不落本地表，但仍需一个查询入口）—— {@code PersistenceNotificationPort}
 * 同时实现本接口，是默认落地。
 */
public interface WorkflowInboxQuery {

  /**
   * 查询某接收人的站内信。
   *
   * @param tenantId 租户ID
   * @param recipientId 接收人账户ID
   * @param unreadOnly 仅返回未读项
   * @return 通知列表（按时间倒序）
   */
  List<WorkflowNotification> inbox(long tenantId, String recipientId, boolean unreadOnly);
}
