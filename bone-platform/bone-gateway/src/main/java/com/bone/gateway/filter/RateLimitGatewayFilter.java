package com.bone.gateway.filter;

import com.bone.gateway.config.GatewayRateLimitProperties;
import java.util.Collections;
import java.util.Optional;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 网关限流：Redis 令牌桶（滑动窗口 INCR + EXPIRE），按 IP/用户维度。 order 在 JWT 鉴权之后（鉴权通过才会注入 X-User-Id）。超限返 429。 */
@Component
public class RateLimitGatewayFilter implements GlobalFilter, Ordered {

  public static final int ORDER = JwtAuthGlobalFilter.ORDER + 10;

  private final GatewayRateLimitProperties properties;
  private final Optional<ReactiveStringRedisTemplate> redisTemplate;

  public RateLimitGatewayFilter(
      GatewayRateLimitProperties properties, ReactiveStringRedisTemplate redisTemplate) {
    this.properties = properties;
    this.redisTemplate = Optional.ofNullable(redisTemplate);
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!properties.isEnabled()) {
      return chain.filter(exchange);
    }
    if (redisTemplate.isEmpty()) {
      return chain.filter(exchange);
    }

    ServerHttpRequest request = exchange.getRequest();
    String key = buildKey(request);
    ReactiveStringRedisTemplate redis = redisTemplate.get();
    String redisKey = "bone:gateway:ratelimit:" + key;

    // 原子自增并仅在首条设置过期：Lua 脚本消除 INCR 与 EXPIRE 间的竞态窗口（避免进程崩溃后计数永不过期导致永久 429）
    RedisScript<Long> incrScript =
        RedisScript.of(
            "local c = redis.call('incr', KEYS[1])\n"
                + "if c == 1 then redis.call('expire', KEYS[1], ARGV[1]) end\n"
                + "return c",
            Long.class);
    return redis
        .execute(
            incrScript,
            Collections.singletonList(redisKey),
            Collections.singletonList(String.valueOf(properties.getWindowSeconds())))
        .next()
        .flatMap(
            count -> {
              if (count == null || count > properties.getCapacity()) {
                exchange
                    .getResponse()
                    .getHeaders()
                    .set("Retry-After", String.valueOf(properties.getWindowSeconds()));
                return GatewayErrorWriter.write(
                    exchange, HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后重试");
              }
              return chain.filter(exchange);
            });
  }

  private String buildKey(ServerHttpRequest request) {
    String path = request.getURI().getPath();
    StringBuilder sb = new StringBuilder(path);
    boolean byIp = properties.isByIp();
    boolean byUser = properties.isByUser();
    if (byIp) {
      sb.append(":ip=").append(resolveIp(request));
    }
    if (byUser) {
      String userId = request.getHeaders().getFirst("X-User-Id");
      if (StringUtils.hasText(userId)) {
        sb.append(":user=").append(userId);
      }
    }
    return sb.toString();
  }

  private static String resolveIp(ServerHttpRequest request) {
    String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
    if (StringUtils.hasText(forwarded)) {
      String first = forwarded.split(",")[0].trim();
      if (!first.isEmpty()) {
        return first;
      }
    }
    String real = request.getHeaders().getFirst("X-Real-IP");
    if (StringUtils.hasText(real)) {
      return real.trim();
    }
    return request.getRemoteAddress() != null
        ? request.getRemoteAddress().getAddress().getHostAddress()
        : "unknown";
  }

  @Override
  public int getOrder() {
    return ORDER;
  }
}
