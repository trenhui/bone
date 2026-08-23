package com.bone.integration.infrastructure.external;

import com.bone.integration.domain.client.ExternalSystemClient;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/** Redis 连接器：基于 {@link RedisTemplate} 的真实调用。 */
@Component("REDIS")
@RequiredArgsConstructor
public class RedisClientImpl implements ExternalSystemClient {

  private final RedisTemplate<String, Object> redisTemplate;

  @Override
  public boolean testConnection(Map<String, Object> config) {
    try {
      return redisTemplate.getConnectionFactory() != null
          && redisTemplate.getConnectionFactory().getConnection().ping() != null;
    } catch (Exception ex) {
      return false;
    }
  }

  @Override
  public Object sendRequest(
      String endpoint, Map<String, Object> params, Map<String, Object> config) {
    String operation = resolveOperation(params, config);
    String key = resolveKey(endpoint, params, config);
    switch (operation.toUpperCase()) {
      case "SET" -> {
        String value = String.valueOf(resolveValue(params, config));
        redisTemplate.opsForValue().set(key, value);
        return Map.of("ok", true, "key", key);
      }
      case "DELETE" -> {
        Boolean deleted = redisTemplate.delete(key);
        return Map.of("deleted", Boolean.TRUE.equals(deleted), "key", key);
      }
      case "EXPIRE" -> {
        long seconds = Long.parseLong(String.valueOf(resolveValue(params, config)));
        Boolean set = redisTemplate.expire(key, java.time.Duration.ofSeconds(seconds));
        return Map.of("expired", Boolean.TRUE.equals(set), "key", key);
      }
      case "GET":
      default -> {
        Object value = redisTemplate.opsForValue().get(key);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("value", value);
        return result;
      }
    }
  }

  @Override
  public String getType() {
    return "REDIS";
  }

  private static String resolveOperation(Map<String, Object> params, Map<String, Object> config) {
    Object op = params != null ? params.get("operation") : null;
    if (op == null) {
      op = config.get("operation");
    }
    return op == null ? "GET" : String.valueOf(op);
  }

  private static String resolveKey(
      String endpoint, Map<String, Object> params, Map<String, Object> config) {
    if (endpoint != null && !endpoint.isBlank()) {
      return endpoint;
    }
    Object key = params != null ? params.get("key") : null;
    if (key == null) {
      key = config.get("key");
    }
    return key == null ? "default" : String.valueOf(key);
  }

  private static Object resolveValue(Map<String, Object> params, Map<String, Object> config) {
    Object value = params != null ? params.get("value") : null;
    if (value == null) {
      value = config.get("value");
    }
    return value;
  }
}
