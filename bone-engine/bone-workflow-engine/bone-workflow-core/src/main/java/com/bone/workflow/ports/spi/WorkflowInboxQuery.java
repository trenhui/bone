package com.bone.workflow.ports.spi;

import java.util.List;
import java.util.Optional;

/**
 * 站内信收件箱查询端口（读能力）。
 *
 * <p>与 {@link NotificationPort}（写能力，由引擎在派单/挂起/终态时调用）分离：审批流「通知」是尽力而为的投影，
 * 而「我的待办/待阅入口」是办理人主动拉取，二者生命周期不同，故拆成独立端口。
 *
 * <p>居于 core 端口层而非 starter：宿主入站面（REST Controller）按类型注入本端口——若端口类型住在 starter 内部包里，metadata-server
 * 这类「不依赖 starter」的宿主就无法引用，收件箱永远没有 HTTP 出口。
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

  /**
   * 某实例<b>最近一次挂起告警</b>的说明文案（诊断用，只读）。
   *
   * <p><b>为什么需要它</b>：实例进入 {@code SUSPENDED} 时引擎<b>不派单</b>，单据详情页只能看到「挂起、零待办」——
   * 发起人与审批人无从判断是「审批人解析不出」「审批人就是发起人本人（职责分离）」还是「组织信息缺失」，
   * 现场只能靠翻服务端日志。挂起原因本身已作为告警写进通知表（真源），把它读回来展示即可，无需给 {@code wf_instance} 加列。
   *
   * <p><b>缺省空实现</b>：通知走 IM/邮件的宿主可以不落本地表，此时该方法返回 {@link Optional#empty()}， 调用方按「原因未知」降级展示通用指引——
   * 诊断信息缺失不得让单据详情整体失败。
   */
  default Optional<String> latestSuspensionNotice(long tenantId, String instanceId) {
    return Optional.empty();
  }
}
