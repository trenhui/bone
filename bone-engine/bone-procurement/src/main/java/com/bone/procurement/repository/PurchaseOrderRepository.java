package com.bone.procurement.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.procurement.entity.PurchaseOrder;

/**
 * 采购订单仓库接口
 * 继承bone-metadata-sdk的Repository接口，对标MyBatis-Plus功能
 */
public interface PurchaseOrderRepository extends Repository<PurchaseOrder, Long> {
}