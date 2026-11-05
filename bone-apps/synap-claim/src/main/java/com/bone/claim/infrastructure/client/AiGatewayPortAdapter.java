package com.bone.claim.infrastructure.client;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.config.LlmGatewayProperties;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.claim.application.port.out.AiGatewayPort;
import com.bone.claim.application.port.out.AiGatewayPort.Fact;
import com.bone.claim.application.port.out.AiGatewayUnavailableException;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.domain.model.decision.AiDegradationReason;
import com.bone.claim.domain.model.decision.AiDiscrepancyCodes;
import com.bone.claim.domain.model.evidence.EvidenceFactPolicy;
import com.bone.claim.domain.security.RejectReasonCodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AI 评估网关适配器（Phase B：复用 bone-ai C5 LLM 网关治理门面）。
 *
 * <p>彻底移除一期本地确定性桩里"自研"的 AI 评估语义，改为委托 {@link LlmGatewayApplicationService}—— 出站 PII 脱敏（OWASP
 * LLM02）、提示注入检测（OWASP LLM01）、结构化输出校验、证据链（LlmCallRecorded）、 成本红线与配额全部由平台网关统一收口（ADR-002 / 能力图谱 §7.2
 * 安全三件套复用 C8）。 synap-claim 只负责"拼 prompt + 解析结构化结论"，不碰任何安全 / 治理实现。
 *
 * <p>网关未配置真实 provider（{@code bone.ai.gateway.base-url}）时自动回落网关内置 MockLlmClient。 G0-2
 * 起该替身遵守结构化输出契约并自报 {@code mock:true}，本适配器<b>显式拒绝</b>替身响应，按 fail-closed 抛 {@link
 * AiGatewayUnavailableException}（消息携带可运维的修法指引），决策图对 LLM 节点降级 FALLBACK—— 绝不拿非结构化 / 不可信 /
 * 替身输出做自动赔付结论。
 *
 * <p>接线位置（ADR-002/§3.2.1）：bone-ai 实现类只在此 {@code infrastructure.client} 包被引入， 业务层经 {@link
 * AiGatewayPort} 防腐层消费，杜绝"绕过防腐层的偷接"。
 */
@Component
public class AiGatewayPortAdapter implements AiGatewayPort {

  /** 业务场景标签：供网关 {@code ModelRouter} 做模型档位路由（简单场景走廉价模型）。 */
  static final String SCENARIO = "claim-ai-assess";

  /**
   * 逻辑别名：仅在未配置 {@code bone.ai.gateway.model} 时作为请求模型使用。
   *
   * <p><b>它不是可发给上游的模型 ID</b>。曾经它就是 {@code LlmRequest#model} 的字面值，后果是：配好真实 provider 后， 上游以 {@code
   * invalid_request_error: The supported API model names are ...} 拒掉每一次调用，而网关把 4xx 收敛成 一句 {@code
   * GATEWAY_ERROR}——探活用配置模型（通）、业务用别名（不通），两边结论相反。真实 provider 上这属于必然失败， 不是边界情况：接任何 provider
   * 都必须让业务请求携带该 provider 认识的模型名。
   */
  static final String MODEL = "synap-claim-ai";

  /**
   * 提示词契约版本：改动 system prompt 的结构化字段集（含拒赔原因码枚举）必须同步 bump。
   *
   * <p>决策复算要求"回到当时那套配置"——同一案件用新提示词重跑会得到不同结论，故提示词版本属审计必留字段 （V5 {@code
   * claim_decision_execution.prompt_version} 注释即"回放与复算必需"）。
   */
  // v4：差异提示不再只做留痕，白名单内的码会驱动非资金流程状态（见 AiDiscrepancyRouting），
  // v5：输入侧补入材料解析事实，并要求 facts / evidenceRefs 必须落回材料定位。
  // 提示词契约随语义变化 bump，回放需能区分「差异码仅展示」「差异码参与路由」「事实必须有出处」三代语义。
  static final String PROMPT_VERSION = "claim-assess-v5";

  /** 差异提示受控码白名单（唯一真源在领域层）：模型不得臆造自由文本结论。 */
  static final List<String> DISCREPANCY_CODES = AiDiscrepancyCodes.ALL;

