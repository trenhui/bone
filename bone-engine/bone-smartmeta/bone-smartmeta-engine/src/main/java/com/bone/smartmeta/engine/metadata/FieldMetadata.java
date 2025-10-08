package com.bone.smartmeta.engine.metadata;

import com.bone.smartmeta.engine.annotation.FieldType;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 字段元数据模型类
 */
@Getter
@Setter
public class FieldMetadata {

    // 字段API名称
    private String apiName;
    
    // 显示标签
    private String label;
    
    // 字段类型
    private FieldType type;
    
    // 描述
    private String description;
    
    // 是否必填
    private boolean required;
    
    // 是否唯一
    private boolean unique;
    
    // 长度
    private int length = 255;
    
    // 数字精度
    private int precision;
    
    // 小数位数
    private int scale;
    
    // 默认值
    private String defaultValue;
    
    // 格式模式
    private String pattern;
    
    // 引用的实体
    private String referenceTo;
    
    // 选择列表值
    private List<String> picklistValues = new ArrayList<>();
    
    // 是否建立索引
    private boolean indexed;
    
    // 是否为主键
    private boolean primaryKey;
    
    // 是否为系统字段
    private boolean systemField;
    
    // Java字段名称
    private String fieldName;
    
    // 数据库列名
    private String columnName;
    
    // 是否加密
    private boolean encrypted;
    
    // 加密算法
    private String encryptionAlgorithm;
    
    // 是否可搜索
    private boolean searchable = true;
    
    // 是否可排序
    private boolean sortable = true;
    
    // 公式表达式
    private String formulaExpression;
    
    // 公式返回类型
    private FieldType formulaReturnType;
    
    // 重算配置
    private RecalculationMetadata recalculation;
    
    // 业务规则
    private List<BusinessRuleMetadata> businessRules = new ArrayList<>();

    /**
     * 添加业务规则
     */
    public void addBusinessRule(BusinessRuleMetadata rule) {
        businessRules.add(rule);
    }
}
