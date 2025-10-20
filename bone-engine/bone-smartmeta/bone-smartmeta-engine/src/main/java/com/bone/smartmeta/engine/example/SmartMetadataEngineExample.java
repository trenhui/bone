package com.bone.smartmeta.engine.example;

import com.bone.smartmeta.engine.*;
import com.bone.smartmeta.engine.model.*;
import com.bone.smartmeta.engine.rule.BusinessRuleRegistry;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能元数据引擎使用示例
 * 展示如何使用元数据驱动架构进行实体定义、验证和业务规则处理
 */
public class SmartMetadataEngineExample {
    
    public static void main(String[] args) {
        // 1. 创建引擎配置
        EngineConfiguration configuration = EngineConfiguration.builder()
                .withEnableCache(true)
                .withFieldCalculationEnabled(true)
                .withBusinessRuleValidationEnabled(true)
                .withBusinessRuleExecutionEnabled(true)
                .withRuleExecutionTimeoutMs(5000)
                .build();
        
        // 2. 初始化智能元数据引擎
        SmartMetadataEngine engine = new SmartMetadataEngine(configuration);
        
        try {
            // 3. 定义实体元数据
            defineEntityMetadata(engine);
            
            // 4. 定义业务规则
            defineBusinessRules(engine);
            
            // 5. 使用示例
            createAndValidateEntity(engine);
            
            // 6. 执行业务规则示例
            executeBusinessRules(engine);
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 7. 关闭引擎
            engine.shutdown();
        }
    }
    
    /**
     * 定义实体元数据
     */
    private static void defineEntityMetadata(SmartMetadataEngine engine) {
        System.out.println("=== 定义实体元数据 ===");
        
        // 定义用户实体
        EntityMetadata userEntity = EntityMetadata.builder()
                .entityApiName("user")
                .entityName("用户")
                .description("系统用户实体")
                .build();
        
        // 添加字段元数据
        // ID字段
        FieldMetadata idField = FieldMetadata.builder()
                .fieldName("id")
                .displayName("用户ID")
                .fieldType(FieldMetadata.FieldType.STRING)
                .isRequired(true)
                .isPrimaryKey(true)
                .description("用户唯一标识")
                .build();
        
        // 用户名字段
        FieldMetadata usernameField = FieldMetadata.builder()
                .fieldName("username")
                .displayName("用户名")
                .fieldType(FieldMetadata.FieldType.STRING)
                .isRequired(true)
                .minLength(3)
                .maxLength(50)
                .description("用户登录名")
                .errorMessage("用户名长度必须在3-50个字符之间")
                .build();
        
        // 年龄字段
        FieldMetadata ageField = FieldMetadata.builder()
                .fieldName("age")
                .displayName("年龄")
                .fieldType(FieldMetadata.FieldType.INTEGER)
                .minValue(18)
                .maxValue(150)
                .description("用户年龄")
                .errorMessage("年龄必须在18-150之间")
                .build();
        
        // 余额字段
        FieldMetadata balanceField = FieldMetadata.builder()
                .fieldName("balance")
                .displayName("账户余额")
                .fieldType(FieldMetadata.FieldType.DECIMAL)
                .defaultValue(0.0)
                .minValue(0.0)
                .description("用户账户余额")
                .build();
        
        // 计算字段：是否为VIP
        FieldMetadata vipField = FieldMetadata.builder()
                .fieldName("isVip")
                .displayName("是否VIP")
                .fieldType(FieldMetadata.FieldType.BOOLEAN)
                .isCalculated(true)
                .calculationExpression("${balance} >= 10000")
                .description("余额大于等于10000为VIP")
                .build();
        
        // 添加字段到实体
        userEntity.addField(idField);
        userEntity.addField(usernameField);
        userEntity.addField(ageField);
        userEntity.addField(balanceField);
        userEntity.addField(vipField);
        
        // 定义订单实体
        EntityMetadata orderEntity = EntityMetadata.builder()
                .entityApiName("order")
                .entityName("订单")
                .description("用户订单实体")
                .build();
        
        // 订单字段
        orderEntity.addField(FieldMetadata.builder()
                .fieldName("orderId")
                .displayName("订单ID")
                .fieldType(FieldMetadata.FieldType.STRING)
                .isRequired(true)
                .isPrimaryKey(true)
                .build());
        
        orderEntity.addField(FieldMetadata.builder()
                .fieldName("userId")
                .displayName("用户ID")
                .fieldType(FieldMetadata.FieldType.STRING)
                .isRequired(true)
                .build());
        
        orderEntity.addField(FieldMetadata.builder()
                .fieldName("amount")
                .displayName("订单金额")
                .fieldType(FieldMetadata.FieldType.DECIMAL)
                .isRequired(true)
                .minValue(0.01)
                .build());
        
        orderEntity.addField(FieldMetadata.builder()
                .fieldName("status")
                .displayName("订单状态")
                .fieldType(FieldMetadata.FieldType.STRING)
                .defaultValue("PENDING")
                .allowedValues(Arrays.asList("PENDING", "PAID", "SHIPPED", "DELIVERED", "CANCELLED"))
                .build());
        
        // 计算字段：折扣金额
        orderEntity.addField(FieldMetadata.builder()
                .fieldName("discountAmount")
                .displayName("折扣金额")
                .fieldType(FieldMetadata.FieldType.DECIMAL)
                .isCalculated(true)
                .calculationExpression("${amount} * 0.1") // 10%折扣
                .build());
        
        // 定义关系
        // 用户-订单一对多关系
        RelationshipMetadata userOrdersRel = RelationshipMetadata.builder()
                .relationshipName("orders")
                .targetEntityApiName("order")
                .relationshipType(RelationshipMetadata.RelationshipType.ONE_TO_MANY)
                .sourceField("id")
                .targetField("userId")
                .cascadeType(Arrays.asList(RelationshipMetadata.CascadeType.DELETE))
                .build();
        
        // 订单-用户多对一关系
        RelationshipMetadata orderUserRel = RelationshipMetadata.builder()
                .relationshipName("user")
                .targetEntityApiName("user")
                .relationshipType(RelationshipMetadata.RelationshipType.MANY_TO_ONE)
                .sourceField("userId")
                .targetField("id")
                .fetchType(RelationshipMetadata.FetchType.LAZY)
                .build();
        
        // 添加关系到实体
        userEntity.addRelationship(userOrdersRel);
        orderEntity.addRelationship(orderUserRel);
        
        // 注册实体元数据到引擎
        engine.getMetadataRegistry().registerEntityMetadata(userEntity);
        engine.getMetadataRegistry().registerEntityMetadata(orderEntity);
        
        System.out.println("实体元数据注册完成：用户实体、订单实体");
    }
    
