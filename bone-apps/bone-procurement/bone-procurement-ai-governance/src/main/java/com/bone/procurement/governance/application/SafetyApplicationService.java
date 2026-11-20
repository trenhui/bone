package com.bone.procurement.governance.application;

import com.bone.procurement.governance.domain.gateway.CapabilityRegistry;
import com.bone.procurement.governance.domain.gateway.GlobalKillSwitchStore;
import com.bone.procurement.governance.domain.gateway.GovernanceActorPort;
import com.bone.procurement.governance.domain.gateway.GovernanceTracePort;
import com.bone.procurement.governance.domain.model.capability.GlobalKillSwitch;
import com.bone.procurement.governance.domain.model.capability.GovernanceTraceEntry;
import com.bone.procurement.governance.domain.model.fairness.BiasSignal;
import com.bone.procurement.governance.domain.model.fairness.FairnessDecision;
import com.bone.procurement.governance.domain.model.labeling.ContentLabel;
import com.bone.procurement.governance.domain.model.labeling.DeliveryDecision;
import com.bone.procurement.governance.domain.service.FairnessRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 安全治理应用服务（批次3）：全域 kill switch + 生成内容双标识出域拦截 + G15 公平确定性裁决。
 *
 * <p>红线：所有操作人取登录态；kill switch 缺省全停、读库即时生效；双标识缺一阻断出域；偏见命中阻断模型晋升并留伦理审查标记。 只落实工程载体与 fail-closed
 * 动作，不代替法务定性、不触碰模型效果数字。
 */
@Service
@ConditionalOnAiGovernanceEnabled
public class SafetyApplicationService {

  private final GlobalKillSwitchStore killSwitchStore;
  private final CapabilityRegistry registry;
  private final GovernanceTracePort tracePort;
  private final GovernanceActorPort actorPort;
  private final FairnessRule fairnessRule = new FairnessRule();

  public SafetyApplicationService(
      GlobalKillSwitchStore killSwitchStore,
      CapabilityRegistry registry,
      GovernanceTracePort tracePort,
      GovernanceActorPort actorPort) {
    this.killSwitchStore = Objects.requireNonNull(killSwitchStore, "killSwitchStore");
    this.registry = Objects.requireNonNull(registry, "registry");
    this.tracePort = Objects.requireNonNull(tracePort, "tracePort");
    this.actorPort = Objects.requireNonNull(actorPort, "actorPort");
  }

  /** 用例一：当前 kill switch 快照（无记录按缺省 STANDBY）。 */
  public KillSwitchView currentKillSwitch() {
    GlobalKillSwitch ks = killSwitchStore.current();
    if (ks == null) {
      ks = GlobalKillSwitch.standby();
    }
    return KillSwitchView.from(ks);
  }

  /** 用例二：一键停用（engaged）；drillRef 记录演练编号。操作人取登录态。 */
  public KillSwitchView engageKillSwitch(String reason, String drillRef) {
    String operator = requireActor();
    GlobalKillSwitch ks = killSwitchStore.current();
    if (ks == null) {
      ks = GlobalKillSwitch.standby();
    }
    ks.engage(operator, reason, drillRef);
    killSwitchStore.save(ks);
    tracePort.append(
        GovernanceTraceEntry.of(
            "GLOBAL",
            "KILL_SWITCH_ENGAGED",
            "operator=" + operator + ", drill=" + drillRef + ", reason=" + reason));
    return KillSwitchView.from(ks);
  }

  /** 用例三：解除停用（仅 ENGAGED 可恢复）。操作人取登录态。 */
  public KillSwitchView disengageKillSwitch(String reason) {
    String operator = requireActor();
    GlobalKillSwitch ks = killSwitchStore.current();
    if (ks == null) {
      throw new IllegalStateException("kill switch 未初始化，无法解除");
    }
    ks.disengage(operator, reason);
    killSwitchStore.save(ks);
    tracePort.append(
        GovernanceTraceEntry.of(
            "GLOBAL", "KILL_SWITCH_DISENGAGED", "operator=" + operator + ", reason=" + reason));
    return KillSwitchView.from(ks);
  }

  /** 用例四：全域准入总闸——kill switch 未解除则任何能力都不得推理。 */
  public boolean globalAllowsAi() {
    GlobalKillSwitch ks = killSwitchStore.current();
    return ks != null && ks.allowsAi();
  }

  /** 用例五：内容出域双标识拦截（LABEL-01）。 */
  public DeliveryDecision checkDelivery(ContentLabel label) {
    DeliveryDecision decision = DeliveryDecision.decide(label);
    tracePort.append(
        GovernanceTraceEntry.of(
            label.capabilityCode(),
            decision.released() ? "LABEL_RELEASE" : "LABEL_BLOCKED",
            "contentRef=" + label.contentRef() + ", reason=" + decision.reasonCode()));
    return decision;
  }

  /** 用例六：G15 公平确定性裁决（FAIR-01/02）。 */
  public FairnessDecision evaluateFairness(BiasSignal signal) {
    FairnessDecision decision = fairnessRule.evaluate(signal);
    tracePort.append(
        GovernanceTraceEntry.of(
            "G15",
            decision.biasDetected() ? "BIAS_DETECTED" : "FAIRNESS_CLEAR",
            "subject="
                + signal.subjectRef()
                + ", dimension="
                + decision.dimension()
                + ", reason="
                + decision.reasonCode()));
    return decision;
  }

  /** 停机演练：一键停用后返回各能力生效态快照，验证无残留 AI 路径。 */
  public List<KillSwitchView.CapabilityEffect> drillStatus() {
    boolean globalAi = globalAllowsAi();
    List<KillSwitchView.CapabilityEffect> effects = new ArrayList<>();
    registry
        .listAll()
        .forEach(
            cap ->
                effects.add(
                    new KillSwitchView.CapabilityEffect(
                        cap.code().value(),
                        cap.state().name(),
                        globalAi && cap.allowsInference())));
    return effects;
  }

  private String requireActor() {
    String actor = actorPort.currentActorId();
    if (actor == null || actor.isBlank()) {
      throw new IllegalStateException("需要登录态操作人，kill switch 操作不接受匿名/自报");
    }
    return actor;
  }
}
