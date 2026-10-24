package com.bone.aiapp.eval.rag;

import com.bone.aiapp.eval.gateway.JudgeInput;
import com.bone.aiapp.eval.gateway.JudgePort;
import com.bone.aiapp.eval.gateway.JudgeVerdict;
import com.bone.airag.domain.service.AnswerQuality;
import com.bone.airag.domain.service.AnswerQualityJudge;
import com.bone.airag.domain.service.HeuristicAnswerQualityJudge;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * LLM 语义级回答质量裁判（缺口③的语义层）：经 {@link JudgePort}（网关门面）发起评判——judge 也是模型调用， 与 {@code GatewayJudgeAdapter}
 * 同构走「模型调用唯一入站」不变式：成本 / 审计 / 配额 / trace 一样不少。
 *
 * <p><b>与确定性判官的分层</b>（详见 {@link AnswerQualityJudge}）：
 *
 * <ul>
 *   <li>faithfulness / answerRelevance——语义裁判，捕捉词汇级判官的盲区（否定反转 / 同义改写 / 跨句归纳 / 数值推导）；
 *   <li>contextPrecision——委派 {@link HeuristicAnswerQualityJudge}：检索侧词汇级指标无语义歧义， 无需耗一次模型调用（判据口径与 CI
 *       门禁完全一致，两层可对账）。
 * </ul>
 *
 * <p><b>fail-closed 解析</b>：模型输出缺字段 / 数字越界 / 为空时判 0 分并在未支撑断言位带回原因—— 评测门禁的教训是「解析失败不得静默放行」（与 {@code
 * GatewayJudgeAdapter} 的 score=0 降级同姿态）。 无据（上下文空）/ 无产出（答案空）直接复用确定性层的 fail-closed，且省一次模型调用。
 */
@Component
public class LlmAnswerQualityJudge implements AnswerQualityJudge {

  private static final String SYSTEM =
      "你是严格的 RAG 回答质量裁判。仅依据「期望」字段给出的检索上下文评判「实际」答案，" + "不得引入上下文之外的任何知识。";

  private static final String RUBRIC =
      "逐句检查答案断言是否被上下文支撑：faithfulness=被支撑断言数/总断言数（0~1）；"
          + "answer_relevance=答案对问题的覆盖度（0~1）；unsupported_claims=未被支撑的断言原文列表。"
          + "仅输出 JSON：{\"faithfulness\":0.0~1.0,\"answer_relevance\":0.0~1.0,\"unsupported_claims\":[\"...\"]}";

  private static final Pattern FAITHFULNESS =
      Pattern.compile("\"faithfulness\"\\s*:\\s*(-?[0-9.]+)");
  private static final Pattern RELEVANCE =
      Pattern.compile("\"answer_relevance\"\\s*:\\s*(-?[0-9.]+)");
  private static final Pattern UNSUPPORTED =
      Pattern.compile("\"unsupported_claims\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
  private static final Pattern CLAIM_STRING = Pattern.compile("\"([^\"]+)\"");

  private final JudgePort judgePort;
  private final HeuristicAnswerQualityJudge precisionDelegate = new HeuristicAnswerQualityJudge();

  public LlmAnswerQualityJudge(JudgePort judgePort) {
    this.judgePort = judgePort;
  }

  @Override
  public AnswerQuality judge(String query, List<String> contexts, String answer) {
    if (contexts == null || contexts.isEmpty() || answer == null || answer.isBlank()) {
      return AnswerQuality.failClosed();
    }
    JudgeVerdict verdict =
        judgePort.judge(
            new JudgeInput(
                SYSTEM, RUBRIC, nvl(query), String.join("\n---\n", contexts), nvl(answer)));
    AnswerQuality semantic = parse(verdict.rationale());
    // contextPrecision 委派确定性判官：口径与 CI 门禁一致，两层指标可对账
    AnswerQuality precision = precisionDelegate.judge(nvl(query), contexts, answer);
    return new AnswerQuality(
        semantic.faithfulness(),
        semantic.answerRelevance(),
        precision.contextPrecision(),
        semantic.unsupportedClaims());
  }

  /** 解析模型 JSON；任何缺失/越界/畸形数字都 fail-closed 记 0 并带回原因（禁静默放行）。 */
  static AnswerQuality parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return unparseable("empty judge response");
    }
    Matcher fm = FAITHFULNESS.matcher(raw);
    Matcher rm = RELEVANCE.matcher(raw);
    if (!fm.find() || !rm.find()) {
      return unparseable(raw);
    }
    double faithfulness;
    double relevance;
    try {
      faithfulness = clamp(Double.parseDouble(fm.group(1)));
      relevance = clamp(Double.parseDouble(rm.group(1)));
    } catch (NumberFormatException e) {
      return unparseable(raw);
    }
    return new AnswerQuality(faithfulness, relevance, 0.0, parseClaims(raw));
  }

  private static AnswerQuality unparseable(String raw) {
    String reason = "judge_output_unparseable:" + raw.replaceAll("\\s+", " ").strip();
    return new AnswerQuality(0.0, 0.0, 0.0, List.of(truncate(reason, 200)));
  }

  private static List<String> parseClaims(String raw) {
    Matcher am = UNSUPPORTED.matcher(raw);
    if (!am.find()) {
      return List.of();
    }
    List<String> claims = new ArrayList<>();
    Matcher cm = CLAIM_STRING.matcher(am.group(1));
    while (cm.find()) {
      claims.add(cm.group(1));
    }
    return List.copyOf(claims);
  }

  private static double clamp(double v) {
    return v < 0.0 ? 0.0 : Math.min(v, 1.0);
  }

  private static String truncate(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max) + "…";
  }

  private static String nvl(String s) {
    return s == null ? "" : s;
  }
}
