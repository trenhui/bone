package com.bone.claim.domain.model.claim;

import java.time.LocalDate;

/**
 * 赔案号生成器（前缀 {@code CL}）；编解码内核见 {@link BizNoCodec}。
 *
 * <p><b>格式</b>：{@code CL + yyyyMMdd(Asia/Shanghai) + 13 位 Crockford Base32 + 1 位 Luhn32 校验码}，共 24
 * 字符。
 *
 * <p><b>唯一性链条</b>：{@code DistributedIdGenerator.generateLongId()} 雪花 ID 全局唯一 → Feistel 双射 → Base32
 * 双射 ⇒ 赔案号全局唯一（与发号量无关，非概率保证）。数据库 {@code uk_claim_no} 为最终兜底。
 *
 * <p><b>与报案号的关系</b>：报案号（{@link ReportNoGenerator}）与之同构但前缀为 {@code RP}。两者共用 {@link
 * BizNoCodec}，校验位不覆盖前缀，故存量回填的前缀改写不会破坏校验；反过来，前缀不匹配时逆向解析会失败， 两个命名空间不会互相混淆。
 *
 * <p><b>部署前提</b>：多实例必须使用不同的 {@code BONE_DATACENTER_ID} / {@code BONE_WORKER_ID}（各 5 bit，最多 1024
 * 实例），与主键雪花 ID 同一配置源；共用默认 0/0 会让主键与赔案号同时重号——这是部署约束，生成器无法自检。
 */
public final class ClaimNoGenerator {

  /** 赔案号前缀（对外语义：Claim）。 */
  public static final String PREFIX = "CL";

  /** 总长度（与 {@link BizNoCodec#TOTAL_LEN} 同源，不得各写一份）。 */
  public static final int TOTAL_LEN = BizNoCodec.TOTAL_LEN;

  private ClaimNoGenerator() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /** 以当前东八区日期生成赔案号。 */
  public static String generate(long snowflakeId) {
    return BizNoCodec.generate(PREFIX, snowflakeId);
  }

  /**
   * 可注入日期的重载（供确定性单测使用）。
   *
   * @throws IllegalArgumentException 雪花 ID 非正
   */
  public static String generate(long snowflakeId, LocalDate bizDate) {
    return BizNoCodec.generate(PREFIX, snowflakeId, bizDate);
  }

  /** 校验赔案号格式与校验位（供客服手输、入站检索键校验；不查库）。 */
  public static boolean isValid(String claimNo) {
    return BizNoCodec.isValid(PREFIX, claimNo);
  }

  /**
   * 从赔案号还原雪花 ID（= 物理主键），供对账与追溯。
   *
   * @throws IllegalArgumentException 号码非法、校验位不符或前缀不是 {@code CL}
   */
  public static long extractSnowflakeId(String claimNo) {
    return BizNoCodec.extractSnowflakeId(PREFIX, claimNo);
  }
}