    /**
     * 定义业务规则
     */
    private static void defineBusinessRules(SmartMetadataEngine engine) {
        System.out.println("\n=== 定义业务规则 ===");
        
        // VIP用户验证规则
        BusinessRuleMetadata vipValidationRule = BusinessRuleMetadata.builder()
                .id("vip-validation-rule")
                .name("VIP用户验证规则")
                .entityApiName("user")
                .ruleType(BusinessRuleMetadata.RuleType.VALIDATION)
                .triggerEvents(Arrays.asList("VALIDATE", "UPDATE"))
                .expression("${isVip} == true || ${balance} < 10000")
                .errorMessage("VIP用户余额必须大于等于10000")
                .priority(100)
                .description("验证VIP用户的余额是否符合要求")
                .build();
        
        // 订单金额限制规则
        BusinessRuleMetadata orderAmountRule = BusinessRuleMetadata.builder()
                .id("order-amount-rule")
                .name("订单金额限制规则")
                .entityApiName("order")
                .ruleType(BusinessRuleMetadata.RuleType.VALIDATION)
                .triggerEvents(Arrays.asList("CREATE", "VALIDATE"))
                .expression("${amount} <= 100000")
                .errorMessage("订单金额不能超过100000")
                .priority(200)
                .description("限制单个订单金额")
                .build();
        
        // 订单创建操作规则
        BusinessRuleMetadata orderCreateRule = BusinessRuleMetadata.builder()
                .id("order-create-rule")
                .name("订单创建规则")
                .entityApiName("order")
                .ruleType(BusinessRuleMetadata.RuleType.ACTION)
                .triggerEvents(Arrays.asList("CREATE"))
                .expression("System.out.println(\"订单创建成功: \" + ${orderId} + \"，金额: \" + ${amount});")
                .priority(100)
                .description("订单创建时执行的操作")
                .build();
        
        // VIP用户折扣规则
        BusinessRuleMetadata vipDiscountRule = BusinessRuleMetadata.builder()
                .id("vip-discount-rule")
                .name("VIP用户折扣规则")
                .entityApiName("order")
                .ruleType(BusinessRuleMetadata.RuleType.ACTION)
                .triggerEvents(Arrays.asList("CREATE", "UPDATE"))
                .expression("if (user != null && user.getField('isVip') == Boolean.TRUE) { entity.setField('discountAmount', amount * 0.2); } else { entity.setField('discountAmount', amount * 0.1); }")
                .priority(150)
                .description("VIP用户享受20%折扣，普通用户10%折扣")
                .build();
        
        // 注册业务规则
        engine.registerBusinessRule(vipValidationRule);
        engine.registerBusinessRule(orderAmountRule);
        engine.registerBusinessRule(orderCreateRule);
        engine.registerBusinessRule(vipDiscountRule);
        
        System.out.println("业务规则注册完成：4条规则");
    }
    
