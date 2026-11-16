package com.bone.masterdata.domain.gateway;

import com.bone.masterdata.domain.model.changerequest.ChangeRequest;
import com.bone.masterdata.domain.model.changerequest.ChangeRequest.ApprovalState;
import com.bone.masterdata.domain.model.changerequest.ChangeRequest.Segment;

/**
 * 变更请求工作流协同端口（{@code bone-workflow-engine} 的终局接入缝）。
 *
 * <p><b>为什么是端口而不是直接依赖引擎</b>：平台模块受 ArchUnit {@code platformMustNotDependOnEngineApps}
 * 约束，不得依赖引擎可部署应用壳；分段并行路由、任务分派与超时升级由工作流引擎提供，平台只发布语义事件。 生产 adapter 属
 * L3；本轮联调用测试夹具记录调用序列，证明三段并行与终态通知的接缝确实存在。
 *
 * <p><b>不是过渡件</b>：终局图中该端口原位存在，换实现不需要改领域内核、CR 语义或 ID。
 */
public interface ChangeRequestWorkflowPort {

  void onSubmitted(ChangeRequest changeRequest);

  void onSegmentDecided(ChangeRequest changeRequest, Segment segment, ApprovalState state);

  void onApplied(ChangeRequest changeRequest);
}
