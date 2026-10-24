package com.bone.airag.domain.service;

import com.bone.airag.domain.gateway.GroundingJudgePort;
import com.bone.airag.domain.model.GroundingJudgeVerdict;
import com.bone.airag.domain.model.RagResult;
import java.util.List;

/**
 * 语义接地裁判的禁用态（Null Object）：恒判 {@code supported=true}，reason={@code JUDGE_DISABLED}。
 *
 * <p>组合根未接入 LLM 裁判时的默认实现（与 {@link NoOpReranker} / {@link NoDocAcl} 同构）——回答链无分支判断， 语义层零开销；接入方以
 * {@code @Bean} 覆盖为网关卡面适配后即生效。
 *
 * <p><b>禁用 ≠ 不校验</b>：确定性守卫 {@link GroundingGuard}（无据拒答 / 引用强制 / 逐句词面支撑 / 数字一致性） 始终在岗，禁用的只是语义增强层。
 */
public class NoOpGroundingJudge implements GroundingJudgePort {

  @Override
  public GroundingJudgeVerdict judge(String question, String answer, List<RagResult> contexts) {
    return new GroundingJudgeVerdict(true, GroundingJudgeVerdict.REASON_DISABLED, List.of());
  }
}
