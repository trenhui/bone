package com.bone.procurement.readiness;

import com.bone.procurement.governance.application.AiGovernanceApplicationService;
import com.bone.procurement.governance.application.CapabilityView;
import com.bone.procurement.governance.application.SafetyApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 就绪度只读聚合服务：采集运行期事实并交给纯评估器裁决。
 *
 * <p>治理 Bean 受条件开关控制，缺省未装配；用 {@link ObjectProvider} 探测，缺失即"治理未启用"→ UNKNOWN， 保证 readiness
 * 端点在任何配置下都可用（与 advisory 端点不同，后者随治理开关关闭）。评测态只读加载 evalset 真源的 {@code status} 与 {@code
 * needsOwnerReview}，不信任裸配置布尔；读取失败即未知。采集失败一律 UNKNOWN。
 */
@Service
public class AiReadinessApplicationService {

  private static final Logger log = LoggerFactory.getLogger(AiReadinessApplicationService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  static final String DEFAULT_EVALSET_PATH =
      "doc/ai-product/procuraAI/eval/ai-decision-evalset.json";

  private final AiReadinessEvaluator evaluator;
  private final ObjectProvider<SafetyApplicationService> safety;
  private final ObjectProvider<AiGovernanceApplicationService> governance;
  private final String baseUrl;
  private final String evalsetPath;

  public AiReadinessApplicationService(
      AiReadinessEvaluator evaluator,
      ObjectProvider<SafetyApplicationService> safety,
      ObjectProvider<AiGovernanceApplicationService> governance,
      @Value("${bone.ai.gateway.base-url:}") String baseUrl,
      @Value("${bone.procurement.ai-readiness.evalset-path:" + DEFAULT_EVALSET_PATH + "}")
          String evalsetPath) {
    this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    this.safety = Objects.requireNonNull(safety, "safety");
    this.governance = Objects.requireNonNull(governance, "governance");
    this.baseUrl = baseUrl;
    this.evalsetPath = evalsetPath;
  }

  /** 只读：不发起模型调用、不写任何状态。 */
  public AiReadinessView readiness() {
    boolean providerConfigured = baseUrl != null && !baseUrl.isBlank();
    if (!providerConfigured) {
      log.warn("AI provider 未配置（bone.ai.gateway.base-url 为空），当前为 Mock；就绪度不得返回 AVAILABLE");
    }
    AiReadinessFacts facts;
    try {
      SafetyApplicationService safetyService = safety.getIfAvailable();
      AiGovernanceApplicationService governanceService = governance.getIfAvailable();
      boolean governanceEnabled = safetyService != null && governanceService != null;
      Boolean killSwitchEngaged = null;
      if (safetyService != null) {
        killSwitchEngaged = !safetyService.currentKillSwitch().allowsAi();
      }
      boolean inferenceCapabilityRegistered =
          governanceService != null
              && governanceService.listCapabilities().stream()
                  .anyMatch(CapabilityView::allowsInference);
      facts =
          new AiReadinessFacts(
              providerConfigured,
              governanceEnabled,
              killSwitchEngaged,
              readEvalFrozen(evalsetPath),
              inferenceCapabilityRegistered);
    } catch (RuntimeException e) {
      log.warn("就绪度事实采集失败，fail-closed 为 UNKNOWN: {}", e.getMessage());
      return AiReadinessView.unknown("FACT_COLLECTION_FAILED");
    }
    return evaluator.evaluate(facts);
  }

  /**
   * 只读加载 evalset 真源：只有 status=frozen 且 needsOwnerReview=false 才视为评测已冻结。 status/seedsDraft 视为未转正
   * （DEGRADED），字段缺失或读取失败视为未知（UNKNOWN），绝不因裸配置布尔抬升判词。
   */
  static Boolean readEvalFrozen(String path) {
    try {
      JsonNode root = MAPPER.readTree(Files.readString(Path.of(path)));
      JsonNode status = root.get("status");
      JsonNode needsReview = root.get("needsOwnerReview");
      if (status == null
          || !status.isTextual()
          || needsReview == null
          || !needsReview.isBoolean()) {
        return null;
      }
      return "frozen".equals(status.asText()) && !needsReview.asBoolean();
    } catch (Exception e) {
      log.warn("评测集读取失败（{}），评测态判为未知: {}", path, e.getMessage());
      return null;
    }
  }
}
