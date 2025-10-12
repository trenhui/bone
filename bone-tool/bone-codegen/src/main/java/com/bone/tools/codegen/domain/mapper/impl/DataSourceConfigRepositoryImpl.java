package com.bone.tools.codegen.domain.mapper.impl;

import com.bone.metadata.sdk.Repository;
import com.bone.tools.codegen.domain.entity.DataSourceConfigDO;
import com.bone.tools.codegen.domain.mapper.DataSourceConfigMapper;
import com.bone.tools.codegen.util.FieldAccessor;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 数据源配置 Repository 实现类
 *
 * @author system
 */
@Repository
public class DataSourceConfigRepositoryImpl implements DataSourceConfigMapper {

    // 临时使用内存存储，实际应该使用数据库
    private final Map<Long, DataSourceConfigDO> dataStore = new HashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1);

    // Repository接口方法实现
    @Override
    public DataSourceConfigDO findById(Long id) {
        return dataStore.get(id);
    }

    @Override
    public List<DataSourceConfigDO> findAll() {
        return new ArrayList<>(dataStore.values());
    }

    @Override
    public DataSourceConfigDO save(DataSourceConfigDO entity) {
        if (entity.getId() == null) {
            entity.setId(idCounter.incrementAndGet());
        }
        dataStore.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public void deleteById(Long id) {
        dataStore.remove(id);
    }

    @Override
    public List<DataSourceConfigDO> findByParams(Map<String, Object> params) {
        return dataStore.values().stream()
                .filter(config -> {
                    if (params == null || params.isEmpty()) {
                        return true;
                    }
                    for (Map.Entry<String, Object> entry : params.entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        if ("url".equals(key) && !FieldAccessor.getFieldValue(config, "url").equals(value)) {
                        return false;
                    }
                    if ("username".equals(key) && !FieldAccessor.getFieldValue(config, "username").equals(value)) {
                        return false;
                    }
                        if ("id".equals(key) && !config.getId().equals(value)) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<DataSourceConfigDO> findByUrlAndUsername(String url, String username) {
        return dataStore.values().stream()
                .filter(config -> {
                    try {
                        // 使用反射获取字段值
                        String configUrl = (String) FieldAccessor.getFieldValue(config, "url");
                        String configUsername = (String) FieldAccessor.getFieldValue(config, "username");
                        return url.equals(configUrl) && username.equals(configUsername);
                    } catch (Exception e) {
                        // 忽略反射异常，返回false表示不匹配
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    public List<DataSourceConfigDO> selectAll() {
        return findAll();
    }

    public List<DataSourceConfigDO> selectByParams(Map<String, Object> params, int offset, int limit) {
        return findAll(); // 简单实现
    }

    public long countByParams(Map<String, Object> params) {
        return dataStore.size();
    }
}