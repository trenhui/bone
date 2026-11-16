package com.bone.masterdata.testsupport.changerequest;

import com.bone.masterdata.domain.gateway.ChangeRequestWorkflowPort;
import com.bone.masterdata.domain.model.changerequest.ChangeRequest;
import com.bone.masterdata.domain.model.changerequest.ChangeRequest.ApprovalState;
import com.bone.masterdata.domain.model.changerequest.ChangeRequest.Segment;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作流协同夹具（{@code bone-workflow-engine} 接入缝）：记录三段并行路由与终态通知的调用序列。
 *
 * <p>平台不得依赖引擎应用壳（ArchUnit {@code platformMustNotDependOnEngineApps}），因此生产实现是 出站端口
 * adapter（L3）；本夹具只验证「端口确实在正确时机被调用」，不冒充工作流引擎能力已交付。
 */
public final class RecordingChangeRequestWorkflowAdapter implements ChangeRequestWorkflowPort {

  private final List<String> calls = new ArrayList<>();

  @Override
  public void onSubmitted(ChangeRequest changeRequest) {
    calls.add("SUBMITTED:" + changeRequest.getChangeRequestId());
  }

  @Override
  public void onSegmentDecided(ChangeRequest changeRequest, Segment segment, ApprovalState state) {
    calls.add("SEGMENT:" + segment + ":" + state);
  }

  @Override
  public void onApplied(ChangeRequest changeRequest) {
    calls.add("APPLIED:" + changeRequest.getChangeRequestId());
  }

  public List<String> calls() {
    return new ArrayList<>(calls);
  }

  public void reset() {
    calls.clear();
  }
}
