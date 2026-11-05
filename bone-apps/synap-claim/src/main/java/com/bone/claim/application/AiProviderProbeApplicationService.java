package com.bone.claim.application;

import com.bone.claim.application.port.out.AiProviderProbePort;
import com.bone.claim.application.port.out.AiProviderProbePort.ProviderProbe;
import com.bone.claim.domain.model.decision.AiDegradationReason;
import com.bone.claim.domain.model.decision.AiModelProvenance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 模型 provider 探活（只读，除一次最小 upstream 调用外无副作用）。
 *
 * <p><b>三道口径里的位置</b>：
 *
 * <ol>
 *   <li>部署态 {@code /ai-readiness}：配置项有没有值、适配器是不是真实实现；
 *   <li><b>连通态（本服务）</b>：此刻这一次调用能不能拿到符合契约的响应；
 *   <li>运行期 {@code /ai-runtime-health}：过去一段时间实际跑成了什么样。
 * </ol>
 *
 * <p>三者不可互相替代。「配置了但不通」在①里是绿的、在③里要等真实案件跑过才红——中间这段时间正是事故窗口；反之「昨天通过、今天密钥过期」
 * 在③里是历史数据、在②里才是当下事实。故本服务<b>不做缓存</b>：缓存会让探活退化成「上次还行」，而运维点它就是为了要「现在」。
 *
 * <p><b>绝不做的事</b>：不写决策轨迹、不改开关、不参与金额或状态判定、不复述上游异常消息原文（异常消息可能含内部地址 / 密钥片段，
 * 且按文本分类本仓已明令禁止）。失败一律归到受控原因码。
 */
@Service
@RequiredArgsConstructor
public class AiProviderProbeApplicationService {

  /** 判词：未配置 provider（不发请求）。 */
  public static final String VERDICT_NOT_CONFIGURED = "NOT_CONFIGURED";

  /** 判词：链路通且符合结构化契约。 */
  public static final String VERDICT_REACHABLE = "REACHABLE";

  /** 随响应下发的口径声明：防止把「通」读成「准」。 */
  public static final String SCOPE_NOTE =
      "连通性探针：只证明本次调用可达且返回合法 JSON，不证明模型判断准确——准确率见 /claims/eval/samples 的 agreementRate。"
          + "每次调用会计一次 provider 用量（场景标签 claim-ai-provider-probe，可与核赔调用分开统计），请勿做成高频轮询。";

  private final AiProviderProbePort aiProviderProbePort;

  public ProviderProbeReport probe() {
    ProviderProbe probe = aiProviderProbePort.probe();
    String verdict = verdictOf(probe);
    return new ProviderProbeReport(
        verdict,
        probe.configured(),
        probe.reachable(),
        probe.model(),
        probe.model() == null ? null : AiModelProvenance.of(probe.model()).name(),
        probe.latencyMs(),
        probe.reason(),
        actionOf(verdict),
        SCOPE_NOTE);
  }

  /** 判词派生：未配置 > 通 > 受控原因码。原因码缺失却不可达属实现违约，按网关异常兜底，绝不给出空判词。 */
  private static String verdictOf(ProviderProbe probe) {
    if (!probe.configured()) {
      return VERDICT_NOT_CONFIGURED;
    }
    if (probe.reachable()) {
      return VERDICT_REACHABLE;
    }
    return isKnownReason(probe.reason())
        ? probe.reason()
        : AiDegradationReason.GATEWAY_ERROR.name();
  }

  private static boolean isKnownReason(String reason) {
    if (reason == null || reason.isBlank()) {
      return false;
    }
    try {
      AiDegradationReason.valueOf(reason);
      return true;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  /**
   * 受控码 → 处置动作。
   *
   * <p>每个动作都必须是「运维下一步能真去做的事」。写「AI 不可用，请检查配置」等于没写——四个码的处置动作互斥，正是分类存在的理由。
   */
  private static String actionOf(String verdict) {
    return switch (verdict) {
      case VERDICT_NOT_CONFIGURED -> "尚未配置真实 provider：设置环境变量 BONE_AI_GATEWAY_BASE_URL（OpenAI 兼容地址，如 https://api.deepseek.com）"
          + "与 BONE_AI_GATEWAY_API_KEY 后重启。不配也能启动，但网关会回落 Mock 替身，"
          + "适配器按 fail-closed 拒绝替身响应，所有 AI 节点走降级。";
      case VERDICT_REACHABLE -> "无需处置：链路此刻可用。若运行期参与率仍为 0，问题不在 provider，看 /ai-runtime-health 的降级原因分布。";
      case "MOCK_NOT_ALLOWED" -> "已配 base-url 但拿到的仍是网关替身响应：检查 provider 装配是否真正生效"
          + "（api-key 占位符是否被解析、LlmClient 是否被本地实现覆盖）。";
      case "OUTPUT_CONTRACT_VIOLATION" -> "上游返回内容不是合法 JSON：确认模型支持 JSON 输出模式，或网关 JsonOutputValidator 是否在拦截；重试无效，改提示词或换模型。";
      case "PROVIDER_TIMEOUT" -> "上游超时：先确认 provider 侧状态，再评估调大 bone.ai.gateway.timeout 或扩容；重试可能有效。";
      default -> "其余网关异常（鉴权 / 限流 / 网络 / 熔断）：查网关侧日志与 provider 控制台；本端点刻意不复述异常消息文本，"
          + "以免把「密钥过期」误报成「限流」。";
    };
  }

  /**
   * 探活结论。
   *
   * @param verdict 判词：{@link #VERDICT_NOT_CONFIGURED} / {@link #VERDICT_REACHABLE} / 受控降级原因码
   * @param configured {@code base-url} 是否有值（配置态）
   * @param reachable 是否拿到符合契约的真实响应（运行态）
   * @param model 上游回传的模型标识
   * @param provenance 模型来源分类（{@link AiModelProvenance}）：证明「是生产模型」还是「是替身」，与运行期健康度同一判据
   * @param latencyMs 端到端耗时
   * @param reason 失败时的受控原因码
   * @param action 该判词对应的处置动作
   * @param note 口径声明
   */
  public record ProviderProbeReport(
      String verdict,
      boolean configured,
      boolean reachable,
      String model,
      String provenance,
      long latencyMs,
      String reason,
      String action,
      String note) {}
}
