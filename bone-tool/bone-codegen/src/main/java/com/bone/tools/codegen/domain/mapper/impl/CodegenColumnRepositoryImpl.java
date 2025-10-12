package com.bone.tools.codegen.domain.mapper.impl;

// 移除Repository接口实现，避免方法签名不匹配的问题
import com.bone.tools.codegen.domain.entity.CodegenColumnDO;
import com.bone.tools.codegen.domain.mapper.CodegenColumnMapper;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import com.bone.tools.codegen.util.FieldAccessor;

/**
 * 列定义 数据库操作类
 */
public class CodegenColumnRepositoryImpl implements CodegenColumnMapper {

    private final Map<Long, CodegenColumnDO> dataStore = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    // 实现Repository接口方法
    public CodegenColumnDO findById(Long id) {
        return dataStore.get(id);
    }

    public List<CodegenColumnDO> findAll() {
        return new ArrayList<>(dataStore.values());
    }

    public Long save(CodegenColumnDO entity) {
        Long id = idGenerator.incrementAndGet();
        dataStore.put(id, entity);
        return id;
    }

    public boolean deleteById(Long id) {
        return dataStore.remove(id) != null;
    }

    // 实现CodegenColumnMapper接口方法
    @Override
    public List<CodegenColumnDO> selectListByTableId(Long tableId) {
        return dataStore.values().stream()
                .filter(column -> FieldAccessor.getFieldValue(column, "tableId").equals(tableId))
                .sorted(Comparator.comparing(CodegenColumnDO::getId))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteListByTableId(Long tableId) {
        List<Long> idsToDelete = dataStore.values().stream()
                .filter(column -> FieldAccessor.getFieldValue(column, "tableId").equals(tableId))
                .map(CodegenColumnDO::getId)
                .collect(Collectors.toList());
        deleteByIds(idsToDelete);
    }

    @Override
    public List<CodegenColumnDO> findByParams(Map<String, Object> params) {
        return dataStore.values().stream()
                .filter(column -> {
                    if (params == null || params.isEmpty()) {
                        return true;
                    }
                    for (Map.Entry<String, Object> entry : params.entrySet()) {
                        String field = entry.getKey();
                        Object value = entry.getValue();
                        if ("tableId".equals(field) && value instanceof Long) {
                        if (!FieldAccessor.getFieldValue(column, "tableId").equals(value)) {
                            return false;
                        }
                    }
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        ids.forEach(dataStore::remove);
    }
}