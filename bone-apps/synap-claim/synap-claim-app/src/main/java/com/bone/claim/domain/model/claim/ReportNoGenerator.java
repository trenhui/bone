package com.bone.claim.domain.model.claim;

import java.time.LocalDate;

/**
 * 报案号生成器（生产级，全局唯一 + 高并发零碰撞 + 防枚举 + 可校验）。
 *
 * <p>1:N 建模：一个报案（FNOL，事故通知事件）下可有 N 个赔案（赔付处理单元）。报案号与赔案号是<b>两个独立编号</b>：
 *
 * <ul>
 *   <li>报案号 {@code report_no}：本类生成，{@code RP} 前缀——标识"客户告知了一次事故"，一个报案下挂 N 个赔案；
 *   <li>赔案号 {@code claim_no}：{@link ClaimNoGenerator} 生成，{@code CL} 前缀——标识"我们为这件事办的一笔赔付"。
 * </ul>
 *
 * <p>两者共享 {@link OpaqueNoCodec} 算法真源（雪花 ID → Feistel 双射 → Base32 → 校验位），仅前缀不同， 号码空间天然不冲突；
 * 唯一性、防枚举、校验位口径与赔案号完全一致，详见 {@link ClaimNoGenerator} 类注释。
 *
 * <p><b>格式</b>：{@code RP + yyyyMMdd(Asia/Shanghai) + 13位Crockford Base32 + 1位Luhn32校验码}，共 24 字符。
 *
 * <p><b>可逆性</b>：{@link #extractSnowflakeId(String)} 可从报案号还原报案物理主键（{@code t_claim_report.id}），
 * 供对账追溯。注意报案号还原的是<b>报案 ID</b>而非赔案 ID——这是 1:N 下与赔案号的关键区别。
 */
public final class ReportNoGenerator {

  private static final String PREFIX = "RP";
  public static final int TOTAL_LEN = OpaqueNoCodec.totalLen(PREFIX.length());

  private ReportNoGenerator() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /** 以当前东八区日期生成报案号。 */
  public static String generate(long snowflakeId) {
    return generate(snowflakeId, OpaqueNoCodec.today());
  }

  /** 可注入日期的重载（供确定性单测）。 */
  public static String generate(long snowflakeId, LocalDate reportDate) {
    return OpaqueNoCodec.generate(PREFIX, snowflakeId, reportDate);
  }

  /** 校验报案号格式与校验位。只校验结构与校验位，不查库。 */
  public static boolean isValid(String reportNo) {
    return OpaqueNoCodec.isValid(PREFIX, reportNo);
  }

  /** 从报案号还原报案雪花 ID（= 报案物理主键 t_claim_report.id）。 */
  public static long extractSnowflakeId(String reportNo) {
    return OpaqueNoCodec.extractSnowflakeId(PREFIX, reportNo);
  }
}
