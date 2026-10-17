package com.bone.ai.gateway.domain.gateway;

import com.bone.ai.gateway.domain.LlmResponse;
import java.util.Optional;

/**
 * 精确响应缓存端口（确定性请求的重复调用复用）。
 *
 * <p><b>为什么值得做</b>：企业场景里大量请求是「同一份保单条款 + 同一个问题」，每次重算既费钱又拉高 P95。业界（LiteLLM / GPTCache / Portkey）都把
 * exact-match cache 作为成本治理的第一档手段——它没有语义缓存的误命中风险，收益却最直接。
 *
 * <p><b>只缓存确定性请求</b>：门面仅在 {@code temperature=0}（或未指定）时走缓存。temperature>0 的输出本就随机，
 * 缓存会改变业务语义（同一请求两次判定不同才是预期行为）。
 *
 * <p><b>缓存命中不发事件</b>：证据链记录「真实发生的调用」，命中既未触达模型也无 token 消耗，留证反而污染审计口径； 命中只记结构化日志（含估算节省 token
 * 不可知，故不做成本声称）。
 *
 * <p>默认实现 {@code InMemoryLlmResponseCache}（有界 LRU + TTL）。多副本场景需 Redis 等共享缓存，届时以 {@code @Bean} 覆盖。
 */
public interface LlmResponseCache {

  /** 取缓存（命中返回响应副本）。 */
  Optional<LlmResponse> get(String key);

  /** 写缓存（实现应保证有界，不得无界增长）。 */
  void put(String key, LlmResponse response);
}
