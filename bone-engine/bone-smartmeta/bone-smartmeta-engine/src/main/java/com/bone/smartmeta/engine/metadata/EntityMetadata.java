package com.bone.smartmeta.engine.metadata;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体元数据模型类
 */
@Getter
@Setter
public class EntityMetadata {

    // 实体API名称（唯一标识符）
    private String apiName;
    
    // 显示标签
    private String label;
    
    // 复数显示标签
    private String pluralLabel;
    
    // 数据库表名
    private String tableName;
    
    // 业务域
    private String domain;
    
    // 实体类别
    private String category;
    
    // 描述
    private String description;
    
    // 所有权模型
    private String ownershipModel;
    
    // 对应的Java类
    private Class<?> entityClass;
    
    // 所属包名
    private String packageName;
    
    // 版本号
    private String version;
    
    // 记录类型
    private List<RecordTypeMetadata> recordTypes = new ArrayList<>();
    
    // 字段元数据
    private List<FieldMetadata> fields = new ArrayList<>();
    
    // 验证规则
    private List<ValidationRuleMetadata> validationRules = new ArrayList<>();
    
    // 字段级安全设置
    private List<FieldLevelSecurityMetadata> fieldLevelSecurity = new ArrayList<>();
    
    // 索引
    private List<IndexMetadata> indexes = new ArrayList<>();
    
    // AI相关配置
    private AiMetadata aiMetadata = new AiMetadata();
    
    // 查询缓存超时时间（秒）
    private int queryCacheTtl = 3600;
    
    // 是否可缓存
    private boolean cacheable = true;
    
    // 是否启用历史跟踪
    private boolean historyTrackingEnabled = false;
    
    // 跟踪的字段
    private List<String> trackedFields = new ArrayList<>();
    
    // 字段映射（API名称到字段元数据）
    private transient Map<String, FieldMetadata> fieldMap;

    /**
     * 添加字段元数据
     */
    public void addField(FieldMetadata field) {
        fields.add(field);
        // 重置字段映射缓存
        fieldMap = null;
    }

    /**
     * 根据API名称获取字段元数据
     */
    public FieldMetadata getField(String apiName) {
        if (fieldMap == null) {
            fieldMap = new HashMap<>();
            for (FieldMetadata field : fields) {
                fieldMap.put(field.getApiName(), field);
            }
        }
        return fieldMap.get(apiName);
    }

    /**
     * 检查是否包含指定API名称的字段
     */
    public boolean hasField(String apiName) {
        return getField(apiName) != null;
    }
}
