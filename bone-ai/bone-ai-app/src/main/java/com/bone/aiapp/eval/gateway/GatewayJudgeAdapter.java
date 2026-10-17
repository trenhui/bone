package com.bone.aiapp.eval.gateway;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.aiapp.config.BoneAiProperties;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * LLM-as-Judge 适配器：经 {@link LlmGatewayApplicationService}（网关门面）发起评判，使 judge
 * 调用被网关统一计量（成本/审计/配额）并落入证据链。
 *
 * <p><b>为何必须走门面而非直连 {@code LlmClient}（2026-10-17 复核修复）</b>：judge 也是模型调用，直连 SPI 会绕过成本红线、 不发 {@code
 * LlmCallRecorded}、不进 trace 落盘——等于在「模型调用唯一入站」的不变式上开了一道自身侧门。评测调用量在大规模回归时 同样可观，绕过计量即绕过成本治理。
 *
 * <p>解析模型返回中的 {@code score}/{@code passed} 字段；解析失败时降级为 score=0/passed=false，避免门禁误放行。
 */
@Component
public class GatewayJudgeAdapter implements JudgePort {

  private static final String JUDGE_SYSTEM =
      "你是严格的 AI 评测裁判。依据判据对「实际输出」相对「期望输出」评分，"
          + "仅输出 JSON：{\"score\":0.0~1.0, \"passed\":true|false, \"rationale\":\"...\"}。";
  private static final Pattern SCORE = Pattern.compile("\"score\"\\s*:\\s*([0-9.]+)");
  private static final Pattern PASSED = Pattern.compile("\"passed\"\\s*:\\s*(true|false)");

  private final LlmGatewayApplicationService gatewayService;
  private final BoneAiProperties properties;

  public GatewayJudgeAdapter(
      LlmGatewayApplicationService gatewayService, BoneAiProperties properties) {
    this.gatewayService = gatewayService;
    this.properties = properties;
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
        LlmRequest.of(
            properties.getJudgeModel(),
            List.of(LlmMessage.system(JUDGE_SYSTEM), LlmMessage.user(user)),
            input.traceId());
    LlmResponse resp = gatewayService.invoke(req);
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
