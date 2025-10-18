package com.bone.smartmeta.engine.config;

import com.bone.smartmeta.engine.MetadataEngine;
import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.FieldMetadata;
import com.bone.smartmeta.engine.metadata.AiMetadata;
import com.bone.smartmeta.engine.metadata.ValidationRuleMetadata;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 元数据引擎初始化器
 * 负责初始化元数据引擎，加载默认元数据和配置
 */
@Component
public class MetadataEngineInitializer implements ApplicationRunner {
    
    private static final Logger log = LoggerFactory.getLogger(MetadataEngineInitializer.class);
    
    private final MetadataEngine metadataEngine;
    private final SmartMetaProperties properties;
    private final ResourceLoader resourceLoader;
    
    public MetadataEngineInitializer(MetadataEngine metadataEngine, SmartMetaProperties properties, ResourceLoader resourceLoader) {
        this.metadataEngine = metadataEngine;
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始初始化SmartMeta引擎...");
        
        // 初始化元数据引擎
        initEngine();
        
        // 加载默认元数据
        loadDefaultMetadata();
        
        // 启用热加载（如果配置）
        if (properties.isHotReloadEnabled()) {
            startHotReload();
        }
        
        // 初始化AI功能（如果启用）
        if (properties.isAiEnhancementEnabled()) {
            initAiFeatures();
        }
        
        log.info("SmartMeta引擎初始化完成！");
    }
    
    /**
     * 初始化引擎
     */
    private void initEngine() {
        try {
            // 设置引擎配置
            metadataEngine.initialize();
            
            // 加载配置文件中的元数据
            loadMetadataFromConfig();
            
            log.info("引擎初始化成功");
        } catch (Exception e) {
            log.error("引擎初始化失败", e);
            throw new RuntimeException("SmartMeta引擎初始化失败", e);
        }
    }
    
    /**
     * 加载默认元数据
     */
    private void loadDefaultMetadata() {
        try {
            // 创建默认实体元数据
            createDefaultEntities();
            
            log.info("默认元数据加载成功");
        } catch (Exception e) {
            log.error("默认元数据加载失败", e);
        }
    }
    
    /**
     * 创建默认实体
     */
    private void createDefaultEntities() {
        // 创建User实体
        EntityMetadata userEntity = createUserEntity();
        metadataEngine.registerEntity(userEntity);
        
        // 创建Product实体
        EntityMetadata productEntity = createProductEntity();
        metadataEngine.registerEntity(productEntity);
        
        // 创建Order实体
        EntityMetadata orderEntity = createOrderEntity();
        metadataEngine.registerEntity(orderEntity);
    }
    
    /**
     * 创建用户实体
     */
    private EntityMetadata createUserEntity() {
        EntityMetadata entity = new EntityMetadata();
        entity.setId("user-entity");
        entity.setApiName("User");
        entity.setLabel("用户");
        entity.setDescription("系统用户实体");
        entity.setDomain(properties.getDefaultDomain());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setTags(Arrays.asList("system", "user"));
        
        // 创建字段
        List<FieldMetadata> fields = new ArrayList<>();
        
        // id字段
        fields.add(createField("id", "用户ID", "STRING", true, null, null, "主键ID", null, null));
        
        // username字段
        fields.add(createField("username", "用户名", "STRING", true, 3, 50, "登录用户名", null, Pattern.compile("^[a-zA-Z0-9_]{3,50}$")));
        
        // email字段
        fields.add(createField("email", "邮箱", "STRING", true, null, 100, "用户邮箱", null, Pattern.compile("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")));
        
        // password字段
        fields.add(createField("password", "密码", "STRING", true, 6, 255, "用户密码", null, null));
        
        // status字段
        fields.add(createField("status", "状态", "STRING", true, null, 20, "用户状态", "ACTIVE", null));
        
        // 创建时间字段
        fields.add(createField("createdAt", "创建时间", "DATETIME", true, null, null, "创建时间", "#date()", null));
        
        // 虚拟字段：全名
        FieldMetadata fullNameField = createField("fullName", "全名", "STRING", false, null, null, "用户全名", null, null);
        fullNameField.setVirtual(true);
        fullNameField.setCalculationExpression("concat(#root.username, ' ', #root.email)");
        fields.add(fullNameField);
        
        entity.setFields(fields);
        
        // 添加验证规则
        List<ValidationRuleMetadata> rules = new ArrayList<>();
        rules.add(createValidationRule("uniqueUsername", "用户名必须唯一", "VALIDATION", "#root.username != null", null));
        rules.add(createValidationRule("validEmail", "邮箱格式必须正确", "VALIDATION", "#root.email matches '^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$'", "email"));
        
        entity.setValidationRules(rules);
        
        // 添加AI元数据
        AiMetadata aiMetadata = new AiMetadata();
        aiMetadata.setAiModel(properties.getAi().getDefaultModel());
        aiMetadata.setAiQueryOptimizationEnabled(true);
        aiMetadata.setAiFeatures(Arrays.asList("QUERY_SUGGESTION", "DATA_ANALYSIS"));
        entity.setAiMetadata(aiMetadata);
        
        return entity;
    }
    
