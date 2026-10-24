package com.bone.aiapp.config;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.airag.domain.gateway.GroundingJudgePort;
import com.bone.airag.domain.model.GroundingJudgeVerdict;
import com.bone.airag.domain.model.RagResult;
import com.bone.core.exception.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 语义接地裁判的网关卡面适配（R5）：rag 的 {@link GroundingJudgePort} → {@link LlmGatewayApplicationService}。
 *
 * <p><b>为什么必须走门面而非直连 {@code LlmClient}</b>（与 {@link RagAnswerGenerationAdapter} 同构）：裁判也是模型调用， 直连
 * SPI 会绕过成本红线、不发证据、不进 trace 落盘。经门面后，裁判自动继承网关全治理链：红线短路 → 租户配额 → 提示注入检测（user
 * 原文含检索上下文与模型答案——上下文里夹带的注入指令若已被模型带进答案，这里是又一道 tripwire）→ 档位路由 → 出口脱敏 → 缓存 → 输出内容安全。
 *
 * <p><b>故障收敛（端口契约的核心）</b>：裁判是增强层，不是回答链的 SPOF——
 *
 * <ul>
 *   <li>网关治理拒绝 / provider 故障（{@link BizException}）⇒ {@code JUDGE_UNAVAILABLE}，由回答编排回落确定性守卫 + 留降级痕迹；
 *   <li>模型输出不可解析（无 JSON / 字段缺失）⇒ 同样 {@code JUDGE_UNAVAILABLE}——<b>不得猜着放行，也不得冒充判定</b>： 把解析失败当成
 *       {@code UNSUPPORTED} 是用可用性故障伪造合规拒答，监控会失真；
 *   <li>适配器任何路径都不抛异常——裁判故障把回答链打断等于可用性事故。
 * </ul>
 *
 * <p><b>模型档位</b>：复用 {@code bone.ai.rag-answer-model}（裁判质量对档位敏感，与生成同档起步；实测分流后再拆分独立配置）。
 *
 * <p><b>启用开关</b>：{@code bone.ai.rag.grounding.judge-enabled}（<b>默认 false = opt-in</b>）；开启时本 Bean
 * 才装配（语义层生效），关闭时组合根回落 rag 自动配置的 {@code NoOpGroundingJudge}（语义层零开销，确定性守卫仍在岗）。 默认关的理由：裁判使每次问答成本约翻倍（生成
 * + 裁判两次模型调用），默认只保留零成本确定性守卫；高合规场景显式开启。
 */
@Component
@ConditionalOnProperty(
    name = "bone.ai.rag.grounding.judge-enabled",
    havingValue = "true",
    matchIfMissing = false)
public class LlmGroundingJudgeAdapter implements GroundingJudgePort {

