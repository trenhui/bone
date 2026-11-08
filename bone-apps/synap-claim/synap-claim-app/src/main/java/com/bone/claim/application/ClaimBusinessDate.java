package com.bone.claim.application;

import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 赔案业务日：一次就诊实际"发生在哪一天"（发票日优先，缺失回落赔案受理日）。
 *
 * <p><b>为什么要有一个统一入口，而不是各处自己取 {@code invoice_date}</b>：同一个业务日被三件事共用， 三者取到不同日期就会出现"算的时候看 A 天、写的时候记 B
 * 天"的账实错位：
 *
 * <ul>
 *   <li>日限额窗口（Q-05，{@code LIABILITY_DAILY_LIMIT} 的业务日累计）；
 *   <li>目录准入判定基准日（缺口 ①，按基准日锁定目录版本）；
 *   <li>额度年度归属（R-DED-01 / R-CAP-03，保单年度由业务日决定，<b>不由处理时刻决定</b>）。
 * </ul>
 *
 * <p><b>为什么不能直接用处理时刻（{@code now}）顶替</b>：9 月的发票 10 月才赔、跨年 12 月的发票次年 1 月才报案，
 * 这类案件按处理时刻归年度会把费用记到<b>下一个保单年度</b>——上一年度的免赔额凭空重置（少赔）， 封顶额度重复满额（超赔），且事后从账上完全看不出记错了。
 *
 * <p><b>受理日取 {@code createdAt} 并转系统时区</b>：这是系统里唯一有"受理"语义的时间戳（案件落库时刻）；它是瞬时值， 转业务日必须选时区。这里与既有 {@code
 * Year.now()} 同口径用系统时区，理由是理赔作业按本地日历排班； 若将来引入保单所在时区，应在本类单点替换，而不是散落到各调用方。
 *
 * <p>两者都缺失（历史案件）返回 {@code null}——**不猜**，由调用方按各自语义 fail-closed（日限额拒算、年度口径退回处理日）。
 */
public final class ClaimBusinessDate {

  private ClaimBusinessDate() {}

  /**
   * 取本案业务日。
   *
   * @return 业务日；发票日与受理时间戳都缺失时返回 {@code null}
   */
  public static LocalDate of(List<ClaimWithItemsProjection> rows) {
    if (rows == null || rows.isEmpty()) {
      return null;
    }
    for (ClaimWithItemsProjection row : rows) {
      if (row != null && row.getInvoiceDate() != null) {
        return row.getInvoiceDate();
      }
    }
    ClaimWithItemsProjection head = rows.get(0);
    if (head == null || head.getCreatedAt() == null) {
      return null;
    }
    return LocalDate.ofInstant(head.getCreatedAt(), ZoneId.systemDefault());
  }
}
