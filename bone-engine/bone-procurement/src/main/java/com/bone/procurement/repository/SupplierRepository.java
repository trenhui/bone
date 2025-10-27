package com.bone.procurement.repository;

import com.bone.procurement.entity.Supplier;
import java.util.Optional;
import java.util.List;

/**
 * 供应商数据访问层
 * 提供对供应商表的CRUD操作及自定义查询
 */
public interface SupplierRepository {
    // 基础CRUD方法
    Supplier save(Supplier supplier);
    Optional<Supplier> findById(Long id);
    void deleteById(Long id);
    List<Supplier> findAll();
    
    // 自定义查询方法
    Optional<Supplier> findByCode(String code);
    List<Supplier> findByNameContaining(String name);
}