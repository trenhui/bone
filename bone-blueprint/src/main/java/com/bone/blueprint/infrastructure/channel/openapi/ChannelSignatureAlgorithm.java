package com.bone.blueprint.infrastructure.channel.openapi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 渠道开放平台签名算法 —— 四大平台的签名规则各不相同，这是接真实渠道时最容易写错的一处。
 *
 * <p><b>为什么必须显式枚举而不是「按渠道码猜」</b>：扩展实现里散落 {@code if ("TAOBAO".equals(code))} 之类分支，
 * 将来接第五个渠道时新增分支只能靠人工回忆「京东是不是也用 MD5」。 枚举把「渠道 → 算法」这一映射收敛到一处， 且可被单元测试逐个锁死 （摘要结果是确定值，可直接断言字面量）。
 *
 * <p><b>公共约定</b>：四大平台<strong>都要求按参数名升序拼接</strong> {@code key=value} 再拼接密钥做摘要，
 * 差异只在摘要算法与密钥位置；因此排序拼接抽成 {@link #concat}，各算法只定义「密钥怎么参与」。
 */
public enum ChannelSignatureAlgorithm {

  /** MD5 大写十六进制：淘宝 / 京东 / 拼多多（TOP 系与宙斯系）。 */
  MD5 {
    @Override
    public String sign(String secret, String data) {
      return digest(secret + data);
    }
  },

  /** HMAC-SHA256 大写十六进制：抖音开放平台（参数签名 + Header 令牌）。 */
  HMAC_SHA256 {
    @Override
    public String sign(String secret, String data) {
      try {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return toHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
      } catch (Exception ex) {
        throw new IllegalStateException("抖音渠道签名失败（HmacSHA256 不可用）", ex);
      }
    }
  };

  /**
   * 生成签名（大写十六进制）。
   *
   * @param secret 渠道密钥（appSecret / clientSecret）
   * @param data 参与摘要的待签串（由 {@link #concat} + 密钥位置规则拼出）
   */
  public abstract String sign(String secret, String data);

  /**
   * MD5 摘要，十六进制大写。
   *
   * <p>必须是 {@code static}：枚举常量在类初始化阶段就调用 {@link #sign}，那时还没有枚举实例， 调实例方法会编译不过也拿不到对象。
   */
  private static String digest(String text) {
    try {
      byte[] bytes = MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8));
      return toHex(bytes);
    } catch (Exception ex) {
      throw new IllegalStateException("渠道签名失败（MD5 不可用）", ex);
    }
  }

  /**
   * 按「参数名升序 + {@code key=value} + {@code &} 连接」生成待签串。
   *
   * <p>返回值同时是签名输入与测试断言对象：拼接顺序错一位就得到完全不同的签名， 而渠道侧只回 {@code sign error}、本地永远复现不出——所以它必须能被单测用字面量锁死。
   */
  public static String concat(Map<String, String> params) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> entry : new TreeMap<>(params).entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append('&');
      }
      sb.append(entry.getKey()).append('=').append(entry.getValue());
    }
    return sb.toString();
  }

  private static String toHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
    }
    return sb.toString().toUpperCase();
  }
}
