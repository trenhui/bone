package com.bone.masterdata.domain.gateway;

import com.bone.masterdata.domain.model.supplier.Vendor;
import java.util.Optional;

/**
 * Vendor 交易与付款资格的出站端口。
 *
 * <p>与 {@link SupplierRepositoryPort} 分开是刻意为之：两者 Owner 不同（财务 / 采购），生产落地时可能落在不同 schema、由不同团队维护；
 * 合并成一个端口会让 Owner 边界在代码层消失。
 *
 * <p>本切片同样只提供内存实现，生产 adapter 属 R-2。
 */
public interface VendorRepositoryPort {

  /** 取某租户下某统一身份的交易付款资格；未登记返回 empty。 */
  Optional<Vendor> findCurrent(long tenantId, String partyId);

  /** 保存（新增或覆盖同 partyId 的当前版本）；{@code expectedVersion} 语义见 {@link SupplierRepositoryPort#save}。 */
  void save(Vendor vendor, long expectedVersion);
}
