package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.model.inventory.Inventory;
import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 库存仓储（SDK 代理实现）。 */
public interface InventoryRepository extends Repository<Inventory, Long> {

  /** 按商品 + 仓库定位库存行。 */
  default Inventory findByProduct(Long tenantId, Long productId, String warehouseCode) {
    Criteria<Inventory> criteria =
        Criteria.<Inventory>create()
            .eq(Inventory::getTenantId, tenantId)
            .eq(Inventory::getProductId, productId)
            .eq(Inventory::getWarehouseCode, warehouseCode);
    return findOneByCriteria(criteria);
  }

  /** 某商品在全部仓库的库存行（下单时按此清单扣减）。 */
  default List<Inventory> findByProduct(Long tenantId, Long productId) {
    Criteria<Inventory> criteria =
        Criteria.<Inventory>create()
            .eq(Inventory::getTenantId, tenantId)
            .eq(Inventory::getProductId, productId)
            .orderByAsc(Inventory::getWarehouseCode);
    return findByCriteria(criteria);
  }

  default PageResult<Inventory> findPage(
      Long tenantId, Long productId, Boolean lowStockOnly, int page, int size) {
    Criteria<Inventory> criteria =
        Criteria.<Inventory>create()
            .eq(Inventory::getTenantId, tenantId)
            .eq(productId != null, Inventory::getProductId, productId)
            .orderByAsc(Inventory::getProductId)
            .page(page, size);
    PageResult<Inventory> result = pageByCriteria(criteria);
    // Criteria 不支持列间比较（available_qty <= safety_stock），在此过滤。
    // SDK 的 Criteria 无 groupBy / 表达式比较能力（架构事实），不做硬凑。
    if (Boolean.TRUE.equals(lowStockOnly) && result != null && result.getRecords() != null) {
      List<Inventory> filtered =
          result.getRecords().stream().filter(Inventory::isBelowSafetyStock).toList();
      return PageResult.of(filtered, result.getTotal(), result.getPage(), result.getSize());
    }
    return result;
  }
}
