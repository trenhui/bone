package com.bone.claim.application.port.out;

/**
 * 模型 provider 连通性探针（出站，一次最小化真实调用）。
 *
 * <p><b>它补的是哪个洞</b>：{@code /claim-ops/ai-readiness} 回答的是「配置项有没有值」，{@code
 * /claim-ops/ai-runtime-health} 回答的是「过去一段时间跑成什么样」。两者之间还缺一个当下的事实——<b>「现在这一刻，provider 到底通不通」</b>。
 * 缺了它，配好环境到第一次真实案件之间是一段盲区：base-url 写错、密钥过期、模型不支持 JSON 输出，任何一条都只能等真实理赔案跑过、
 * 在决策轨迹里留下一次降级，才会被发现；而这些故障的共同特征是<b>「配了」和「通了」看起来完全一样</b>。
 *
 * <p><b>为什么不做成「读配置断言」</b>：读配置只能证明字符串非空。{@code https://api.example.com} 与一个拼错的域名在配置里毫无区别，
 * 密钥过期更是一个合法字符串——只有真的发一次请求才能区分。故本端口必须产生一次真实 upstream 调用（AWS Bedrock 的 smoke invocation、LiteLLM 的
 * {@code /health} 单次补全探活是同一种做法）。
 *
 * <p><b>成本与留痕</b>：探针请求走网关统一治理链（脱敏 / 计量 / 证据），与业务调用同一条路，但用独立场景标签 {@code
 * claim-ai-provider-probe}——成本报表可以按场景把探活开销剔出去，不会与核赔调用混在一起。这是探针能长期存在的前提： 做不到可归属，运维迟早会因为「污染成本看板」而关掉它。
 */
public interface AiProviderProbePort {

  /** 发起一次探活。实现须自行吞掉所有异常并转成 {@link ProviderProbe#reason()}，不得让异常穿到 HTTP 层。 */
  ProviderProbe probe();

  /**
   * 探活原始事实（不含判词）。
   *
   * <p>判词（VERDICT）与处置建议由应用层派生：端口只报「配没配 / 通没通 / 哪个模型 / 多久 / 什么原因码」这些可从外部观测的事实，
   * 避免把文案写进适配器——文案要改时不该动出站实现。
   *
   * @param configured 配置态：{@code bone.ai.gateway.base-url} 是否有值（无值 = 网关回落 Mock，不发请求）
   * @param reachable 运行态：是否拿到了一次符合结构化契约的真实响应
   * @param model 上游回传的模型标识（{@code reachable=false} 时为 {@code null}）
   * @param latencyMs 端到端耗时（毫秒；含网络与网关治理开销）
   * @param reason 失败时的受控原因码（{@link com.bone.claim.domain.model.decision.AiDegradationReason} 的
   *     name）； 成功或未配置时为 {@code null}
   */
  record ProviderProbe(
      boolean configured, boolean reachable, String model, long latencyMs, String reason) {}
}
