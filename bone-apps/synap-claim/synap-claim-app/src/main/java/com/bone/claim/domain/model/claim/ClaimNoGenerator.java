package com.bone.claim.domain.model.claim;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 报案号生成器（生产级，全局唯一 + 高并发零碰撞 + 防枚举 + 可校验）。
 *
 * <p><b>格式</b>：{@code CL + yyyyMMdd(Asia/Shanghai) + 13位Crockford Base32 + 1位Luhn32校验码}，共 24 字符。
 *
 * <p><b>为什么不用纯随机</b>：随机 N 位在生日悖论下必然碰撞——10 位 Base32（50 bit）发到约 1.25e7 单时碰撞概率已达 50%，
 * 理赔系统量级远超此数。本生成器对<b>全局唯一的雪花 ID</b> 做 6 轮 Feistel 置换：不同输入必产生不同输出（双射）， 碰撞概率恒为
 * 0；输出外观随机、不可从号码反推发号速率/机器拓扑。
 *
 * <p><b>唯一性链条</b>：{@code DistributedIdGenerator.generateLongId()} 雪花 ID 全局唯一（timestamp 41bit +
 * datacenter 5bit + worker 5bit + sequence 12bit，含时钟回拨防护）→ Feistel 双射 → Base32 双射 → 报案号全局唯一。 数据库另有
 * {@code uk_claim_no} 全局唯一索引作最终兜底：任何环节出错都会 fail-fast，绝不静默产生重号。
 *
 * <p><b>部署前提</b>：多实例必须使用不同的 {@code BONE_DATACENTER_ID} / {@code BONE_WORKER_ID}（各 5 bit，最多 1024
 * 实例）， 与主键雪花 ID 同一配置源。若多实例共用默认 0/0，主键与报案号都会重号——这是部署约束，不是生成器能自检的。
 *
 * <p><b>可逆性</b>：Feistel 逆变换使 {@link #extractSnowflakeId(String)} 可从报案号还原雪花 ID（= 物理主键），
 * 供对账/客服追溯免联查；逆变换的正确性由 round-trip 单测钉死。
 */
public final class ClaimNoGenerator {

  /** Crockford Base32：剔除 I/L/O/U，避免 0/O、1/I/L 手抄与口播混淆；解码时接受小写与易混字符归一化。 */
  private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

  private static final int BASE = 32;
  private static final String PREFIX = "CL";
  private static final int DATE_LEN = 8;
  private static final int PAYLOAD_CHARS = 13;
  private static final int CHECKSUM_CHARS = 1;
  public static final int TOTAL_LEN = PREFIX.length() + DATE_LEN + PAYLOAD_CHARS + CHECKSUM_CHARS;

  private static final ZoneId BIZ_ZONE = ZoneId.of("Asia/Shanghai");
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

  /** 6 轮常量：取自 SHA-256 前 6 个 32 位字，无特殊含义，仅需固定且互异。 */
  private static final int[] ROUND_CONSTANTS = {
    0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A, 0x510E527F, 0x9B05688C
  };

  private ClaimNoGenerator() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /** 以当前东八区日期生成报案号。 */
  public static String generate(long snowflakeId) {
    return generate(snowflakeId, LocalDate.now(BIZ_ZONE));
  }

  /**
   * 可注入日期的重载（供确定性单测与"以出险日/受理日编号"的未来扩展使用）。
   *
   * @throws IllegalArgumentException 雪花 ID 非正（雪花实现保证正数，防御性校验）
   */
  public static String generate(long snowflakeId, LocalDate reportDate) {
    if (snowflakeId <= 0) {
      throw new IllegalArgumentException("雪花 ID 必须为正数，实际：" + snowflakeId);
    }
    Objects.requireNonNull(reportDate, "报案日期不能为空");
    String payload = encodeBase32(feistel(snowflakeId));
    String data = reportDate.format(DATE_FMT) + payload;
    return PREFIX + data + checksum(data);
  }

  /**
   * 校验报案号格式与校验位（供客服手输、入站检索键校验）。
   *
   * <p>只校验结构与校验位，不查库；存在性由仓储判定。
   */
  public static boolean isValid(String claimNo) {
    if (claimNo == null || claimNo.length() != TOTAL_LEN || !claimNo.startsWith(PREFIX)) {
      return false;
    }
    String data = claimNo.substring(PREFIX.length(), claimNo.length() - CHECKSUM_CHARS);
    char expected;
    try {
      expected = checksum(data);
    } catch (IllegalArgumentException e) {
      return false;
    }
    return claimNo.charAt(claimNo.length() - 1) == expected;
  }

  /**
   * 从报案号还原雪花 ID（= 物理主键），供对账与追溯。
   *
   * @throws IllegalArgumentException 号码非法或校验位不符
   */
  public static long extractSnowflakeId(String claimNo) {
    if (!isValid(claimNo)) {
      throw new IllegalArgumentException("报案号非法或校验位不符：" + claimNo);
    }
    String payload =
        claimNo.substring(PREFIX.length() + DATE_LEN, claimNo.length() - CHECKSUM_CHARS);
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
