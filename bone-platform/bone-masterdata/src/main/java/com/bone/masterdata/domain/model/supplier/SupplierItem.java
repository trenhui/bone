package com.bone.masterdata.domain.model.supplier;

import com.bone.core.exception.DomainException;
import java.math.BigDecimal;

/**
 * 供货条目（Purchasing Org data，Owner = 采购）。
 *
 * <p><b>不可变</b>：改价不修改既有条目，而是追加一个版本更高的新条目。价格历史因此天然可审计， 且历史版本的金额不会被后续改价追溯性改写。
 *
 * @param itemCode 物料/服务编码，同一供货关系内唯一
 * @param version 从 1 开始单调递增的条目版本
 * @param unitPrice 含税单价，不得为负
 * @param uom 计量单位
 */
public record SupplierItem(String itemCode, long version, BigDecimal unitPrice, String uom) {

  public static SupplierItem of(String itemCode, long version, BigDecimal unitPrice, String uom) {
    if (isBlank(itemCode)) {
      throw new DomainException("供货条目缺少 itemCode，禁止构造");
    }
    if (version < 1L) {
      throw new DomainException("供货条目版本必须从 1 开始: " + version);
    }
    if (unitPrice == null || unitPrice.signum() < 0) {
      throw new DomainException("供货条目单价必须为非负数: " + itemCode);
    }
    if (isBlank(uom)) {
      throw new DomainException("供货条目缺少计量单位: " + itemCode);
    }
    return new SupplierItem(itemCode.trim(), version, unitPrice, uom.trim());
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
