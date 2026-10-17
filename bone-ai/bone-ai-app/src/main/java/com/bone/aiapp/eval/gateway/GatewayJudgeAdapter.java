package com.bone.aiapp.eval.gateway;

import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * LLM-as-Judge 适配器：经 {@code LlmClient}（网关）发起评判，使 judge 调用被网关统一计量（成本/审计/配额）。
 *
 * <p>解析模型返回中的 {@code score}/{@code passed}/{@code rationale} 字段；解析失败时降级为
 * score=0/passed=false，避免门禁误放行。
 */
@Component
public class GatewayJudgeAdapter implements JudgePort {

  private static final String JUDGE_SYSTEM =
      "你是严格的 AI 评测裁判。依据判据对「实际输出」相对「期望输出」评分，"
          + "仅输出 JSON：{\"score\":0.0~1.0, \"passed\":true|false, \"rationale\":\"...\"}。";
  private static final Pattern SCORE = Pattern.compile("\"score\"\\s*:\\s*([0-9.]+)");
  private static final Pattern PASSED = Pattern.compile("\"passed\"\\s*:\\s*(true|false)");
  private static final String MODEL = "qwen-judge";

  private final LlmClient llmClient;

  public GatewayJudgeAdapter(LlmClient llmClient) {
    this.llmClient = llmClient;
  }

  @Override
  public JudgeVerdict judge(JudgeInput input) {
    String user =
        String.format(
            "系统：%s%n判据：%s%n输入：%s%n期望：%s%n实际：%s%n",
            nvl(input.system()),
            nvl(input.criterion()),
            nvl(input.input()),
            nvl(input.expected()),
            nvl(input.actual()));
    LlmRequest req =
        LlmRequest.of(MODEL, List.of(LlmMessage.system(JUDGE_SYSTEM), LlmMessage.user(user)));
    LlmResponse resp = llmClient.chat(req);
    return parse(resp.content());
  }

  private JudgeVerdict parse(String content) {
    if (content == null || content.isBlank()) {
      return new JudgeVerdict(0.0, false, "empty judge response");
    }
    Matcher sm = SCORE.matcher(content);
    Matcher pm = PASSED.matcher(content);
    double score = sm.find() ? Double.parseDouble(sm.group(1)) : 0.0;
    boolean passed = pm.find() && Boolean.parseBoolean(pm.group(1));
    return new JudgeVerdict(score, passed, content.trim());
  }

  private static String nvl(String s) {
    return s == null ? "" : s;
  }
}
