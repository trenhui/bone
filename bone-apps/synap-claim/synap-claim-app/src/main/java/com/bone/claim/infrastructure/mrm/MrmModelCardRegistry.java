package com.bone.claim.infrastructure.mrm;

import com.bone.claim.application.port.out.MrmModelCardPort;
import com.bone.claim.domain.model.evaluation.AiModelCard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * MRM 模型卡清单只读 Registry（配置驱动，不落库、不造数）。
 *
 * <p>初态 classpath 配置 {@code mrm-model-cards.json} 的 {@code modelCards} 为空数组——没有已登记模型卡。此时 {@link
 * LoadStatus#NO_MODEL_CARDS} 必须在响应里显式可见：空清单的 {@code inventory().clean()==true} 只是内核「没有
 * blocker」，绝不能被读成「治理健康」。
 *
 * <p>配置缺失/损坏/字段不合约束（如 evalSetHash 非 64 位 sha256、阈值口径倒置）一律降级为 {@link LoadStatus#CONFIG_UNAVAILABLE}
 * 并清空可观测值（fail-closed）：不可信的配置不生效，也不冒充「已验证」。配置替换只需改文件，不需要改 Java / 发版；不提供任何写入口。
 */
@Component
public class MrmModelCardRegistry implements MrmModelCardPort {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final DriftObservation NO_OBSERVATION = new DriftObservation(null, null, null);

  private final List<AiModelCard.ModelCard> cards;
  private final DriftObservation drift;
  private final LoadStatus status;

  /** Spring 装配入口：路径可用环境变量覆盖，默认 classpath 内的空清单。显式标注以区分测试装配构造器。 */
  @Autowired
  public MrmModelCardRegistry(
      @Value("${bone.claim.mrm.model-cards.location:classpath:mrm-model-cards.json}")
          Resource resource) {
    Parsed parsed = load(resource);
    this.cards = parsed.cards();
    this.drift = parsed.drift();
    this.status = parsed.status();
  }

  /** 离线/测试装配：直接给已解析的清单与观测，跳过 IO。 */
  public MrmModelCardRegistry(
      List<AiModelCard.ModelCard> cards, DriftObservation drift, LoadStatus status) {
    this.cards = List.copyOf(cards);
    this.drift = drift;
    this.status = status;
  }

  public List<AiModelCard.ModelCard> cards() {
    return cards;
  }

  public DriftObservation drift() {
    return drift;
  }

  public LoadStatus status() {
    return status;
  }

  private record Parsed(
      List<AiModelCard.ModelCard> cards, DriftObservation drift, LoadStatus status) {}

  private static Parsed load(Resource resource) {
    if (resource == null) {
      return unavailable();
    }
    try (InputStream in = resource.getInputStream()) {
      JsonNode root = MAPPER.readTree(in);
      JsonNode cardsNode = root.path("modelCards");
      if (!cardsNode.isArray()) {
        return unavailable();
      }
      List<AiModelCard.ModelCard> parsedCards = new ArrayList<>();
      for (JsonNode node : cardsNode) {
        parsedCards.add(toCard(node));
      }
      DriftObservation parsedDrift = toDrift(root.path("drift"));
      LoadStatus status = parsedCards.isEmpty() ? LoadStatus.NO_MODEL_CARDS : LoadStatus.LOADED;
      return new Parsed(List.copyOf(parsedCards), parsedDrift, status);
    } catch (Exception ex) {
      return unavailable();
    }
  }

  private static Parsed unavailable() {
    return new Parsed(List.of(), NO_OBSERVATION, LoadStatus.CONFIG_UNAVAILABLE);
  }

  private static AiModelCard.ModelCard toCard(JsonNode node) {
    return new AiModelCard.ModelCard(
        text(node, "modelId"),
        text(node, "promptVersion"),
        text(node, "intendedUse"),
        text(node, "trainingDataLineage"),
        text(node, "evalSetHash"),
        text(node, "metricSlices"),
        text(node, "knownLimitations"),
        text(node, "retirementCondition"),
        text(node, "owner"),
        AiModelCard.Verification.valueOf(text(node, "verification")),
        LocalDate.parse(text(node, "nextReviewDate")));
  }

  private static DriftObservation toDrift(JsonNode node) {
    BigDecimal psi = decimal(node, "psi");
    BigDecimal watchRatio = decimal(node, "watchRatio");
    BigDecimal threshold = decimal(node, "threshold");
    if (psi == null) {
      // 没有观测时阈值缺失不影响 NO_DATA 结论；仍原样带回供审计。
      return new DriftObservation(null, watchRatio, threshold);
    }
    if (psi.signum() < 0
        || watchRatio == null
        || threshold == null
        || watchRatio.compareTo(threshold) >= 0) {
      throw new IllegalArgumentException("漂移配置口径无效：psi/阈值不满足 AiModelCard.drift 前置条件");
    }
    return new DriftObservation(psi, watchRatio, threshold);
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (!value.isTextual() || value.asText().isBlank()) {
      throw new IllegalArgumentException("模型卡字段缺失或非文本：" + field);
    }
    return value.asText();
  }

  private static BigDecimal decimal(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isNull() || value.isMissingNode() ? null : value.decimalValue();
  }
}