  private static final Logger log = LoggerFactory.getLogger(LlmGroundingJudgeAdapter.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * 裁判系统提示词（NLI 口径 + fail-closed 判定纪律 + 强制 JSON 输出）。
   *
   * <p>三条判定纪律对应三类真实漏判：① 只用上下文、不用自身知识——模型最危险的编造正是「听起来对但上下文没给」； ②
   * 无引用标记的句子按全部上下文判——强制引用是生成侧契约，裁判不能因缺引用就放行；③ 不确定一律判无支撑——裁判自身的 fail-closed， 与检索侧「无出处不得生成」同构。
   */
  private static final String JUDGE_SYSTEM_PROMPT =
      "你是接地性裁判，判断「答案」的每一句话是否被「上下文」支撑。规则："
          + "1. 只依据上下文判断，不得使用自身知识；上下文未提及的事实、数字、比例、期限、结论都属无支撑。"
          + "2. 答案中的引用标记 [n] 表示该句依据第 n 条上下文；无引用标记的句子按全部上下文判断。"
          + "3. 任何一句话无支撑（含数字与上下文不一致、结论上下文未给出、与上下文矛盾）即 overall supported=false。"
          + "4. 无法确定时一律判无支撑。"
          + "只输出 JSON，不要输出任何其他文字："
          + "{\"supported\": true或false, \"reason\": \"简要原因\", \"unsupported_sentences\": [\"无支撑句原文\", ...]}";

  private final LlmGatewayApplicationService gatewayService;
  private final BoneAiProperties properties;

  public LlmGroundingJudgeAdapter(
      LlmGatewayApplicationService gatewayService, BoneAiProperties properties) {
    this.gatewayService = gatewayService;
    this.properties = properties;
  }

  @Override
  public GroundingJudgeVerdict judge(String question, String answer, List<RagResult> contexts) {
    String content;
    try {
      LlmResponse response =
          gatewayService.invoke(
              LlmRequest.of(
                  properties.getRagAnswerModel(),
                  List.of(
                      LlmMessage.system(JUDGE_SYSTEM_PROMPT),
                      LlmMessage.user(buildUserPrompt(question, answer, contexts)))));
      content = response.content();
    } catch (BizException e) {
      // 治理拒绝（注入疑似/配额/红线/内容安全）或 provider 故障：没判，不是判了不过
      log.warn(
          "RAG 语义接地裁判被治理链拒绝或 provider 故障，回落确定性守卫: code={}, detail={}",
          e.getErrorCode(),
          e.getMessage());
      return unavailable(e.getMessage());
    } catch (RuntimeException e) {
      log.warn("RAG 语义接地裁判未知故障，回落确定性守卫: {}", e.toString());
      return unavailable(e.toString());
    }
    return parse(content);
  }

  /** 装配裁判 user 提示词：问题 + 编号上下文 + 待判答案（编号与引用序号一一对应）。 */
  private static String buildUserPrompt(String question, String answer, List<RagResult> contexts) {
    StringBuilder sb = new StringBuilder(256);
    sb.append("问题：").append(question == null ? "" : question).append('\n');
    sb.append("上下文：\n");
    for (int i = 0; i < contexts.size(); i++) {
      RagResult c = contexts.get(i);
      sb.append('[').append(i + 1).append("] ");
      sb.append(c == null || c.text() == null ? "" : c.text());
      sb.append('\n');
    }
    sb.append("答案：").append(answer == null ? "" : answer);
    return sb.toString();
  }

  /** 解析裁判输出：容错围栏 / 前后缀散文，严校验字段（缺 supported 即不可用，不猜）。 */
  private static GroundingJudgeVerdict parse(String content) {
    if (content == null || content.isBlank()) {
      return unavailable("裁判返回空内容");
    }
    int start = content.indexOf('{');
    int end = content.lastIndexOf('}');
    if (start < 0 || end <= start) {
      return unavailable("裁判返回不含 JSON: " + snippet(content));
    }
    try {
      JsonNode root = MAPPER.readTree(content.substring(start, end + 1));
      JsonNode supported = root.get("supported");
      if (supported == null || !supported.isBoolean()) {
        return unavailable("裁判 JSON 缺 supported 布尔字段: " + snippet(content));
      }
      if (supported.asBoolean()) {
        return new GroundingJudgeVerdict(true, GroundingJudgeVerdict.REASON_SUPPORTED, List.of());
      }
      List<String> sentences = new ArrayList<>();
      JsonNode arr = root.get("unsupported_sentences");
      if (arr != null && arr.isArray()) {
        for (JsonNode n : arr) {
          if (n != null && !n.asText().isBlank()) {
            sentences.add(snippet(n.asText()));
          }
        }
      }
      // 无支撑句清单为空时把裁判自述原因落进审计轨迹位（unsupportedSentences），不丢现场
      if (sentences.isEmpty()) {
        JsonNode reason = root.get("reason");
        sentences.add(
            reason == null || reason.asText().isBlank() ? "裁判未给出无支撑句明细" : snippet(reason.asText()));
      }
      return new GroundingJudgeVerdict(
          false, GroundingJudgeVerdict.REASON_UNSUPPORTED, List.copyOf(sentences));
    } catch (Exception e) {
      // JSON 解析异常（含 Jackson 解析失败，受检的 JsonProcessingException 一并兜住）：没判，不是判了不过
      return unavailable("裁判输出不可解析: " + e.getMessage());
    }
  }

  private static GroundingJudgeVerdict unavailable(String detail) {
    return new GroundingJudgeVerdict(
        false,
        GroundingJudgeVerdict.REASON_UNAVAILABLE,
        List.of(detail == null ? "" : snippet(detail)));
  }

  /** 审计轨迹位截断（与 GroundingGuard 的 snippet 口径一致：不把整段塞进日志/响应）。 */
  private static String snippet(String s) {
    String t = s == null ? "" : s;
    return t.length() <= 80 ? t : t.substring(0, 80) + "…";
  }
}
