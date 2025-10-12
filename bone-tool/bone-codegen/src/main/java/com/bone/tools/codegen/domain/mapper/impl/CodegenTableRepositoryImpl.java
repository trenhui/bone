package com.bone.tools.codegen.domain.mapper.impl;

import com.bone.core.model.PageResult;
import com.bone.tools.codegen.domain.entity.CodegenTableDO;
import com.bone.tools.codegen.domain.mapper.CodegenTableMapper;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * 代码生成表 Repository 实现类
 *
 * @author system
 */
@Repository
public class CodegenTableRepositoryImpl implements CodegenTableMapper {

    // 临时使用内存存储
    private final Map<Long, CodegenTableDO> dataStore = new HashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1);

    // Repository接口方法实现
    public Optional<CodegenTableDO> findById(Long id) {
        return Optional.ofNullable(dataStore.get(id));
    }

    public List<CodegenTableDO> findAll() {
        return new ArrayList<>(dataStore.values());
    }

    public <S extends CodegenTableDO> S save(S entity) {
        if (entity.getId() == null) {
            entity.setId(idCounter.incrementAndGet());
        }
        dataStore.put(entity.getId(), entity);
        return entity;
    }

    public void deleteById(Long id) {
        dataStore.remove(id);
    }

    // 实现Criteria相关方法
    public List<CodegenTableDO> findByCriteria(Object criteria) {
        // 简化实现，直接返回所有数据
        return new ArrayList<>(dataStore.values());
    }

    public CodegenTableDO findOneByCriteria(Object criteria) {
        List<CodegenTableDO> result = findByCriteria(criteria);
        return result.isEmpty() ? null : result.get(0);
    }

    public PageResult<CodegenTableDO> pageByCriteria(Object criteria) {
        List<CodegenTableDO> all = findByCriteria(criteria);
        // 简化实现，直接返回一个简单的PageResult对象
        try {
            // 假设PageResult有一个无参构造函数
            Class<?> pageResultClass = Class.forName("com.bone.core.model.PageResult");
            Object result = pageResultClass.getDeclaredConstructor().newInstance();
            
            // 尝试设置items和total字段
            try {
                java.lang.reflect.Field itemsField = pageResultClass.getDeclaredField("items");
                itemsField.setAccessible(true);
                itemsField.set(result, all);
            } catch (NoSuchFieldException ignored) {}
            
            try {
                java.lang.reflect.Field totalField = pageResultClass.getDeclaredField("total");
                totalField.setAccessible(true);
                totalField.set(result, Long.valueOf(all.size()));
            } catch (NoSuchFieldException ignored) {}
            
            @SuppressWarnings("unchecked")
            PageResult<CodegenTableDO> typedResult = (PageResult<CodegenTableDO>) result;
            return typedResult;
        } catch (Exception e) {
            // 如果创建失败，返回空列表
            try {
                return (PageResult<CodegenTableDO>) Class.forName("com.bone.core.model.PageResult").getDeclaredConstructor().newInstance();
            } catch (Exception ex) {
                // 如果所有尝试都失败，返回null
                return null;
            }
        }
    }

    // 为了兼容之前的代码，保留这个方法
    public List<CodegenTableDO> findByDataSourceConfigId(Long dataSourceConfigId) {
        return dataStore.values().stream()
                .filter(table -> {
                    try {
                        // 使用反射获取dataSourceConfigId字段值
                        java.lang.reflect.Field field = CodegenTableDO.class.getDeclaredField("dataSourceConfigId");
                        field.setAccessible(true);
                        Long tableDataSourceId = (Long) field.get(table);
                        return tableDataSourceId != null && tableDataSourceId.equals(dataSourceConfigId);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }
}