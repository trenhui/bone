package com.bone.claim.domain.model.claim;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 不可枚举业务编号编解码器（包内共享算法真源）。
 *
 * <p>被 {@link ClaimNoGenerator}（{@code CL}，赔案号）与 {@link ReportNoGenerator}（{@code RP}，报案号）共同委托：
 * 两者<b>前缀不同 ⇒ 号码空间天然不冲突</b>，但共享同一套「雪花 ID → 6 轮 Feistel 双射 → Crockford Base32 → Luhn mod 32
 * 校验位」算法，唯一性链条（零碰撞）与防枚举口径完全一致。两个生成器只是不同的前缀视图。
 *
 * <p><b>校验位口径（兼容性关键）</b>：校验位只覆盖 {@code data = 日期(8) + 载荷(13)}，<b>不含前缀</b>。这样把前缀抽成算法参数后， 既有 {@code
 * CL} 号码逐位不变（已发号与已提交单测零回归）；前缀只参与「按长度切分 + 前缀字面校验」。
 *
 * <p>算法原理、部署前提、唯一性证明见 {@link ClaimNoGenerator} 的类注释。
 */
final class OpaqueNoCodec {

  /** Crockford Base32：剔除 I/L/O/U，避免 0/O、1/I/L 手抄与口播混淆；解码时接受小写与易混字符归一化。 */
  private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

  private static final int BASE = 32;
  private static final int DATE_LEN = 8;
  private static final int PAYLOAD_CHARS = 13;
  private static final int CHECKSUM_CHARS = 1;

  private static final ZoneId BIZ_ZONE = ZoneId.of("Asia/Shanghai");
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

  /** 6 轮常量：取自 SHA-256 前 6 个 32 位字，无特殊含义，仅需固定且互异。 */
  private static final int[] ROUND_CONSTANTS = {
    0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A, 0x510E527F, 0x9B05688C
  };

  private OpaqueNoCodec() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /** 业务东八区当天日期（供各生成器无参 generate 使用，单一时区口径）。 */
  static LocalDate today() {
    return LocalDate.now(BIZ_ZONE);
  }

  /** 总长度 = 前缀长度 + 日期 8 + 载荷 13 + 校验位 1。 */
  static int totalLen(int prefixLen) {
    return prefixLen + DATE_LEN + PAYLOAD_CHARS + CHECKSUM_CHARS;
  }

  /**
   * 生成编号。
   *
   * @throws IllegalArgumentException 雪花 ID 非正（雪花实现保证正数，防御性校验）
   */
  static String generate(String prefix, long snowflakeId, LocalDate date) {
    if (snowflakeId <= 0) {
      throw new IllegalArgumentException("雪花 ID 必须为正数，实际：" + snowflakeId);
    }
    Objects.requireNonNull(prefix, "编号前缀不能为空");
    Objects.requireNonNull(date, "编号日期不能为空");
    String payload = encodeBase32(feistel(snowflakeId));
    String data = date.format(DATE_FMT) + payload;
    return prefix + data + checksum(data);
  }

  /** 校验格式与校验位（只校验结构与校验位，不查库；存在性由仓储判定）。 */
  static boolean isValid(String prefix, String no) {
    if (no == null || prefix == null || no.length() != totalLen(prefix.length())) {
      return false;
    }
    if (!no.startsWith(prefix)) {
      return false;
    }
    String data = no.substring(prefix.length(), no.length() - CHECKSUM_CHARS);
    char expected;
    try {
      expected = checksum(data);
    } catch (IllegalArgumentException e) {
      return false;
    }
    return no.charAt(no.length() - 1) == expected;
  }

  /** 从编号还原雪花 ID（= 对应聚合的物理主键），供对账与追溯。 */
  static long extractSnowflakeId(String prefix, String no) {
    if (!isValid(prefix, no)) {
      throw new IllegalArgumentException("编号非法或校验位不符：" + no);
    }
    String payload = no.substring(prefix.length() + DATE_LEN, no.length() - CHECKSUM_CHARS);
    return unfeistel(decodeBase32(payload));
  }

