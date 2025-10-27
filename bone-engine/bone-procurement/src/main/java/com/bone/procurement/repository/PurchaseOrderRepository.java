package com.bone.procurement.repository;

import com.bone.procurement.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 采购订单仓库接口
 * 使用Spring Data JPA替代不存在的Repository接口
 */
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
}