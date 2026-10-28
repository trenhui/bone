package com.bone.claim.application;

import com.bone.claim.application.DecisionGraphExecutor.DecisionExecutionResult;
import com.bone.claim.application.DecisionGraphExecutor.DecisionVersions;
import com.bone.claim.domain.model.decision.DecisionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 决策执行用例入口（执行决策图 → 消费结论）。
 *
 * <p><b>为什么不让 {@link DecisionGraphExecutor} 自己消费结论</b>：执行器承载的是「证据 → 解析 → 核责 → 理算 → 决策」
 * 的确定性链路，它的可测试性依赖「不碰钱、不碰外部动作」；把自动放款塞进去，等于让一个纯 DAG 执行器持有资金能力， 与「AI 不碰钱、结论只由确定性分诊给出」的边界自相矛盾。
 *
 * <p><b>为什么不让 Controller 直接串两个服务</b>：那会把「执行完就消费」这条业务规则藏在入站适配器里——换个入口（批处理、 MQ）就漏掉。用例编排放应用层，入口只调用一次。
 *
 * <p><b>失败语义</b>：决策执行成功而结论消费被跳过（开关关闭 / 超上限 / 非自动通过）<b>不算失败</b>——决策记录已落库、
 * 轨迹可回放；跳过原因以受控码写日志，供「为什么这单没自动放款」反查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionRunApplicationService {

  private final DecisionGraphExecutor decisionGraphExecutor;
  private final AutoApprovalApplicationService autoApprovalApplicationService;

  /** 执行结果 + 结论消费结果（消费被跳过时为受控原因码，不影响执行结果本身）。 */
  public record DecisionRunResult(DecisionExecutionResult execution, String consumptionReason) {}

  /** 执行一次决策图，并在结论为自动通过时尝试消费（受开关与单笔上限约束）。 */
  public DecisionRunResult execute(long claimId, DecisionVersions versions) {
    DecisionExecutionResult execution = decisionGraphExecutor.execute(claimId, versions);
    if (!DecisionResult.AUTO_APPROVE.name().equals(execution.decisionResult())) {
      return new DecisionRunResult(execution, "NOT_AUTO_APPROVE");
    }
    var outcome = autoApprovalApplicationService.applyAutoDecision(claimId);
    if (!outcome.applied()) {
      log.info("[决策执行] 自动通过结论未消费: claimId={}, reason={}", claimId, outcome.reason());
    }
    return new DecisionRunResult(execution, outcome.reason());
  }
}
