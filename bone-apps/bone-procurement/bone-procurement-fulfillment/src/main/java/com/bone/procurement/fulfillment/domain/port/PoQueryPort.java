package com.bone.procurement.fulfillment.domain.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * PO 只读查询端口（履约侧定义，避免 domain 反向依赖 transaction）。
 *
 * <p>实现须只读，不修改 transaction 状态；未找到/异常返回 empty。
 */
public interface PoQueryPort {

  Optional<PoSnapshot> findByNo(Long tenantId, String poNo);

  /** 只读 PO 快照：状态、版本、供应商、行集合（行含数量/单价/金额）。 */
  record PoSnapshot(
      String poNo, Integer version, String status, String vendorPartyId, List<Line> lines) {

    public record Line(
        Integer lineNo,
        String itemId,
        BigDecimal qty,
        BigDecimal netPrice,
        BigDecimal lineAmount) {}

    public boolean isAcknowledged() {
      return "ACKNOWLEDGED".equals(status);
    }

    /** 未开票余额（本域投影）：简化为行金额合计，本期不做部分开票扣减的真实上游。 */
    public BigDecimal openAmount() {
      BigDecimal total = BigDecimal.ZERO;
      for (Line l : lines) {
        total = total.add(l.lineAmount() == null ? BigDecimal.ZERO : l.lineAmount());
      }
      return total;
    }
  }
}