  /** 认知层数组与字符串上限：防模型输出膨胀撑爆决策轨迹。 */
  static final int MAX_COGNITIVE_ITEMS = 20;

  static final int MAX_COGNITIVE_TEXT = 200;

  /** 异常链分类的最大遍历深度（防自引用 / 循环 cause 把分类逻辑拖死）。 */
  static final int MAX_CAUSE_DEPTH = 8;

  private final LlmGatewayApplicationService gateway;
  private final LlmGatewayProperties properties;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  public AiGatewayPortAdapter(
      LlmGatewayApplicationService gateway, LlmGatewayProperties properties) {
    this.gateway = gateway;
    this.properties = properties;
  }

  /** 测试便捷构造：无配置上下文，请求模型回落逻辑别名（Mock 路径不看该字段）。 */
  AiGatewayPortAdapter(LlmGatewayApplicationService gateway) {
    this(gateway, new LlmGatewayProperties());
  }

  /**
   * 请求模型解析（唯一真源）：优先取部署配置的模型名，缺失才回落逻辑别名。
   *
   * <p>探针与业务必须共用本方法——两处各写一套，就会出现「探活说通、业务全降级」的最坏组合（本方法正是为此而生）。
   */
  static String requestModel(LlmGatewayProperties properties) {
    String configured = properties.getModel();
    return configured == null || configured.isBlank() ? MODEL : configured;
  }

