package com.bone.claim.domain.model.reconciliation.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 待处置差异（对账差异 × 报告，且<b>尚未</b>有处置流水）。
 *
 * <p>放在 {@code domain..projection} 而非 application：写侧仓储允许返回领域读模型（ADR-0030 R2）， 而 application DTO 由
 * domain 反向依赖是禁止的。带 {@code bizDate/channel} 是因为运营看队列时第一个问题是"哪天的、哪个通道的"—— 只给差异 ID 会让人再去查一次报告表。
 *
 * @param diffId 差异 ID（处置接口的入参）
 * @param reportId 所属报告
 * @param bizDate 对账业务日
 * @param channel 对账通道
 * @param claimNo 报案号（额度类差异为空）
 * @param settlementNo 结算单号（额度类差异为空）
 * @param diffType 差异类型（派单依据）
 * @param localAmount 本方金额
 * @param remoteAmount 对方金额
 * @param localStage 本方资金阶段
 * @param remoteStatus 对方状态 / 漂移列名 / 断链列与类型
 * @param createdAt 发现时间
 */
public record PendingDiff(
    Long diffId,
    Long reportId,
    LocalDate bizDate,
    String channel,
    String claimNo,
    String settlementNo,
    String diffType,
    BigDecimal localAmount,
    BigDecimal remoteAmount,
    String localStage,
    String remoteStatus,
    Instant createdAt) {}
