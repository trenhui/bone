package com.bone.ai.gateway.infrastructure;

import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 失败降备援装饰器（备用模型档位）：主模型调用失败时，用备用模型档位重试一次。
 *
 * <p>韧性对位——赔案核赔等关键流程不应因单一模型档位不可用（限流 / 该档位下线 / 临时 5xx）就硬失败转人工。 与传输层 Resilience4j 熔断/重试（{@link
 * ResilientLlmTransport}，治「瞬时故障」）互补：本装饰器治「档位级不可用」，换模型而非重试同一模型。
 *
 * <p>对上层（网关门面）透明：成功即返回（来自主或备），网关的证据 / 计量 / 缓存逻辑零改动——响应里的 {@code model}
 * 即真实产出模型，天然可归因。备用也失败时抛<b>主模型</b>的原始异常（根因是主档位，便于定位）。
 */
public class FallbackLlmClient implements LlmClient {

  private static final Logger log = LoggerFactory.getLogger(FallbackLlmClient.class);

  private final LlmClient primary;
  private final LlmClient fallback;
  private final String fallbackModel;

  public FallbackLlmClient(LlmClient primary, LlmClient fallback, String fallbackModel) {
    this.primary = primary;
    this.fallback = fallback;
    this.fallbackModel = fallbackModel;
  }

  @Override
  public LlmResponse chat(LlmRequest request) {
    try {
      return primary.chat(request);
    } catch (RuntimeException primaryFailure) {
      log.warn(
          "主模型档位调用失败，降备援重试: model={}, fallback={}, cause={}",
          request.model(),
          fallbackModel,
          primaryFailure.toString());
      try {
        return fallback.chat(request.withModel(fallbackModel));
      } catch (RuntimeException fallbackFailure) {
        log.warn("降备援亦失败: fallback={}, cause={}", fallbackModel, fallbackFailure.toString());
        throw primaryFailure; // 根因是主档位，抛原始异常
      }
    }
  }
}
