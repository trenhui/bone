package com.bone.core.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.util.StringUtils;

/**
 * 幂等指纹工具（纯静态，无 Spring 依赖，无业务依赖）。
 *
 * <p><b>为什么单独拆一个类</b>：指纹计算是纯函数——幂等服务用它算请求体指纹，将来业务模块也可能在其他场景复用（如「请求体不变则跳过」的简单去重）。 独立后 {@link
 * IdempotencyService} 也更聚焦编排。
 *
 * <p><b>算法</b>：SHA-256 十六进制。用「键 + 指纹」而非「键 + body 原文」比对——body 可能含不该落库的字段（凭据等），存哈希即可判定异同。
 */
public final class IdempotencyFingerprint {

  private static final String EMPTY = "empty";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private IdempotencyFingerprint() {}

  /**
   * 原始字符串的 SHA-256 指纹。
   *
   * @param rawBody 请求体原始字符串
   * @return 十六进制指纹；空字符串返回 {@code "empty"}
   */
  public static String fingerprint(String rawBody) {
    if (!StringUtils.hasText(rawBody)) {
      return EMPTY;
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(rawBody.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      // SHA-256 一定存在；兜底保持「同 body 同结果」即可
      return Integer.toHexString(rawBody.hashCode());
    }
  }

  /**
   * 任意对象的指纹：字符串直接取，其他对象先序列化成 JSON 再算 SHA-256。
   *
   * <p>序列化失败<strong>不允许降级</strong>为「空指纹」——否则两个不同请求会得到同一指纹，第二次请求会拿到第一次的响应
   * （比不幂等更糟的错误结果）。此处直接失败，让调用方按系统错误处理。
   *
   * @param payload 请求命令对象或原始 body 字符串
   * @return 十六进制指纹
   * @throws IllegalStateException 载荷无法序列化
   */
  public static String fingerprintOf(Object payload) {
    if (payload == null) {
      return EMPTY;
    }
    if (payload instanceof String raw) {
      return fingerprint(raw);
    }
    try {
      return fingerprint(MAPPER.writeValueAsString(payload));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("幂等指纹计算失败（无法序列化载荷）: " + payload.getClass().getName(), ex);
    }
  }
}
