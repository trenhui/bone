package com.bone.claim.domain.model.claim;

import java.time.LocalDate;

/**
 * 赔案号生成器（生产级，全局唯一 + 高并发零碰撞 + 防枚举 + 可校验）。
 *
 * <p>1:N 建模后本生成器专用于<b>赔案号</b>（{@code claim_no}）；报案号由 {@link ReportNoGenerator} 生成（{@code RP} 前缀）。
 * 两者共享 {@link OpaqueNoCodec} 算法真源，仅前缀不同，号码空间天然不冲突。
 *
 * <p><b>格式</b>：{@code CL + yyyyMMdd(Asia/Shanghai) + 13位Crockford Base32 + 1位Luhn32校验码}，共 24 字符。
 *
 * <p><b>为什么不用纯随机</b>：随机 N 位在生日悖论下必然碰撞——10 位 Base32（50 bit）发到约 1.25e7 单时碰撞概率已达 50%，
 * 理赔系统量级远超此数。本生成器对<b>全局唯一的雪花 ID</b> 做 6 轮 Feistel 置换：不同输入必产生不同输出（双射）， 碰撞概率恒为
 * 0；输出外观随机、不可从号码反推发号速率/机器拓扑。
 *
 * <p><b>唯一性链条</b>：{@code DistributedIdGenerator.generateLongId()} 雪花 ID 全局唯一（timestamp 41bit +
 * datacenter 5bit + worker 5bit + sequence 12bit，含时钟回拨防护）→ Feistel 双射 → Base32 双射 → 赔案号全局唯一。 数据库另有
 * {@code uk_claim_no} 全局唯一索引作最终兜底：任何环节出错都会 fail-fast，绝不静默产生重号。
 *
 * <p><b>部署前提</b>：多实例必须使用不同的 {@code BONE_DATACENTER_ID} / {@code BONE_WORKER_ID}（各 5 bit，最多 1024
 * 实例）， 与主键雪花 ID 同一配置源。若多实例共用默认 0/0，主键与赔案号都会重号——这是部署约束，不是生成器能自检的。
 *
 * <p><b>可逆性</b>：Feistel 逆变换使 {@link #extractSnowflakeId(String)} 可从赔案号还原雪花 ID（= 赔案物理主键），
 * 供对账/客服追溯免联查；逆变换的正确性由 round-trip 单测钉死。
 */
public final class ClaimNoGenerator {

  private static final String PREFIX = "CL";
  public static final int TOTAL_LEN = OpaqueNoCodec.totalLen(PREFIX.length());

  private ClaimNoGenerator() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /** 以当前东八区日期生成赔案号。 */
  public static String generate(long snowflakeId) {
    return generate(snowflakeId, OpaqueNoCodec.today());
  }

  /** 可注入日期的重载（供确定性单测与"以出险日/受理日编号"的未来扩展使用）。 */
  public static String generate(long snowflakeId, LocalDate reportDate) {
    return OpaqueNoCodec.generate(PREFIX, snowflakeId, reportDate);
  }

  /** 校验赔案号格式与校验位（供客服手输、入站检索键校验）。只校验结构与校验位，不查库。 */
  public static boolean isValid(String claimNo) {
    return OpaqueNoCodec.isValid(PREFIX, claimNo);
  }

  /** 从赔案号还原雪花 ID（= 赔案物理主键），供对账与追溯。 */
  public static long extractSnowflakeId(String claimNo) {
    return OpaqueNoCodec.extractSnowflakeId(PREFIX, claimNo);
  }
}
