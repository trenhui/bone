package com.bone.procurement.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.procurement.entity.Supplier;
import java.util.Optional;
import java.util.List;
import com.bone.metadata.sdk.query.criteria.Criteria;

/**
 * 供应商数据访问层
 * 提供对供应商表的CRUD操作及自定义查询
 * 继承bone-metadata-sdk的Repository接口，对标MyBatis-Plus功能
 */
public interface SupplierRepository extends Repository<Supplier, Long> {
    
    // 可以通过Criteria构建查询来实现特定的查询需求
    // 如: findByCode、findByNameContaining等功能
}