package com.bone.aiapp.eval.application;

import com.bone.aiapp.eval.domain.model.EvalCase;
import com.bone.aiapp.eval.domain.model.EvalRun;
import com.bone.aiapp.eval.domain.repository.EvalCaseRepository;
import com.bone.aiapp.eval.domain.repository.EvalRunRepository;
import com.bone.airag.domain.service.AnswerQuality;
import com.bone.airag.domain.service.AnswerQualityJudge;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 评测资产管理应用服务（写入面：CI 门禁 / 评测管理 REST 落 case 与 run；评判面：RAG 回答质量语义裁判）。
 *
 * <p>存在理由：① web 层不得直注 domain repository（AGENTS §一.3）；② 详设 §2.7「写入走评测管理 REST」—— 此前只有读端点， CI
 * 门禁无法以受控方式回写运行记录，只能直连数据库，评测闭环断在最后一公里。
 *
 * <p>租户由调用方（Controller 经 {@code AiTenantContext}）解析后传入，本服务不感知传输层。
 */
@Component
public class EvalManagementApplicationService {

  private final EvalCaseRepository caseRepository;
  private final EvalRunRepository runRepository;
  private final AnswerQualityJudge answerQualityJudge;

  public EvalManagementApplicationService(
      EvalCaseRepository caseRepository,
      EvalRunRepository runRepository,
      AnswerQualityJudge answerQualityJudge) {
    this.caseRepository = caseRepository;
    this.runRepository = runRepository;
    this.answerQualityJudge = answerQualityJudge;
  }

  /** 落一条评测用例（输入 / 期望 / 标注元数据）。 */
  public EvalCase createCase(
      Long tenantId,
      String scene,
      String caseType,
      String input,
      String expected,
      boolean expectedHit,
      Integer difficulty,
      BigDecimal kappa) {
    EvalCase evalCase =
        EvalCase.of(tenantId, scene, caseType, input, expected, expectedHit, difficulty, kappa);
    caseRepository.save(evalCase);
    return evalCase;
  }

  /** 落一条评测运行（一次门禁判定的快照：预测 / 期望 / 引用 / judge 分 / 结论）。 */
  public EvalRun createRun(
      Long tenantId,
      String gate,
      Long caseId,
      String predicted,
      String expected,
      boolean expectedHit,
      boolean predictedHit,
      String citedClause,
      BigDecimal judgeScore,
      boolean passed) {
    EvalRun run =
        EvalRun.of(
            tenantId,
            gate,
            caseId,
            predicted,
            expected,
            expectedHit,
            predictedHit,
            citedClause,
            judgeScore,
            passed,
            LocalDateTime.now());
    runRepository.save(run);
    return run;
  }

  /**
   * 评判一次「查询 → 检索上下文 → 答案」三元组的回答质量（faithfulness / relevance / precision）。
   *
   * <p>无持久化、无租户（评判是纯函数式评测动作，不落库）；真实 Bean 为 {@code LlmAnswerQualityJudge}（经网关计量的语义裁判），CI 侧由 rag
   * 的确定性判官对等覆盖。
   */
  public AnswerQuality judgeAnswerQuality(String query, List<String> contexts, String answer) {
    return answerQualityJudge.judge(query, contexts, answer);
  }
}
