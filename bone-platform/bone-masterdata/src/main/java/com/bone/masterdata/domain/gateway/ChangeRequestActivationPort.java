package com.bone.masterdata.domain.gateway;

import com.bone.masterdata.domain.model.changerequest.ChangeRequest;

/**
 * Active 区发布端口：把已通过三段审批的变更原子写入生效区，并追加版本化 Outbox 事件。
 *
 * <p><b>硬约束</b>：编排层<b>只允许</b>在 {@link ChangeRequest#apply} 返回 {@code APPLIED} 之后调用本端口。 生产 adapter
 * 必须再次校验 CR 状态（纵深防御），且与 Active 写入同事务追加 Outbox，避免「生效了但没分发」。
 */
public interface ChangeRequestActivationPort {

  void activate(ChangeRequest applied);
}