    /**
     * 创建并验证实体示例
     */
    private static void createAndValidateEntity(SmartMetadataEngine engine) {
        System.out.println("\n=== 创建并验证实体示例 ===");
        
        // 创建用户实体
        DynamicSmartEntity user = engine.createEntity("user");
        user.setField("id", "1001");
        user.setField("username", "testuser");
        user.setField("age", 25);
        user.setField("balance", 15000.0);
        
        // 验证实体
        ValidationResult validationResult = engine.validateEntity(user);
        
        System.out.println("用户实体验证结果: " + (validationResult.isValid() ? "通过" : "失败"));
        
        // 计算所有字段（包括计算字段）
        engine.calculateAllFields(user);
        
        System.out.println("计算字段结果 - 是否VIP: " + user.getField("isVip"));
        
        // 尝试创建无效实体
        DynamicSmartEntity invalidUser = engine.createEntity("user");
        invalidUser.setField("id", "1002");
        invalidUser.setField("username", "ab"); // 用户名太短
        invalidUser.setField("age", 16); // 年龄太小
        
        ValidationResult invalidResult = engine.validateEntity(invalidUser);
        
        System.out.println("无效用户验证结果: " + (invalidResult.isValid() ? "通过" : "失败"));
        if (!invalidResult.isValid()) {
            System.out.println("错误信息:");
            for (Map.Entry<String, List<String>> entry : invalidResult.getFieldErrors().entrySet()) {
                System.out.println("  " + entry.getKey() + ": " + String.join(", ", entry.getValue()));
            }
        }
    }
    
    /**
     * 执行业务规则示例
     */
    private static void executeBusinessRules(SmartMetadataEngine engine) {
        System.out.println("\n=== 执行业务规则示例 ===");
        
        // 创建VIP用户
        DynamicSmartEntity vipUser = engine.createEntity("user");
        vipUser.setField("id", "2001");
        vipUser.setField("username", "vipuser");
        vipUser.setField("balance", 20000.0);
        
        // 计算VIP状态
        engine.calculateAllFields(vipUser);
        System.out.println("VIP用户状态: " + vipUser.getField("isVip"));
        
        // 创建订单
        DynamicSmartEntity order = engine.createEntity("order");
        order.setField("orderId", "ORD-001");
        order.setField("userId", "2001");
        order.setField("amount", 5000.0);
        
        // 执行业务规则
        ValidationResult result = engine.applyBusinessRules(order, "CREATE");
        
        System.out.println("订单业务规则执行结果: " + (result.isValid() ? "成功" : "失败"));
        System.out.println("订单折扣金额: " + order.getField("discountAmount"));
        
        // 尝试创建超大金额订单
        DynamicSmartEntity bigOrder = engine.createEntity("order");
        bigOrder.setField("orderId", "ORD-002");
        bigOrder.setField("userId", "2001");
        bigOrder.setField("amount", 200000.0); // 超过限制
        
        ValidationResult bigOrderResult = engine.applyBusinessRules(bigOrder, "CREATE");
        
        System.out.println("超大金额订单执行结果: " + (bigOrderResult.isValid() ? "成功" : "失败"));
        if (!bigOrderResult.isValid()) {
            System.out.println("错误信息: " + bigOrderResult.getErrors());
        }
    }
}