package com.bone.claim.application.flow;

import com.bone.claim.application.port.out.ScriptConditionPort;
import com.bone.claim.application.port.out.ScriptConditionPort.ConditionVerdict;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage → Trigger → Condition 的流程驱动（M5a 第二阶段骨架）。
 *
 * <p><b>纯编排、零副作用</b>：只依赖求值端口，不持有仓储、不发布领域事件、不改案件状态。求值异常、端口不可用、 越界脚本、非布尔结果一律 {@link
 * FlowOutcome#halted}，调用方据此停止推进，不得继续写业务状态。
 *
 * <p>本骨架不直接改写 {@code DecisionGraphExecutor} 的 5 节点主链；生产接线须单独评审（拓扑是版本化资产）。
 */
public final class FlowFireService {

  private static final Logger log = LoggerFactory.getLogger(FlowFireService.class);

  private final ScriptConditionPort conditionPort;

  /** 允许 {@code null} 以表达「沙箱不可用」——此时一律 fail-closed，而不是启动期报错。 */
  public FlowFireService(ScriptConditionPort conditionPort) {
    this.conditionPort = conditionPort;
  }

  public FlowOutcome fire(Stage stage, String event, Map<String, Object> facts) {
    if (stage == null) {
      throw new IllegalArgumentException("阶段不能为空");
    }
    Trigger trigger = stage.trigger();
    if (event == null || !event.equals(trigger.event())) {
      return FlowOutcome.halted(stage, "TRIGGER_NOT_MATCHED");
    }
    if (conditionPort == null) {
      log.warn("M5b sandbox unavailable, fail-closed at stage={}", stage.name());
      return FlowOutcome.halted(stage, "ENGINE_UNAVAILABLE");
    }

    ConditionVerdict verdict;
    try {
      verdict = conditionPort.evaluate(trigger.condition().expression(), facts);
    } catch (RuntimeException e) {
      log.warn("M5b condition port threw at stage={}, fail-closed", stage.name(), e);
      return FlowOutcome.halted(stage, "ENGINE_UNAVAILABLE");
    }

    if (verdict == null || verdict.failed()) {
      String reason =
          verdict == null
              ? "ENGINE_UNAVAILABLE"
              : verdict.rejectReason() == null
                  ? "ENGINE_UNAVAILABLE"
                  : verdict.rejectReason().name();
      log.warn("M5b condition rejected at stage={}, reason={}", stage.name(), reason);
      return FlowOutcome.halted(stage, reason);
    }
    if (!verdict.passed()) {
      return FlowOutcome.halted(stage, "CONDITION_FALSE");
    }
    return FlowOutcome.advanced(stage);
  }
}