  @Override
  public AiAssessment assess(ClaimContextPort.ClaimContextView ctx) {
    String prompt = buildPrompt(ctx);
    // 材料索引（code|value → 材料事实）：模型给的事实必须能在里面找到同 code 同 value 的条目，
    // 才承认它是"从材料抽取"，而不是复述输入或凭空生成。空索引是合法状态（没有材料）——
    // 此时任何"事实"都无出处，一律丢弃，这正是「无材料案件不得凭空产事实」的落点。
    Map<String, ClaimContextPort.EvidenceFact> materials = materialIndex(ctx.evidenceFacts());
    // 证据定位白名单：evidenceRefs 只含定位不含内容，没有第二条可交叉校验的凭据，故只能按成员资格核。
    Set<String> allowedRefs =
        ctx.evidenceFacts().stream()
            .map(ClaimContextPort.EvidenceFact::evidenceRef)
            .filter(ref -> ref != null && !ref.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    LlmRequest request =
        new LlmRequest(
            requestModel(properties),
            List.of(
                LlmMessage.system(
                    "你是保险理赔医学评估助手。仅依据给定案件事实判断是否需要拒赔建议，"
                        + "并以严格 JSON 输出，字段：rejectSuggested(boolean)、confidence(number,0~1)、"
                        + "rejectReasons(array of string，仅当 rejectSuggested=true 时给出，最多3个，"
                        + "取值严格限定于 [EVIDENCE_INSUFFICIENT, CLINICAL_UNREASONABLE, POLICY_EXCLUSION,"
                        + " AMOUNT_MISMATCH, FRAUD_SUSPECTED, OTHER]；不在清单内的码一律不得输出)。"
                        + "不要输出任何解释性自由文本；rejectReasons 只取上述枚举码，禁止臆造。"
                        + "另可给出认知层字段（缺失即视为空数组，禁止臆造）："
                        + "facts(array of {code,value,evidenceRef}，只能取自下文「材料事实」行：code 照抄该行 code，evidenceRef 必须原样照抄该行 evidenceRef)、"
                        + "evidenceRefs(array of string，只能取自「材料事实」行的 evidenceRef)、clauseRefs(array of string，条款引用)、"
                        + "discrepancies(array of string，取值严格限定于 ["
                        + String.join(", ", AiDiscrepancyCodes.ALL)
                        + "]；不在清单内的码一律不得输出)。每个数组最多20项、每项字符串最多200字符。"
                        + "认知层字段不得影响金额、rejectSuggested 或 confidence；"
                        + "白名单内的差异码仅供系统把案件从自动通过拉回补件/复核，不是拒赔结论。"
                        + "禁止把案件上下文字段（claimType/claimAmount/hasEvidence 等）复述为 facts："
                        + "那是输入回显，不是从材料抽取的事实，系统会按不可核定位丢弃并计数。"
                        + "若材料中的 total_amount 与 claimAmount 不一致，discrepancies 必须包含 AMOUNT_MISMATCH。"),
                LlmMessage.user(prompt)),
            null,
            null,
            true, // jsonResponse：要求结构化输出（网关 JsonOutputValidator 可校验）
            false,
            ctx.claimId() + "-" + UUID.randomUUID(),
            SCENARIO);
    LlmResponse response;
    try {
      response = gateway.invoke(request);
    } catch (RuntimeException ex) {
      // 网关任何异常（不可用 / 红线 / 配额 / 内容安全）→ 统一 fail-closed 降级。
      // 原因码让「超时」与「其他网关故障」在决策轨迹里可区分：前者扩容/重试有用，后者没用。
      throw new AiGatewayUnavailableException(
          "AI 网关评估失败，降级纯规则路径: " + ex.getMessage(), ex, classify(ex));
    }
    // 模型未遵循结构化输出契约 → 视为不可信，fail-closed 降级
    return parse(response, materials, allowedRefs);
  }

  private AiAssessment parse(
      LlmResponse response,
      Map<String, ClaimContextPort.EvidenceFact> materials,
      Set<String> allowedRefs) {
    try {
      JsonNode node = objectMapper.readTree(response.content());
      // G0-2：替身响应显式拒绝。未配 provider 时网关回落 MockLlmClient（自报 mock:true）；
      // 若继续解析会得到「rejectSuggested=false + confidence=null → 转人工」的退化结论，
      // 且 model_version 被伪报成替身模型。显式失败才能给出可运维的修法指引（Rules of ML #10）。
      if (node.path("mock").asBoolean(false)) {
        throw new AiGatewayUnavailableException(
            "检测到 Mock 替身响应，禁止用于自动赔付结论：请配置 bone.ai.gateway.base-url 接入真实 provider",
            AiDegradationReason.MOCK_NOT_ALLOWED);
      }
      boolean reject = node.path("rejectSuggested").asBoolean(false);
      // 置信度 fail-closed（B9）：缺失 / 显式 null / 越界（<0 或 >1）一律视为"无法判定" → null。
      // null 经 DecisionTriage.CONFIDENCE_UNKNOWN 强制转人工，绝不因缺省 0.5 滑入自动通过或复核档。
      JsonNode confNode = node.path("confidence");
      BigDecimal confidence = null;
      if (!confNode.isMissingNode() && !confNode.isNull()) {
        confidence = BigDecimal.valueOf(confNode.asDouble());
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
          confidence = null;
        }
      }
      // 仅当建议拒赔时解析原因码，并经白名单归一化（未知码丢弃、空则兜底 OTHER）—— 保证"拒赔必有可解释原因"
      List<String> reasons =
          reject ? normalizeRejectReasons(node.path("rejectReasons")) : List.of();
      // v1.2 认知层：缺失字段 fail-soft 为空列表，绝不因"认知字段没给全"把一次可用评估判成不可用
      int[] rejected = {0};
      return new AiAssessment(
          true,
          reject,
          confidence,
          reasons,
          resolvedModel(response),
          PROMPT_VERSION,
          groundedFacts(node.path("facts"), materials, rejected),
          verifiedLocators(
              parseBoundedTexts(node.path("evidenceRefs"), null), allowedRefs, rejected),
          parseBoundedTexts(node.path("clauseRefs"), null),
          parseBoundedTexts(node.path("discrepancies"), DISCREPANCY_CODES),
          rejected[0]);
    } catch (AiGatewayUnavailableException ex) {
      // 替身拒绝属于显式降级：保留可运维的原始消息，不要被「解析失败」二次包装掉
      throw ex;
    } catch (Exception ex) {
      throw new AiGatewayUnavailableException(
          "AI 结构化结论解析失败，降级纯规则路径: " + ex.getMessage(),
          ex,
          AiDegradationReason.OUTPUT_CONTRACT_VIOLATION);
    }
  }

