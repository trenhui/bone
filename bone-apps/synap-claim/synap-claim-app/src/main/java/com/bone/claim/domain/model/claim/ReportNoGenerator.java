package com.bone.claim.domain.model.claim;

import java.time.LocalDate;

/**
 * 报案号生成器（前缀 {@code RP}）；编解码内核见 {@link BizNoCodec}。
 *
 * <p><b>为什么报案号需要独立命名空间</b>：报案是「事故告知事件」，赔案是「赔付处理单元」，一个报案可产生 0..N
 * 个赔案。两套编号若共用一个号段，客服无法从号码判断「这是一次告知还是一笔赔付」， 而「报过几次案」与「赔了几笔」本来就是两个不同口径的统计。
 *
 * <p><b>格式</b>：{@code RP + yyyyMMdd(Asia/Shanghai) + 13 位 Crockford Base32 + 1 位 Luhn32}，共 24
 * 字符，与赔案号同构。
 *
 * <p><b>与存量回填的关系</b>：1:1 时代把赔案号 {@code CL…} 的前缀改写为 {@code RP…} 即得合法报案号——校验位只覆盖 日期与载荷，不含前缀（见 {@link
 * BizNoCodec}）。这让迁移不必在 SQL 里重算 Feistel，也不会引入第二套格式规则。
 *
 * <p><b>唯一性</b>：报案号由报案自身雪花 id 派生（Feistel 双射 ⇒ 全局唯一），数据库 {@code uk_report_no} 兜底。 回填复用赔案 id 作报案
 * id，新旧号段因此不会撞号：一个雪花 id 只会被发一次。
 */
public final class ReportNoGenerator {

  /** 报案号前缀（对外语义：Report）。 */
  public static final String PREFIX = "RP";

  /** 总长度（与 {@link BizNoCodec#TOTAL_LEN} 同源，不得各写一份）。 */
  public static final int TOTAL_LEN = BizNoCodec.TOTAL_LEN;

  private ReportNoGenerator() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /** 以当前东八区日期生成报案号。 */
  public static String generate(long snowflakeId) {
    return BizNoCodec.generate(PREFIX, snowflakeId);
  }

  /** 可注入日期的重载（供确定性单测使用）。 */
  public static String generate(long snowflakeId, LocalDate bizDate) {
    return BizNoCodec.generate(PREFIX, snowflakeId, bizDate);
  }

  /** 校验报案号格式与校验位（不查库）。 */
  public static boolean isValid(String reportNo) {
    return BizNoCodec.isValid(PREFIX, reportNo);
  }

  /**
   * 从报案号还原雪花 ID（= 报案物理主键）。
   *
   * @throws IllegalArgumentException 号码非法、校验位不符或前缀不是 {@code RP}
   */
  public static long extractSnowflakeId(String reportNo) {
    return BizNoCodec.extractSnowflakeId(PREFIX, reportNo);
  }
}
