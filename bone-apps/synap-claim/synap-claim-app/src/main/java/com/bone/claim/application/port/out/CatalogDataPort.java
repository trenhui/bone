package com.bone.claim.application.port.out;

import com.bone.claim.domain.model.adjustment.engine.CatalogAdmission.Verdict;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 外部目录主数据取数端口（出站）。目录由医保局 / 再保 / 接入方维护，有独立版本与生效期， 本仓库只定义查询契约，不持有目录副本（与 ItemCatalogPort 的"两个真源"红线一致）。
 *
 * <p>实现由接入方提供（如 HTTP 客户端 / 本地映射）；未接入时由 {@code ItemCatalogPortAdapter} 走 fail-closed：全部明细返回
 * UNKNOWN，绝不补默值、绝不默认放行。
 */
public interface CatalogDataPort {

  /**
   * 按目录编码 + 业务日取生效版本快照。
   *
   * @param catalogCode 目录编码
   * @param businessDate 判定基准日（发票日期，缺失回落赔案受理日）
   * @param itemCodes 待查编码（去空去重后）
   * @return 该业务日生效的快照；无生效版本 / 取数失败返回 empty（调用方 fail-closed）
   */
  Optional<CatalogSnapshot> lookup(
      String catalogCode, LocalDate businessDate, List<String> itemCodes);

  /** 某业务日生效的目录快照：编码 → 准入结论（未收录的编码由调用方按 UNKNOWN 处理）。 */
  record CatalogSnapshot(
      String catalogCode, LocalDate effectiveDate, Map<String, Verdict> byItemCode) {

    public CatalogSnapshot {
      byItemCode = byItemCode == null ? Map.of() : Map.copyOf(byItemCode);
    }
  }
}