    /**
     * 创建产品实体
     */
    private EntityMetadata createProductEntity() {
        EntityMetadata entity = new EntityMetadata();
        entity.setId("product-entity");
        entity.setApiName("Product");
        entity.setLabel("产品");
        entity.setDescription("产品信息实体");
        entity.setDomain(properties.getDefaultDomain());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setTags(Arrays.asList("business", "product"));
        
        // 创建字段
        List<FieldMetadata> fields = new ArrayList<>();
        fields.add(createField("id", "产品ID", "STRING", true, null, null, "产品主键", null, null));
        fields.add(createField("name", "产品名称", "STRING", true, 1, 200, "产品名称", null, null));
        fields.add(createField("price", "价格", "DOUBLE", true, 0.0, 999999.99, "产品价格", null, null));
        fields.add(createField("stock", "库存", "INTEGER", true, 0, 99999, "产品库存", null, null));
        fields.add(createField("category", "分类", "STRING", true, null, 50, "产品分类", null, null));
        
        // 虚拟字段：折扣价格
        FieldMetadata discountPriceField = createField("discountPrice", "折扣价格", "DOUBLE", false, null, null, "产品折扣价格", null, null);
        discountPriceField.setVirtual(true);
        discountPriceField.setCalculationExpression("#root.price * 0.9"); // 9折
        fields.add(discountPriceField);
        
        entity.setFields(fields);
        
        return entity;
    }
    
    /**
     * 创建订单实体
     */
    private EntityMetadata createOrderEntity() {
        EntityMetadata entity = new EntityMetadata();
        entity.setId("order-entity");
        entity.setApiName("Order");
        entity.setLabel("订单");
        entity.setDescription("订单信息实体");
        entity.setDomain(properties.getDefaultDomain());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setTags(Arrays.asList("business", "order"));
        
        // 创建字段
        List<FieldMetadata> fields = new ArrayList<>();
        fields.add(createField("id", "订单ID", "STRING", true, null, null, "订单主键", "#uuid()", null));
        fields.add(createField("userId", "用户ID", "STRING", true, null, null, "用户ID", null, null));
        fields.add(createField("amount", "订单金额", "DOUBLE", true, 0.0, 9999999.99, "订单总金额", null, null));
        fields.add(createField("status", "订单状态", "STRING", true, null, 20, "订单状态", "PENDING", null));
        
        // 虚拟字段：订单描述
        FieldMetadata descriptionField = createField("description", "订单描述", "STRING", false, null, null, "订单描述", null, null);
        descriptionField.setVirtual(true);
        descriptionField.setCalculationExpression("concat('订单号: ', #root.id, ', 金额: ', #root.amount)");
        fields.add(descriptionField);
        
        entity.setFields(fields);
        
        return entity;
    }
    
    /**
     * 创建字段元数据
     */
    private FieldMetadata createField(String apiName, String label, String type, boolean required, 
                                     Object minValue, Object maxValue, String description, 
                                     String defaultValue, Pattern regexPattern) {
        FieldMetadata field = new FieldMetadata();
        field.setApiName(apiName);
        field.setPhysicalName(apiName.toLowerCase());
        field.setLabel(label);
        field.setType(type);
        field.setRequired(required);
        field.setDescription(description);
        
        // 设置最小值
        if (minValue instanceof Number) {
            field.setMinValue(((Number) minValue).doubleValue());
        } else if (minValue instanceof Integer) {
            field.setMinLength((Integer) minValue);
        }
        
        // 设置最大值
        if (maxValue instanceof Number) {
            field.setMaxValue(((Number) maxValue).doubleValue());
        } else if (maxValue instanceof Integer) {
            field.setMaxLength((Integer) maxValue);
        }
        
        field.setDefaultValue(defaultValue);
        // 修复类型不兼容问题，将Pattern对象转换为String
        field.setRegexPattern(regexPattern != null ? regexPattern.toString() : null);
        
        return field;
    }
    
    /**
     * 创建验证规则
     */
    private ValidationRuleMetadata createValidationRule(String name, String message, 
                                                      String type, String expression, 
                                                      String fieldName) {
        ValidationRuleMetadata rule = new ValidationRuleMetadata();
        rule.setName(name);
        rule.setMessage(message);
        rule.setType(type);
        rule.setExpression(expression);
        rule.setFieldName(fieldName);
        
        return rule;
    }
    
    /**
     * 从配置文件加载元数据
     */
    private void loadMetadataFromConfig() {
        try {
            // 尝试从classpath加载元数据文件
            Resource metadataResource = resourceLoader.getResource("classpath:/smartmeta/metadata.json");
            if (metadataResource.exists()) {
                log.info("发现元数据配置文件: {}", metadataResource.getURI());
                // 这里可以实现从文件加载元数据的逻辑
            }
        } catch (Exception e) {
            log.warn("加载元数据配置文件失败", e);
        }
    }
    
    /**
     * 启动热加载
     */
    private void startHotReload() {
        try {
            metadataEngine.startHotReload(properties.getHotReloadInterval());
            log.info("热加载已启用，间隔: {}ms", properties.getHotReloadInterval());
        } catch (Exception e) {
            log.error("启动热加载失败", e);
        }
    }
    
    /**
     * 初始化AI功能
     */
    private void initAiFeatures() {
        try {
            log.info("初始化AI增强功能，使用模型: {}", properties.getAi().getDefaultModel());
            // 这里可以初始化AI相关组件和配置
        } catch (Exception e) {
            log.error("初始化AI功能失败", e);
        }
    }
}