  // ===================== 6 轮 Feistel 网络（64 位双射） =====================

  private static long feistel(long value) {
    int left = (int) (value >>> 32);
    int right = (int) value;
    for (int round = 0; round < ROUND_CONSTANTS.length; round++) {
      int mixed = left ^ mix32(right ^ ROUND_CONSTANTS[round]);
      left = right;
      right = mixed;
    }
    return ((long) left << 32) | (right & 0xFFFF_FFFFL);
  }

  private static long unfeistel(long value) {
    int left = (int) (value >>> 32);
    int right = (int) value;
    for (int round = ROUND_CONSTANTS.length - 1; round >= 0; round--) {
      int prevRight = left;
      int prevLeft = right ^ mix32(prevRight ^ ROUND_CONSTANTS[round]);
      left = prevLeft;
      right = prevRight;
    }
    return ((long) left << 32) | (right & 0xFFFF_FFFFL);
  }

  /** MurmurHash3 32-bit finalizer；Feistel 的轮函数无需可逆，只要混淆充分。 */
  private static int mix32(int value) {
    value ^= value >>> 16;
    value *= 0x7FEB352D;
    value ^= value >>> 15;
    value *= 0x846CA68B;
    value ^= value >>> 16;
    return value;
  }

  // ===================== Base32 编解码 =====================

  private static String encodeBase32(long value) {
    char[] out = new char[PAYLOAD_CHARS];
    for (int i = PAYLOAD_CHARS - 1; i >= 0; i--) {
      out[i] = ALPHABET[(int) (value & (BASE - 1))];
      value >>>= 5;
    }
    return new String(out);
  }

  private static long decodeBase32(String text) {
    if (text.length() != PAYLOAD_CHARS) {
      throw new IllegalArgumentException("Base32 载荷长度必须为 " + PAYLOAD_CHARS);
    }
    long value = 0;
    for (int i = 0; i < text.length(); i++) {
      int digit = digitOf(text.charAt(i));
      if (digit < 0) {
        throw new IllegalArgumentException("非法 Base32 字符：" + text.charAt(i));
      }
      value = (value << 5) | digit;
    }
    return value;
  }

  /** Crockford 容错解码：小写转大写；易混字符 I/L→1、O→0（生成端永不产出，仅容错手抄）。 */
  private static int digitOf(char ch) {
    char c = Character.toUpperCase(ch);
    if (c >= '0' && c <= '9') {
      return c - '0';
    }
    if (c == 'I' || c == 'L') {
      return 1;
    }
    if (c == 'O') {
      return 0;
    }
    if (c >= 'A' && c <= 'Z') {
      for (int i = 10; i < ALPHABET.length; i++) {
        if (ALPHABET[i] == c) {
          return i;
        }
      }
    }
    return -1;
  }

  // ===================== Luhn mod 32 校验位 =====================

  /**
   * 对 data（日期 8 位 + 载荷 13 位，均为 Base32 字符）计算校验位——不含前缀（兼容性口径，见类注释）。
   *
   * <p>Luhn mod N 性质：检出全部单字符错误与绝大多数相邻转置。校验位从右往左权重从 2 起交替， 验证时含校验位的整串从右往左权重从 1 起交替，两组权重对 data
   * 部分一致，故校验位使总和 ≡ 0 (mod 32)。
   */
  private static char checksum(String data) {
    int sum = 0;
    int weight = 2;
    for (int i = data.length() - 1; i >= 0; i--) {
      int digit = digitOf(data.charAt(i));
      if (digit < 0) {
        throw new IllegalArgumentException("校验输入含非法字符：" + data.charAt(i));
      }
      int product = digit * weight;
      sum += (product / BASE) + (product % BASE);
      weight = weight == 2 ? 1 : 2;
    }
    return ALPHABET[(BASE - (sum % BASE)) % BASE];
  }
}