  /**
   * 只按异常链类型分类，<b>不匹配消息文本</b>：文案会随上游版本变化，按字符串分类等于把判断建在措辞上， 上游改一句话就会把「超时」静默归成「其他网关故障」。
   *
   * <p>异常链最多遍历 {@value #MAX_CAUSE_DEPTH} 层：自引用或循环 cause 的异常在真实世界里出现过， 这里不能让分类逻辑自己死循环。
   */
  static AiDegradationReason classify(Throwable ex) {
    Throwable current = ex;
    for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (current instanceof SocketTimeoutException
          || current instanceof TimeoutException
          || current instanceof HttpTimeoutException) {
        return AiDegradationReason.PROVIDER_TIMEOUT;
      }
      Throwable next = current.getCause();
      current = next == current ? null : next;
    }
    return AiDegradationReason.GATEWAY_ERROR;
  }

  /**
   * 真实上游模型标识：优先取网关回传的 {@code response.model()}（ModelRouter 实际路由到的模型）， 缺失时退回逻辑别名 {@link
   * #MODEL}——绝不返回空串，避免"AI 真的跑过但溯源字段为空"。
   */
  private static String resolvedModel(LlmResponse response) {
    String model = response.model();
    return model == null || model.isBlank() ? MODEL : model;
  }

  /** 从模型输出抽取拒赔原因码并白名单归一化（见 {@link RejectReasonCodes}）。 */
  private List<String> normalizeRejectReasons(JsonNode raw) {
    if (raw == null || !raw.isArray()) {
      return RejectReasonCodes.normalize(null);
    }
    List<Object> values = new ArrayList<>();
    raw.forEach(v -> values.add(v.isTextual() ? v.asText() : v.toString()));
    return RejectReasonCodes.normalize(values);
  }

  /**
   * 解析结构化事实并逐条做「材料落地」校验：保留下来的是<b>材料侧事实的投影</b>，不是模型的自由输出。
   *
   * <p><b>判据是内容，不是定位</b>：模型自报的 {@code evidenceRef} 只是一个字符串——它可以把真实定位配给编造的值， 也可以给真值配一个不存在的定位。故这里按
   * {@code code|value} 在材料索引里查找：找不到即说明这条"事实"在材料里 并不存在（复述输入 / 凭空生成 /
   * 数值改写），丢弃并计数；找到则<b>连定位一起取材料侧的</b>，模型自报的定位不作为凭据。
   *
   * <p>这是唯一能把「事实是否可核」从人工抽查变成机器判据的位置：实测里模型把案件上下文原样复述成 {@code facts}、 把 {@code claimContext}
   * 编成证据定位，人眼与总量报表都发现不了。
   */
  private List<Fact> groundedFacts(
      JsonNode raw, Map<String, ClaimContextPort.EvidenceFact> materials, int[] rejected) {
    if (raw == null || !raw.isArray()) {
      return List.of();
    }
    List<Fact> facts = new ArrayList<>();
    for (JsonNode item : raw) {
      if (facts.size() >= MAX_COGNITIVE_ITEMS) {
        break;
      }
      if (!item.isObject()) {
        continue;
      }
      String code = bounded(item.path("code").asText(""));
      String value = bounded(item.path("value").asText(""));
      ClaimContextPort.EvidenceFact material = materials.get(factKey(code, value));
      if (material == null) {
        rejected[0]++;
        continue;
      }
      // 一律取材料侧的 code / value / evidenceRef：轨迹里出现的必须是"材料里确有的那一条"，
      // 而不是模型改写后的版本——否则展示面与材料会悄悄分叉。
      facts.add(new Fact(material.code(), material.value(), material.evidenceRef()));
    }
    return List.copyOf(facts);
  }

  /** 材料索引：归一化 code + 去空白 value 作键；同一 (code,value) 出现多次取首条（定位等价）。 */
  private static Map<String, ClaimContextPort.EvidenceFact> materialIndex(
      List<ClaimContextPort.EvidenceFact> facts) {
    Map<String, ClaimContextPort.EvidenceFact> index = new LinkedHashMap<>();
    for (ClaimContextPort.EvidenceFact fact : facts) {
      index.putIfAbsent(factKey(fact.code(), fact.value()), fact);
    }
    return Map.copyOf(index);
  }

  /**
   * 事实键：字段码归一（大小写与分隔符无关），值去首尾空白。
   *
   * <p>只做这两步归一：数值格式改写（{@code 1234.56 → 1,234.56}）不被接受——那属于模型改写材料内容，
   * 丢弃并计入不可核条数是正确方向（可观测），而不是宽容到「看起来像就放行」。
   */
  static String factKey(String code, String value) {
    return EvidenceFactPolicy.normalizeCode(code) + "|" + (value == null ? "" : value.trim());
  }

  /** 证据定位同样要可核：引用一个我没给过的定位，与编造事实是同一类问题。 */
  private static List<String> verifiedLocators(
      List<String> refs, Set<String> allowedRefs, int[] rejected) {
    List<String> verified = new ArrayList<>();
    for (String ref : refs) {
      if (allowedRefs.contains(ref)) {
        verified.add(ref);
      } else {
        rejected[0]++;
      }
    }
    return List.copyOf(verified);
  }

  /** 解析受控字符串数组：白名单（null = 只截断）、长度与数量三重收敛。 */
  private List<String> parseBoundedTexts(JsonNode raw, List<String> whitelist) {
    if (raw == null || !raw.isArray()) {
      return List.of();
    }
    List<String> values = new ArrayList<>();
    for (JsonNode item : raw) {
      if (values.size() >= MAX_COGNITIVE_ITEMS) {
        break;
      }
      if (!item.isTextual()) {
        continue;
      }
      String value = bounded(item.asText());
      if (value.isEmpty() || (whitelist != null && !whitelist.contains(value))) {
        continue;
      }
      values.add(value);
    }
    return List.copyOf(values);
  }

  private static String bounded(String text) {
    if (text == null) {
      return "";
    }
    String trimmed = text.trim();
    return trimmed.length() <= MAX_COGNITIVE_TEXT
        ? trimmed
        : trimmed.substring(0, MAX_COGNITIVE_TEXT);
  }

  /**
   * 拼装提示词：聚合标量（案件面）+ 材料事实（证据面）。
   *
   * <p><b>为什么必须带材料</b>：只有聚合标量时，模型能做的只有「复述输入」——实测轨迹里 {@code aiFacts} 的 code 就是 {@code
   * claimType}/{@code claimAmount}/{@code minOcrConfidence}，{@code aiEvidenceRefs} 恒为 {@code
   * [claimContext]}。那不是抽取，只是把提示词抄回来：对核赔员零信息量，也让「事实是否可核」无从校验。
   *
   * <p>材料事实已由 {@code EvidenceFactPolicy} 过滤与截断（姓名 / 证件 / 病历号不出域），按 {@code
   * docType|code=value|evidenceRef} 逐行给出，便于模型原样引用定位。
   */
  private String buildPrompt(ClaimContextPort.ClaimContextView ctx) {
    StringBuilder prompt =
        new StringBuilder("案件上下文（已脱敏）：")
            .append("claimId=")
            .append(ctx.claimId())
            .append(", claimType=")
            .append(ctx.claimType())
            .append(", claimAmount=")
            .append(ctx.claimAmount())
            .append(", hasEvidence=")
            .append(ctx.hasEvidence())
            .append(", evidenceAllParsed=")
            .append(ctx.evidenceAllParsed())
            .append(", minOcrConfidence=")
            .append(ctx.minOcrConfidence())
            .append(", evidenceAllVerifyPassed=")
            .append(ctx.evidenceAllVerifyPassed())
            .append("\n材料事实（感知层解析结果，每行 docType|code=value|evidenceRef；无材料时为「无」）：");
    List<ClaimContextPort.EvidenceFact> facts = ctx.evidenceFacts();
    if (facts.isEmpty()) {
      prompt.append("\n无");
    } else {
      for (ClaimContextPort.EvidenceFact fact : facts) {
        prompt
            .append("\n- ")
            .append(fact.docType())
            .append('|')
            .append(fact.code())
            .append('=')
            .append(fact.value())
            .append('|')
            .append(fact.evidenceRef());
      }
    }
    return prompt.toString();
  }
}
