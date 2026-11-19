package com.bone.procurement.governance.domain.gateway;

/** 登录态操作人端口：治理操作人与人工终决复核人一律从登录态取，不接受请求自报（审计红线）。 */
public interface GovernanceActorPort {

  /** 当前登录人标识；无登录态返回 null，调用方须 fail-closed。 */
  String currentActorId();
}
