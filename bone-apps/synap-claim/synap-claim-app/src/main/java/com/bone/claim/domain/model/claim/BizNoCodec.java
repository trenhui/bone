package com.bone.claim.domain.model.claim;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 业务号编解码内核（赔案号 / 报案号共用，唯一真源）。
 *
 * <p><b>为什么抽出来</b>：报案号与赔案号是两套语义、两个命名空间，但格式完全同构（前缀 + 日期 + 混淆载荷 + 校验位）。 各写一份等于把 Feistel / Base32 /
 * Luhn32 复制两遍——改一处忘一处就会出现「两种号不同校验规则」， 而校验位恰恰是客服手抄检错的唯一防线。
 *
 * <p><b>格式</b>：{@code 前缀(2) + yyyyMMdd(8, Asia/Shanghai) + 13 位 Crockford Base32 + 1 位 Luhn32}，共 24
 * 字符。
 *
 * <p><b>校验位只覆盖「日期 + 载荷」，不含前缀</b>——这是刻意设计而非疏漏：
 *
 * <ul>
 *   <li>同一天、同一雪花 id 在两个前缀下的校验位相同，故存量回填把 {@code CL…} 改写成 {@code RP…} 后号码依然合法， 迁移不需要在 SQL 里重算密码学置换；
 *   <li>前缀不参与校验 ⇒ 前缀必须由调用方显式传入，{@link #isValid} / {@link #extractSnowflakeId} 会校验前缀，
 *       避免「拿赔案号当报案号查」静默命中。
 * </ul>
 *
 * <p><b>唯一性</b>：雪花 id 全局唯一 → Feistel（双射）→ Base32（双射）⇒ 输出全局唯一，与发号量无关（非概率保证）。 数据库唯一索引仍是最终兜底。
 */
public final class BizNoCodec {

  /** Crockford Base32：剔除 I/L/O/U，避免 0/O、1/I/L 手抄与口播混淆；解码时接受小写与易混字符归一化。 */
  private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

  private static final int BASE = 32;
  private static final int PREFIX_LEN = 2;
  private static final int DATE_LEN = 8;
  private static final int PAYLOAD_CHARS = 13;
  private static final int CHECKSUM_CHARS = 1;

  /** 号码总长度（前缀 2 + 日期 8 + 载荷 13 + 校验 1）。 */
  public static final int TOTAL_LEN = PREFIX_LEN + DATE_LEN + PAYLOAD_CHARS + CHECKSUM_CHARS;

  private static final ZoneId BIZ_ZONE = ZoneId.of("Asia/Shanghai");
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

  /** 6 轮常量：取自 SHA-256 前 6 个 32 位字，无特殊含义，仅需固定且互异。 */
  private static final int[] ROUND_CONSTANTS = {
    0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A, 0x510E527F, 0x9B05688C
  };

  private BizNoCodec() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /** 以当前东八区日期生成。 */
  public static String generate(String prefix, long snowflakeId) {
    return generate(prefix, snowflakeId, LocalDate.now(BIZ_ZONE));
  }

  /**
   * 可注入日期的重载（供确定性单测与「以受理日/出险日编号」的未来扩展使用）。
   *
   * @throws IllegalArgumentException 前缀非法或雪花 ID 非正
   */
  public static String generate(String prefix, long snowflakeId, LocalDate bizDate) {
    requirePrefix(prefix);
    if (snowflakeId <= 0) {
      throw new IllegalArgumentException("雪花 ID 必须为正数，实际：" + snowflakeId);
    }
    Objects.requireNonNull(bizDate, "业务日期不能为空");
    String payload = encodeBase32(feistel(snowflakeId));
    String data = bizDate.format(DATE_FMT) + payload;
    return prefix + data + checksum(data);
  }

  /** 校验前缀、长度与校验位（不查库；存在性由仓储判定）。 */
  public static boolean isValid(String prefix, String no) {
    if (prefix == null || prefix.length() != PREFIX_LEN) {
      return false;
    }
    if (no == null || no.length() != TOTAL_LEN || !no.startsWith(prefix)) {
      return false;
    }
    String data = no.substring(PREFIX_LEN, no.length() - CHECKSUM_CHARS);
    char expected;
    try {
      expected = checksum(data);
    } catch (IllegalArgumentException e) {
      return false;
    }
    return no.charAt(no.length() - 1) == expected;
  }

  /**
   * 从号码还原雪花 ID（= 物理主键），供对账与追溯。
   *
   * @throws IllegalArgumentException 前缀不匹配、号码非法或校验位不符
   */
  public static long extractSnowflakeId(String prefix, String no) {
    requirePrefix(prefix);
    if (!isValid(prefix, no)) {
      throw new IllegalArgumentException(
          "号码非法、校验位不符或前缀不是 " + prefix + "：" + no + "（不同命名空间的号不能互相解析）");
    }
    String payload = no.substring(PREFIX_LEN + DATE_LEN, no.length() - CHECKSUM_CHARS);
    return unfeistel(decodeBase32(payload));
  }

  private static void requirePrefix(String prefix) {
    if (prefix == null || prefix.length() != PREFIX_LEN) {
      throw new IllegalArgumentException("业务号前缀必须为 2 个字符，实际：" + prefix);
    }
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
   * 对 data（日期 8 位 + 载荷 13 位，均为 Base32 字符）计算校验位。
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
