package com.bone.integration.application.support;

import com.bone.integration.application.port.out.ConnectorSecretCipherPort;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 连接器凭据的收口处理：<b>写入加密、读出剔除、更新沿用</b>。
 *
 * <p>三者缺一不可，只做其中一两项都会留下真实缺口：
 *
 * <ul>
 *   <li><b>只加密不剔除</b> ⇒ 读端照样回吐明文，加密只挡住了库内裸奔，泄露面不变；
 *   <li><b>只剔除不合并</b> ⇒ 前端编辑页把详情 JSON 原样回填（见 ConnectorManagement.tsx 的 {@code
 *       JSON.stringify(connector.config)}），用户点保存就会把凭据键整个丢掉，连接器当场失效；
 *   <li><b>不做合并语义</b> 是最容易踩的坑：任何「详情不返回凭据」的实现都必须配套它，否则 「修好一个泄露、弄坏全部连接器」。
 * </ul>
 *
 * <p><b>为什么出口是「剔除」而不是「掩码」</b>：掩码值（{@code ******}）看起来无害，但会被前端原样回填 并提交回库，把真凭据覆盖成掩码 —— 剔除 +
 * 服务端沿用原值才没有这个回路。
 */
@Component
@RequiredArgsConstructor
public class ConnectorSecretSupport {

  /**
   * 敏感键名单（大小写不敏感，按 key 精确匹配）。
   *
   * <p>刻意**不含** {@code accessKey} / {@code username} / {@code endpoint} / {@code bucket}：它们是定位
   * 信息而非凭据，剔除会让运维在界面上失去可用性。真正的秘密只有认证材料。
   */
  public static final Set<String> SECRET_KEYS =
      Set.of(
          "secretkey",
          "secret",
          "password",
          "passwd",
          "token",
          "accesstoken",
          "refreshtoken",
          "apikey",
          "privatekey",
          "credentials");

  /** 前端可能回填的占位符：出现即视为「未提供」，沿用库中原值。 */
  private static final Set<String> PLACEHOLDERS =
      Set.of("******", "***", "[REDACTED]", "***MASKED***");

  private final ConnectorSecretCipherPort cipher;

  /** 写入前：敏感键值加密。返回新 Map，不改入参。 */
  public Map<String, Object> encryptForPersist(Map<String, Object> config) {
    if (config == null || config.isEmpty()) {
      return config;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : config.entrySet()) {
      Object value = entry.getValue();
      if (isSecret(entry.getKey()) && value instanceof String s && !s.isBlank()) {
        out.put(entry.getKey(), cipher.encrypt(s));
      } else {
        out.put(entry.getKey(), value);
      }
    }
    return out;
  }

  /**
   * 读出前：剔除敏感键，并回报「已配置的敏感键名」供界面提示。
   *
   * <p>返回的键名保持**原库大小写**，便于前端显示「secretKey 已配置」而无需猜命名。
   */
  public MaskedConfig maskForRead(Map<String, Object> config) {
    if (config == null || config.isEmpty()) {
      return new MaskedConfig(Map.of(), List.of());
    }
    Map<String, Object> safe = new LinkedHashMap<>();
    List<String> configured = new java.util.ArrayList<>();
    for (Map.Entry<String, Object> entry : config.entrySet()) {
      if (isSecret(entry.getKey())) {
        configured.add(entry.getKey());
        continue;
      }
      safe.put(entry.getKey(), entry.getValue());
    }
    return new MaskedConfig(safe, configured);
  }

  /**
   * 更新时合并：入参里敏感键<b>缺失或为占位符</b> ⇒ 沿用库中原值；给了新值 ⇒ 取新值。
   *
   * <p>非敏感键一律以入参为准（用户可能 legitimately 想改 endpoint / bucket）。
   */
  public Map<String, Object> mergeOnUpdate(
      Map<String, Object> incoming, Map<String, Object> existing) {
    if (incoming == null) {
      return encryptForPersist(existing);
    }
    Map<String, Object> merged = new LinkedHashMap<>(incoming);
    for (Map.Entry<String, Object> old : nullSafe(existing).entrySet()) {
      if (!isSecret(old.getKey())) {
        continue;
      }
      String key = old.getKey();
      if (!containsKeyIgnoreCase(merged, key)) {
        merged.put(key, old.getValue());
        continue;
      }
      Object supplied = getIgnoreCase(merged, key);
      if (supplied == null
          || (supplied instanceof String s && (s.isEmpty() || PLACEHOLDERS.contains(s.trim())))) {
        // 缺失或占位符 ⇒ 保留原值（空串是「显式清空」，不属于占位符语义）
        merged.put(key, old.getValue());
      }
    }
    return encryptForPersist(merged);
  }

  /** 消费侧（如 S3 客户端）取真实凭据：敏感键值解密。 */
  public Map<String, Object> decryptForConsume(Map<String, Object> config) {
    if (config == null || config.isEmpty()) {
      return config;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : config.entrySet()) {
      Object value = entry.getValue();
      if (isSecret(entry.getKey()) && value instanceof String s && !s.isBlank()) {
        out.put(entry.getKey(), cipher.decrypt(s));
      } else {
        out.put(entry.getKey(), value);
      }
    }
    return out;
  }

  private static Map<String, Object> nullSafe(Map<String, Object> map) {
    return map == null ? Map.of() : map;
  }

  static boolean isSecret(String key) {
    return key != null && SECRET_KEYS.contains(key.toLowerCase(java.util.Locale.ROOT));
  }

  private static boolean containsKeyIgnoreCase(Map<String, Object> map, String key) {
    return getIgnoreCase(map, key) != null;
  }

  private static Object getIgnoreCase(Map<String, Object> map, String key) {
    for (Map.Entry<String, Object> e : map.entrySet()) {
      if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
        return e.getValue();
      }
    }
    return null;
  }

  /** 读出结果：安全配置 + 已配置的敏感键名。 */
  public record MaskedConfig(Map<String, Object> config, List<String> secretKeysConfigured) {
    public Set<String> secretKeyNames() {
      return new LinkedHashSet<>(secretKeysConfigured);
    }
  }
}
