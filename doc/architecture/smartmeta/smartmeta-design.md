基于业界最佳实践，我为您提供 **Bone SmartMeta 智能元数据引擎的详细技术实现方案**，包含完整的代码架构、设计模式和实现细节。

## 🏆 **Bone SmartMeta — 完整技术实现方案**

## 🎯 **1. 核心架构深度设计**

### **1.1 分层架构与包结构**

```java
// 📁 完整包结构设计
com.bone.smartmeta/
├── core/                           // 🧠 核心模块
│   ├── metamodel/                  // 元模型定义
│   │   ├── entity/                 // 实体元数据
│   │   ├── field/                  // 字段元数据  
│   │   ├── relation/               // 关系元数据
│   │   └── rule/                   // 规则元数据
│   ├── registry/                   // 注册中心
│   ├── generator/                  // 代码生成
│   │   ├── template/               // 模板引擎
│   │   ├── strategy/               // 生成策略
│   │   └── output/                 // 输出管理
│   ├── connector/                  // 数据连接
│   │   ├── jdbc/                   // 关系数据库
│   │   ├── nosql/                  // NoSQL
│   │   └── api/                    // API连接
│   └── intelligence/               // 🤖 智能核心
│       ├── ai/                     // AI服务
│       ├── ml/                     // 机器学习
│       └── reasoning/              // 推理引擎
├── sdk/                            // 🛠️ 开发工具包
│   ├── api/                        // 编程接口
│   ├── annotation/                 // 注解驱动
│   ├── dsl/                        // 领域语言
│   └── toolkit/                    // 工具集
├── studio/                         // 🎨 设计平台
│   ├── web/                        // 前端应用
│   ├── server/                     // 后端服务
│   └── plugin/                     // IDE插件
└── starter/                        // ⚡ Spring启动器
    └── autoconfigure/              // 自动配置
```

### **1.2 核心领域模型设计**

```java
// 📁 bone-smartmeta-engine/src/main/java/com/bone/smartmeta/core/metamodel/
package com.bone.smartmeta.core.metamodel;

/**
 * 🏗️ 智能实体元数据 - 聚合根
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartEntity implements AggregateRoot<String> {
    
    @NotBlank
    @Size(max = 100)
    private final String name;
    
    @NotBlank
    @Size(max = 100)
    private String tableName;
    
    @Size(max = 500)
    private String description;
    
    @Builder.Default
    @Valid
    private List<SmartField> fields = new ArrayList<>();
    
    @Builder.Default
    @Valid
    private List<SmartRelation> relations = new ArrayList<>();
    
    @Builder.Default
    @Valid
    private List<BusinessRule> businessRules = new ArrayList<>();
    
    @Builder.Default
    private Map<String, Object> extensions = new HashMap<>();
    
    @Valid
    private AIConfig aiConfig;
    
    @Builder.Default
    private EntityStatus status = EntityStatus.DRAFT;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    /**
     * 🏭 工厂方法 - 创建新实体
     */
    public static SmartEntity create(String name, String tableName, String description) {
        SmartEntity entity = SmartEntity.builder()
            .name(name)
            .tableName(tableName)
            .description(description)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        
        // 发布领域事件
        entity.registerEvent(new EntityCreatedEvent(entity));
        return entity;
    }
    
    /**
     * ➕ 添加字段 - 领域行为
     */
    public SmartEntity addField(SmartField field) {
        // 业务规则验证
        if (fields.stream().anyMatch(f -> f.getName().equals(field.getName()))) {
            throw new BusinessException("字段名称必须唯一: " + field.getName());
        }
        
        this.fields.add(field);
        this.updatedAt = LocalDateTime.now();
        
        // 发布领域事件
        registerEvent(new FieldAddedEvent(this, field));
        return this;
    }
    
    /**
     * 🗑️ 移除字段
     */
    public SmartEntity removeField(String fieldName) {
        boolean removed = fields.removeIf(f -> f.getName().equals(fieldName));
        if (removed) {
            this.updatedAt = LocalDateTime.now();
            registerEvent(new FieldRemovedEvent(this, fieldName));
        }
        return this;
    }
    
    /**
     * 🔄 更新实体
     */
    public SmartEntity update(String tableName, String description) {
        this.tableName = tableName;
        this.description = description;
        this.updatedAt = LocalDateTime.now();
        
        registerEvent(new EntityUpdatedEvent(this));
        return this;
    }
    
    /**
     * ✅ 验证实体完整性
     */
    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();
        
        // 必须有主键字段
        boolean hasPrimaryKey = fields.stream()
            .anyMatch(SmartField::isPrimaryKey);
        if (!hasPrimaryKey) {
            errors.add("实体必须包含至少一个主键字段");
        }
        
        // 字段名称不能重复
        Set<String> fieldNames = fields.stream()
            .map(SmartField::getName)
            .collect(Collectors.toSet());
        if (fieldNames.size() != fields.size()) {
            errors.add("字段名称不能重复");
        }
        
        return ValidationResult.of(errors);
    }
    
    @Override
    public String getId() {
        return this.name;
    }
}

/**
 * 📊 智能字段定义 - 值对象
 */
@Value
@Builder
@AllArgsConstructor
public class SmartField implements ValueObject {
    
    @NotBlank
    String name;
    
    @NotNull
    FieldType type;
    
    @Min(1)
    @Max(4000)
    Integer length;
    
    Integer precision;
    
    Integer scale;
    
    @Builder.Default
    Boolean required = false;
    
    @Builder.Default
    Boolean unique = false;
    
    String defaultValue;
    
    String comment;
    
    @Builder.Default
    Boolean primaryKey = false;
    
    @Builder.Default
    Boolean autoIncrement = false;
    
    @Builder.Default
    List<ValidationRule> validationRules = new ArrayList<>();
    
    UIConfig uiConfig;
    
    AISuggestion aiSuggestion;
    
    /**
     * 🏭 创建主键字段
     */
    public static SmartField createPrimaryKey(String name, FieldType type) {
        return SmartField.builder()
            .name(name)
            .type(type)
            .primaryKey(true)
            .autoIncrement(true)
            .required(true)
            .comment("主键字段")
            .build();
    }
    
    /**
     * 🏭 创建业务字段
     */
    public static SmartField createBusinessField(String name, FieldType type, 
                                               boolean required, String comment) {
        return SmartField.builder()
            .name(name)
            .type(type)
            .required(required)
            .comment(comment)
            .build();
    }
    
    /**
     * 🔍 获取Java类型
     */
    public Class<?> getJavaType() {
        return type.getJavaType();
    }
    
    /**
     * 🔍 获取数据库类型
     */
    public String getDatabaseType(DatabaseType dbType) {
        return type.getDatabaseType(dbType, length, precision, scale);
    }
}

/**
 * 🎯 字段类型枚举
 */
public enum FieldType {
    STRING(String.class),
    INTEGER(Integer.class),
    LONG(Long.class),
    BIG_DECIMAL(BigDecimal.class),
    BOOLEAN(Boolean.class),
    LOCAL_DATE(LocalDate.class),
    LOCAL_DATE_TIME(LocalDateTime.class),
    ENUM(Enum.class),
    JSON(Object.class);
    
    private final Class<?> javaType;
    
    FieldType(Class<?> javaType) {
        this.javaType = javaType;
    }
    
    public Class<?> getJavaType() {
        return javaType;
    }
    
    public String getDatabaseType(DatabaseType dbType, Integer length, 
                                 Integer precision, Integer scale) {
        return dbType.getTypeMapping(this, length, precision, scale);
    }
}
```

### **1.3 元数据注册中心实现**

```java
// 📁 bone-smartmeta-engine/src/main/java/com/bone/smartmeta/core/registry/
package com.bone.smartmeta.core.registry;

/**
 * 🎪 智能元数据注册中心
 */
@Component
@Slf4j
public class SmartMetadataRegistry {
    
    private final Map<String, SmartEntity> entityRegistry = new ConcurrentHashMap<>();
    private final Map<String, List<SmartEntity>> packageRegistry = new ConcurrentHashMap<>();
    private final Map<String, EntityVersion> versionRegistry = new ConcurrentHashMap<>();
    
    private final EventPublisher eventPublisher;
    private final List<RegistryListener> listeners;
    
    /**
     * 📝 注册智能实体
     */
    @Transactional
    public void registerEntity(SmartEntity entity) {
        String entityName = entity.getName();
        
        // 验证实体
        ValidationResult validation = entity.validate();
        if (!validation.isValid()) {
            throw new ValidationException("实体验证失败: " + validation.getErrors());
        }
        
        // 检查版本冲突
        EntityVersion currentVersion = versionRegistry.get(entityName);
        if (currentVersion != null && currentVersion.isLocked()) {
            throw new ConcurrentModificationException("实体正在被修改: " + entityName);
        }
        
        // 执行注册
        entityRegistry.put(entityName, entity);
        updatePackageRegistry(entity);
        
        // 创建新版本
        EntityVersion newVersion = EntityVersion.create(entity);
        versionRegistry.put(entityName, newVersion);
        
        log.info("✅ 成功注册智能实体: {} (版本: {})", entityName, newVersion.getVersion());
        
        // 发布领域事件
        eventPublisher.publishEvent(new EntityRegisteredEvent(entity, newVersion));
        
        // 通知监听器
        listeners.forEach(listener -> listener.onEntityRegistered(entity));
    }
    
    /**
     * 🔄 更新实体
     */
    @Transactional
    public void updateEntity(SmartEntity entity) {
        String entityName = entity.getName();
        
        if (!entityRegistry.containsKey(entityName)) {
            throw new EntityNotFoundException("实体不存在: " + entityName);
        }
        
        SmartEntity oldEntity = entityRegistry.get(entityName);
        entityRegistry.put(entityName, entity);
        updatePackageRegistry(entity);
        
        // 创建新版本
        EntityVersion newVersion = versionRegistry.get(entityName).increment();
        versionRegistry.put(entityName, newVersion);
        
        log.info("🔄 更新实体: {} -> 版本: {}", entityName, newVersion.getVersion());
        
        eventPublisher.publishEvent(new EntityUpdatedEvent(oldEntity, entity, newVersion));
        listeners.forEach(listener -> listener.onEntityUpdated(oldEntity, entity));
    }
    
    /**
     * 🔍 获取实体定义
     */
    public Optional<SmartEntity> getEntity(String entityName) {
        return Optional.ofNullable(entityRegistry.get(entityName));
    }
    
    /**
     * 🔍 获取实体（带版本）
     */
    public Optional<EntityWithVersion> getEntityWithVersion(String entityName) {
        return Optional.ofNullable(entityRegistry.get(entityName))
            .map(entity -> {
                EntityVersion version = versionRegistry.get(entityName);
                return new EntityWithVersion(entity, version);
            });
    }
    
    /**
     * 📋 按包名获取实体
     */
    public List<SmartEntity> getEntitiesByPackage(String packageName) {
        return packageRegistry.getOrDefault(packageName, Collections.emptyList());
    }
    
    /**
     * 🔎 搜索实体
     */
    public List<SmartEntity> searchEntities(EntitySearchCriteria criteria) {
        return entityRegistry.values().stream()
            .filter(criteria::matches)
            .sorted(Comparator.comparing(SmartEntity::getName))
            .collect(Collectors.toList());
    }
    
    /**
     * 📊 获取注册统计
     */
    public RegistryStats getStats() {
        return RegistryStats.builder()
            .totalEntities(entityRegistry.size())
            .totalFields(entityRegistry.values().stream()
                .mapToInt(e -> e.getFields().size())
                .sum())
            .totalRelations(entityRegistry.values().stream()
                .mapToInt(e -> e.getRelations().size())
                .sum())
            .packages(packageRegistry.size())
            .build();
    }
    
    private void updatePackageRegistry(SmartEntity entity) {
        // 根据实体名称推导包名
        String packageName = derivePackageName(entity.getName());
        packageRegistry.computeIfAbsent(packageName, k -> new ArrayList<>())
            .add(entity);
    }
    
    private String derivePackageName(String entityName) {
        // 简单的包名推导逻辑，实际中可以更复杂
        return "com.example." + entityName.toLowerCase();
    }
}

/**
 * 📊 注册统计
 */
@Value
@Builder
public class RegistryStats {
    int totalEntities;
    int totalFields;
    int totalRelations;
    int packages;
    
    public double getAverageFieldsPerEntity() {
        return totalEntities > 0 ? (double) totalFields / totalEntities : 0;
    }
}

/**
 * 🔐 实体版本管理
 */
@Value
public class EntityVersion {
    String entityName;
    long version;
    String checksum;
    LocalDateTime createdAt;
    boolean locked;
    
    public static EntityVersion create(SmartEntity entity) {
        String checksum = calculateChecksum(entity);
        return new EntityVersion(
            entity.getName(),
            1L,
            checksum,
            LocalDateTime.now(),
            false
        );
    }
    
    public EntityVersion increment() {
        return new EntityVersion(
            entityName,
            version + 1,
            calculateChecksum(entityRegistry.get(entityName)),
            LocalDateTime.now(),
            locked
        );
    }
    
    private static String calculateChecksum(SmartEntity entity) {
        // 简化的checksum计算
        return entity.getName() + ":" + entity.getFields().size();
    }
}
```

## 🚀 **2. 智能代码生成引擎**

### **2.1 生成策略模式实现**

```java
// 📁 bone-smartmeta-engine/src/main/java/com/bone/smartmeta/core/generator/
package com.bone.smartmeta.core.generator;

/**
 * 🎯 代码生成策略接口
 */
public interface CodeGenerationStrategy {
    
    /**
     * 是否支持生成目标
     */
    boolean supports(GenerationTarget target);
    
    /**
     * 生成代码
     */
    GenerationResult generate(GenerationContext context);
    
    /**
     * 获取生成配置
     */
    GenerationConfig getConfig();
    
    /**
     * 验证生成上下文
     */
    ValidationResult validate(GenerationContext context);
}

/**
 * 🖥️ 后端Java生成策略
 */
@Component
@Slf4j
public class BackendJavaStrategy implements CodeGenerationStrategy {
    
    private final TemplateEngine templateEngine;
    private final AIService aiService;
    
    @Override
    public boolean supports(GenerationTarget target) {
        return target == GenerationTarget.JAVA_ENTITY || 
               target == GenerationTarget.JAVA_SERVICE ||
               target == GenerationTarget.JAVA_CONTROLLER;
    }
    
    @Override
    public GenerationResult generate(GenerationContext context) {
        try {
            // 1. 🧠 AI增强分析
            AIAnalysisResult analysis = aiService.analyzeForGeneration(context);
            
            // 2. 🎨 选择最优模板
            TemplateConfig template = selectOptimalTemplate(context, analysis);
            
            // 3. ⚡ 执行生成
            String generatedCode = templateEngine.process(template, enhanceContext(context, analysis));
            
            // 4. 🔧 AI代码优化
            if (context.getConfig().isAiOptimization()) {
                generatedCode = aiService.optimizeCode(generatedCode, context);
            }
            
            // 5. ✅ 质量检查
            QualityCheckResult quality = performQualityCheck(generatedCode, context);
            
            return GenerationResult.success(generatedCode, template, quality, analysis.getSuggestions());
            
        } catch (Exception e) {
            log.error("Java代码生成失败: {}", context.getEntity().getName(), e);
            return GenerationResult.failure(e.getMessage());
        }
    }
    
    @Override
    public GenerationConfig getConfig() {
        return GenerationConfig.builder()
            .target(GenerationTarget.JAVA_ENTITY)
            .templateName("java-entity")
            .fileExtension(".java")
            .outputPath("src/main/java/{packagePath}/{entityName}.java")
            .aiEnhancement(true)
            .build();
    }
    
    @Override
    public ValidationResult validate(GenerationContext context) {
        List<String> errors = new ArrayList<>();
        
        SmartEntity entity = context.getEntity();
        if (entity == null) {
            errors.add("实体不能为空");
        }
        
        if (context.getPackageName() == null) {
            errors.add("包名不能为空");
        }
        
        // 检查Java命名规范
        if (!entity.getName().matches("^[A-Z][a-zA-Z0-9]*$")) {
            errors.add("实体名称必须符合Java类名规范");
        }
        
        return ValidationResult.of(errors);
    }
    
    private TemplateConfig selectOptimalTemplate(GenerationContext context, AIAnalysisResult analysis) {
        List<TemplateConfig> candidates = templateEngine.findTemplates(
            context.getTarget(), 
            context.getConfig().getCodeStyle()
        );
        
        return aiService.recommendTemplate(candidates, context, analysis);
    }
    
    private GenerationContext enhanceContext(GenerationContext context, AIAnalysisResult analysis) {
        return context.toBuilder()
            .variables(buildTemplateVariables(context, analysis))
            .aiSuggestions(analysis.getSuggestions())
            .optimizations(analysis.getOptimizations())
            .build();
    }
    
    private Map<String, Object> buildTemplateVariables(GenerationContext context, AIAnalysisResult analysis) {
        Map<String, Object> variables = new HashMap<>();
        SmartEntity entity = context.getEntity();
        
        variables.put("entity", entity);
        variables.put("packageName", context.getPackageName());
        variables.put("imports", resolveImports(entity));
        variables.put("annotations", resolveAnnotations(entity, analysis));
        variables.put("fields", entity.getFields());
        variables.put("relations", entity.getRelations());
        variables.put("config", context.getConfig());
        variables.put("aiSuggestions", analysis.getSuggestions());
        
        return variables;
    }
    
    private Set<String> resolveImports(SmartEntity entity) {
        Set<String> imports = new TreeSet<>();
        
        // 基础导入
        imports.add("import lombok.Data;");
        imports.add("import lombok.EqualsAndHashCode;");
        imports.add("import javax.persistence.*;");
        imports.add("import java.time.*;");
        
        // 根据字段类型添加导入
        for (SmartField field : entity.getFields()) {
            Class<?> fieldType = field.getType().getJavaType();
            if (fieldType.getPackage() != null && 
                !fieldType.getPackage().getName().startsWith("java.lang")) {
                imports.add("import " + fieldType.getName() + ";");
            }
        }
        
        return imports;
    }
}

/**
 * 🎨 模板引擎实现
 */
@Component
@Slf4j
public class SmartTemplateEngine {
    
    private final Map<String, TemplateProcessor> processors;
    private final TemplateRepository templateRepository;
    private final AIService aiService;
    
    /**
     * ⚡ 处理模板
     */
    public String process(TemplateConfig config, GenerationContext context) {
        TemplateProcessor processor = processors.get(config.getType());
        if (processor == null) {
            throw new TemplateException("不支持的模板类型: " + config.getType());
        }
        
        try {
            // 1. 🧠 AI增强的上下文处理
            GenerationContext enhancedContext = enhanceContextWithAI(context);
            
            // 2. 🎯 执行模板处理
            String result = processor.process(config, enhancedContext);
            
            // 3. ✨ AI后处理优化
            if (config.isAiPostProcess()) {
                result = aiService.postProcessTemplate(result, config, enhancedContext);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("模板处理失败: {}", config.getName(), e);
            throw new TemplateProcessingException("模板处理失败: " + config.getName(), e);
        }
    }
    
    /**
     * 🔍 查找候选模板
     */
    public List<TemplateConfig> findCandidateTemplates(GenerationTarget target, CodeStyle style) {
        return templateRepository.findByTargetAndStyle(target, style);
    }
    
    /**
     * 📝 注册模板处理器
     */
    public void registerProcessor(String type, TemplateProcessor processor) {
        processors.put(type, processor);
    }
    
    private GenerationContext enhanceContextWithAI(GenerationContext context) {
        // AI增强逻辑
        AIContextEnhancement enhancement = aiService.enhanceGenerationContext(context);
        return context.toBuilder()
            .variables(enhancement.getEnhancedVariables())
            .aiSuggestions(enhancement.getSuggestions())
            .optimizations(enhancement.getOptimizations())
            .build();
    }
}

/**
 * 📄 Java实体模板处理器
 */
@Component
@Slf4j
public class JavaEntityTemplateProcessor implements TemplateProcessor {
    
    @Override
    public String process(TemplateConfig config, GenerationContext context) {
        SmartEntity entity = context.getEntity();
        Map<String, Object> variables = context.getVariables();
        
        try {
            // 使用模板引擎处理（这里简化为字符串格式化）
            return """
                package %s;
                
                %s
                
                /**
                 * %s
                 * 
                 * @author SmartMeta Engine
                 * @generated %s
                 */
                %s
                @Data
                @Entity
                @Table(name = "%s")
                @EqualsAndHashCode(callSuper = %s)
                public class %s %s {
                    %s
                }
                """.formatted(
                variables.get("packageName"),
                String.join("\n", (Set<String>) variables.get("imports")),
                entity.getDescription(),
                LocalDateTime.now(),
                generateClassAnnotations(entity, context),
                entity.getTableName(),
                context.hasBaseEntity(),
                entity.getName(),
                context.hasBaseEntity() ? "extends BaseEntity" : "",
                generateFieldsCode(entity, context)
            );
        } catch (Exception e) {
            throw new TemplateProcessingException("Java实体模板处理失败", e);
        }
    }
    
    private String generateClassAnnotations(SmartEntity entity, GenerationContext context) {
        List<String> annotations = new ArrayList<>();
        
        // 添加AI建议的注解
        if (context.getAiSuggestions() != null) {
            context.getAiSuggestions().stream()
                .filter(suggestion -> suggestion.getType() == SuggestionType.CLASS_ANNOTATION)
                .map(Suggestion::getContent)
                .forEach(annotations::add);
        }
        
        return String.join("\n", annotations);
    }
    
    private String generateFieldsCode(SmartEntity entity, GenerationContext context) {
        return entity.getFields().stream()
            .map(field -> generateFieldCode(field, context))
            .collect(Collectors.joining("\n    "));
    }
    
    private String generateFieldCode(SmartField field, GenerationContext context) {
        StringBuilder fieldCode = new StringBuilder();
        
        // 字段注释
        if (StringUtils.isNotBlank(field.getComment())) {
            fieldCode.append("""
                /**
                 * %s
                 */
                """.formatted(field.getComment()));
        }
        
        // 字段注解
        fieldCode.append(generateFieldAnnotations(field, context));
        
        // 字段定义
        fieldCode.append("private %s %s;".formatted(
            field.getType().getJavaType().getSimpleName(),
            field.getName()
        ));
        
        return fieldCode.toString().indent(4).stripTrailing();
    }
    
    private String generateFieldAnnotations(SmartField field, GenerationContext context) {
        List<String> annotations = new ArrayList<>();
        
        // JPA注解
        annotations.add("@Column(name = \"%s\")".formatted(field.getName()));
        
        if (field.isPrimaryKey()) {
            annotations.add("@Id");
            if (field.isAutoIncrement()) {
                annotations.add("@GeneratedValue(strategy = GenerationType.IDENTITY)");
            }
        }
        
        if (!field.isRequired()) {
            annotations.add("@Nullable");
        }
        
        // 验证注解
        field.getValidationRules().forEach(rule -> {
            annotations.add("@" + rule.getAnnotation());
        });
        
        // AI建议的注解
        if (context.getAiSuggestions() != null) {
            context.getAiSuggestions().stream()
                .filter(suggestion -> suggestion.getFieldName() != null &&
                    suggestion.getFieldName().equals(field.getName()))
                .map(Suggestion::getContent)
                .forEach(annotations::add);
        }
        
        return annotations.stream()
            .collect(Collectors.joining("\n    ", "", "\n    "));
    }
    
    @Override
    public boolean supports(String templateType) {
        return "java-entity".equals(templateType);
    }
}
```

### **2.2 生成上下文与配置**

```java
// 📁 bone-smartmeta-engine/src/main/java/com/bone/smartmeta/core/generator/model/
package com.bone.smartmeta.core.generator.model;

/**
 * 🎯 生成上下文
 */
@Value
@Builder(toBuilder = true)
public class GenerationContext {
    
    @NotNull
    SmartEntity entity;
    
    @NotBlank
    String packageName;
    
    @NotNull
    GenerationTarget target;
    
    @NotNull
    GenerationConfig config;
    
    @Builder.Default
    Map<String, Object> variables = new HashMap<>();
    
    @Builder.Default
    List<Suggestion> aiSuggestions = new ArrayList<>();
    
    @Builder.Default
    List<Optimization> optimizations = new ArrayList<>();
    
    DatabaseConfig databaseConfig;
    
    ProjectConfig projectConfig;
    
    /**
     * 🔧 构建默认上下文
     */
    public static GenerationContext defaultContext(SmartEntity entity, String packageName) {
        return GenerationContext.builder()
            .entity(entity)
            .packageName(packageName)
            .target(GenerationTarget.JAVA_ENTITY)
            .config(GenerationConfig.defaultConfig())
            .build();
    }
    
    public boolean hasBaseEntity() {
        return variables.containsKey("baseEntity") && 
               Boolean.TRUE.equals(variables.get("baseEntity"));
    }
}

/**
 * ⚙️ 生成配置
 */
@Value
@Builder
public class GenerationConfig {
    
    @NotNull
    GenerationTarget target;
    
    @NotBlank
    String templateName;
    
    @NotBlank
    String fileExtension;
    
    @NotBlank
    String outputPath;
    
    @Builder.Default
    CodeStyle codeStyle = CodeStyle.MODERN;
    
    @Builder.Default
    boolean aiEnhancement = true;
    
    @Builder.Default
    boolean aiOptimization = true;
    
    @Builder.Default
    boolean overwriteExisting = false;
    
    @Builder.Default
    boolean formatCode = true;
    
    @Builder.Default
    boolean validateCode = true;
    
    @Builder.Default
    List<String> additionalTemplates = new ArrayList<>();
    
    /**
     * 🏭 创建默认配置
     */
    public static GenerationConfig defaultConfig() {
        return GenerationConfig.builder()
            .target(GenerationTarget.JAVA_ENTITY)
            .templateName("java-entity-modern")
            .fileExtension(".java")
            .outputPath("src/main/java/{packagePath}/{entityName}.java")
            .build();
    }
    
    /**
     * 🎯 创建全栈配置
     */
    public static GenerationConfig fullStackConfig() {
        return defaultConfig().toBuilder()
            .additionalTemplates(List.of(
                "java-service",
                "java-controller", 
                "vue3-component",
                "sql-ddl"
            ))
            .build();
    }
}

/**
 * 🎯 生成目标枚举
 */
public enum GenerationTarget {
    JAVA_ENTITY("Java Entity", "后端实体"),
    JAVA_SERVICE("Java Service", "业务服务"),
    JAVA_CONTROLLER("Java Controller", "控制器"),
    VUE3_COMPONENT("Vue3 Component", "Vue组件"),
    REACT_COMPONENT("React Component", "React组件"),
    SQL_DDL("SQL DDL", "数据库脚本"),
    TYPESCRIPT_INTERFACE("TypeScript Interface", "TS接口"),
    API_DOCUMENT("API Document", "API文档");
    
    private final String displayName;
    private final String description;
    
    GenerationTarget(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}

/**
 * 🎨 代码风格枚举
 */
public enum CodeStyle {
    MODERN("现代风格", "使用现代Java特性和最佳实践"),
    ENTERPRISE("企业风格", "符合企业级开发规范"),
    MINIMAL("极简风格", "最简化的代码结构"),
    LEGACY("传统风格", "兼容老版本Java");
    
    private final String displayName;
    private final String description;
    
    CodeStyle(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
```

## 🛠️ **3. SDK 开发工具包深度实现**

### **3.1 智能注解处理器**

```java
// 📁 bone-smartmeta-sdk/src/main/java/com/bone/smartmeta/annotation/processor/
package com.bone.smartmeta.annotation.processor;

/**
 * 🔍 智能实体注解处理器
 */
@SupportedAnnotationTypes("com.bone.smartmeta.annotation.SmartEntity")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@AutoService(Processor.class)
public class SmartEntityAnnotationProcessor extends AbstractProcessor {
    
    private Elements elementUtils;
    private Types typeUtils;
    private Filer filer;
    private Messager messager;
    
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();
        this.filer = processingEnv.getFiler();
        this.messager = processingEnv.getMessager();
    }
    
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
            
            for (Element element : annotatedElements) {
                if (element.getKind() == ElementKind.CLASS) {
                    processSmartEntity((TypeElement) element);
                }
            }
        }
        return true;
    }
    
    private void processSmartEntity(TypeElement element) {
        try {
            // 解析注解信息
            SmartEntity annotation = element.getAnnotation(SmartEntity.class);
            String entityName = annotation.name().isEmpty() ? 
                element.getSimpleName().toString() : annotation.name();
            String tableName = annotation.tableName().isEmpty() ?
                "t_" + camelToSnake(entityName) : annotation.tableName();
            
            // 构建实体元数据
            SmartEntityMetadata metadata = buildEntityMetadata(element, entityName, tableName, annotation);
            
            // 生成元数据文件
            generateMetadataFile(metadata, element);
            
            // 生成辅助类
            generateHelperClasses(metadata, element);
            
        } catch (Exception e) {
            error("处理实体注解失败: " + element.getSimpleName(), element);
        }
    }
    
    private SmartEntityMetadata buildEntityMetadata(TypeElement element, String entityName, 
                                                   String tableName, SmartEntity annotation) {
        SmartEntityMetadata metadata = new SmartEntityMetadata();
        metadata.setName(entityName);
        metadata.setTableName(tableName);
        metadata.setDescription(annotation.description());
        metadata.setAiEnhanced(annotation.aiEnhanced());
        metadata.setDomain(annotation.domain());
        
        // 处理字段
        processFields(element, metadata);
        
        // 处理关系
        processRelations(element, metadata);
        
        return metadata;
    }
    
    private void processFields(TypeElement element, SmartEntityMetadata metadata) {
        for (Element enclosedElement : element.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.FIELD) {
                VariableElement fieldElement = (VariableElement) enclosedElement;
                SmartField fieldAnnotation = fieldElement.getAnnotation(SmartField.class);
                
                if (fieldAnnotation != null) {
                    FieldMetadata fieldMetadata = buildFieldMetadata(fieldElement, fieldAnnotation);
                    metadata.addField(fieldMetadata);
                }
            }
        }
    }
    
    private FieldMetadata buildFieldMetadata(VariableElement fieldElement, SmartField annotation) {
        return FieldMetadata.builder()
            .name(annotation.name().isEmpty() ? 
                fieldElement.getSimpleName().toString() : annotation.name())
            .type(resolveFieldType(fieldElement))
            .length(annotation.length())
            .required(annotation.required())
            .unique(annotation.unique())
            .defaultValue(annotation.defaultValue())
            .comment(annotation.comment())
            .validationRule(annotation.validationRule())
            .build();
    }
    
    private void generateMetadataFile(SmartEntityMetadata metadata, TypeElement element) throws IOException {
        String packageName = elementUtils.getPackageOf(element).getQualifiedName().toString();
        String className = element.getSimpleName().toString() + "Metadata";
        
        JavaFileObject jfo = filer.createSourceFile(packageName + "." + className, element);
        try (PrintWriter out = new PrintWriter(jfo.openWriter())) {
            out.println("package " + packageName + ";");
            out.println();
            out.println("/**");
            out.println(" * 自动生成的元数据类 - 请勿手动修改");
            out.println(" */");
            out.println("public class " + className + " {");
            out.println("    public static final String ENTITY_NAME = \"" + metadata.getName() + "\";");
            out.println("    public static final String TABLE_NAME = \"" + metadata.getTableName() + "\";");
            out.println("}");
        }
    }
    
    private void generateHelperClasses(SmartEntityMetadata metadata, TypeElement element) {
        // 生成查询助手类、DTO类等
        generateQueryHelper(metadata, element);
        generateDtoClass(metadata, element);
    }
    
    private void error(String msg, Element element) {
        messager.printMessage(Diagnostic.Kind.ERROR, msg, element);
    }
    
    private String camelToSnake(String str) {
        return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
```

### **3.2 流式查询DSL完整实现**

```java
// 📁 bone-smartmeta-sdk/src/main/java/com/bone/smartmeta/dsl/
package com.bone.smartmeta.dsl;

/**
 * 🔤 智能查询构建器
 */
public class SmartQuery<T> {
    
    private final Class<T> entityClass;
    private final List<Criterion> criteria = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private final List<String> fetchJoins = new ArrayList<>();
    private PageRequest pageRequest;
    private boolean distinct;
    private LockModeType lockMode;
    
    private final AIService aiService;
    private final QueryOptimizer queryOptimizer;
    
    private SmartQuery(Class<T> entityClass) {
        this.entityClass = entityClass;
        this.aiService = BeanUtils.getBean(AIService.class);
        this.queryOptimizer = BeanUtils.getBean(QueryOptimizer.class);
    }
    
    /**
     * 🎯 创建查询
     */
    public static <T> SmartQuery<T> of(Class<T> entityClass) {
        return new SmartQuery<>(entityClass);
    }
    
    /**
     * ⚡ 等值查询
     */
    public <V> SmartQuery<T> eq(SFunction<T, V> field, V value) {
        criteria.add(Criterion.eq(FieldResolver.resolve(field), value));
        return this;
    }
    
    /**
     * 🔍 不等查询
     */
    public <V> SmartQuery<T> ne(SFunction<T, V> field, V value) {
        criteria.add(Criterion.ne(FieldResolver.resolve(field), value));
        return this;
    }
    
    /**
     * 🔍 模糊查询
     */
    public SmartQuery<T> like(SFunction<T, String> field, String value) {
        criteria.add(Criterion.like(FieldResolver.resolve(field), value));
        return this;
    }
    
    /**
     * 📈 大于等于
     */
    public <V extends Comparable<V>> SmartQuery<T> gte(SFunction<T, V> field, V value) {
        criteria.add(Criterion.gte(FieldResolver.resolve(field), value));
        return this;
    }
    
    /**
     * 📉 小于等于
     */
    public <V extends Comparable<V>> SmartQuery<T> lte(SFunction<T, V> field, V value) {
        criteria.add(Criterion.lte(FieldResolver.resolve(field), value));
        return this;
    }
    
    /**
     * 📊 范围查询
     */
    public <V extends Comparable<V>> SmartQuery<T> between(
            SFunction<T, V> field, V start, V end) {
        criteria.add(Criterion.between(FieldResolver.resolve(field), start, end));
        return this;
    }
    
    /**
     * 📋 IN 查询
     */
    public <V> SmartQuery<T> in(SFunction<T, V> field, Collection<V> values) {
        criteria.add(Criterion.in(FieldResolver.resolve(field), values));
        return this;
    }
    
    /**
     * 🔄 排序
     */
    public SmartQuery<T> orderBy(SFunction<T, ?> field, Sort.Direction direction) {
        orders.add(Order.by(FieldResolver.resolve(field), direction));
        return this;
    }
    
    /**
     * 🔄 智能排序
     */
    public SmartQuery<T> orderByIntelligently(SFunction<T, ?> field) {
        // AI推荐的排序方式
        OrderSuggestion suggestion = aiService.suggestOrder(field);
        return orderBy(field, suggestion.getDirection());
    }
    
    /**
     * 📄 分页
     */
    public SmartQuery<T> page(int page, int size) {
        this.pageRequest = PageRequest.of(page, size);
        return this;
    }
    
    /**
     * 📄 智能分页
     */
    public SmartQuery<T> pageIntelligently(int page) {
        PageSuggestion suggestion = aiService.suggestPageSize(entityClass, page);
        this.pageRequest = PageRequest.of(page, suggestion.getPageSize());
        return this;
    }
    
    /**
     * 🔗 关联查询
     */
    public SmartQuery<T> fetch(SFunction<T, ?> association) {
        fetchJoins.add(FieldResolver.resolve(association));
        return this;
    }
    
    /**
     * 🧠 AI优化查询
     */
    public SmartQuery<T> withAIOptimization() {
        QueryOptimization optimization = aiService.optimizeQuery(criteria, entityClass);
        this.criteria.clear();
        this.criteria.addAll(optimization.getOptimizedCriteria());
        
        // 应用AI建议
        optimization.getSuggestions().forEach(suggestion -> {
            if (suggestion.getType() == SuggestionType.INDEX_HINT) {
                this.distinct = true;
            }
        });
        
        return this;
    }
    
    /**
     * 🎯 执行查询
     */
    public List<T> execute() {
        QueryContext context = buildQueryContext();
        TypedQuery<T> query = createJPAQuery(context);
        return query.getResultList();
    }
    
    /**
     * 📄 执行分页查询
     */
    public PageResult<T> page() {
        if (pageRequest == null) {
            throw new IllegalStateException("分页查询需要先设置分页参数");
        }
        
        QueryContext context = buildQueryContext();
        
        // 查询数据
        TypedQuery<T> query = createJPAQuery(context);
        query.setFirstResult((int) pageRequest.getOffset());
        query.setMaxResults(pageRequest.getPageSize());
        List<T> content = query.getResultList();
        
        // 查询总数
        Long total = executeCountQuery(context);
        
        return new PageResult<>(content, total, pageRequest);
    }
    
    /**
     * 🔢 执行计数查询
     */
    public long count() {
        QueryContext context = buildQueryContext();
        return executeCountQuery(context);
    }
    
    /**
     * ✅ 检查存在性
     */
    public boolean exists() {
        return count() > 0;
    }
    
    /**
     * 🎯 获取单个结果
     */
    public Optional<T> singleResult() {
        QueryContext context = buildQueryContext();
        TypedQuery<T> query = createJPAQuery(context);
        try {
            return Optional.ofNullable(query.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
    
    private QueryContext buildQueryContext() {
        return QueryContext.builder()
            .entityClass(entityClass)
            .criteria(criteria)
            .orders(orders)
            .fetchJoins(fetchJoins)
            .pageRequest(pageRequest)
            .distinct(distinct)
            .lockMode(lockMode)
            .build();
    }
    
    private TypedQuery<T> createJPAQuery(QueryContext context) {
        EntityManager em = getEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        
        CriteriaQuery<T> cq = cb.createQuery(entityClass);
        Root<T> root = cq.from(entityClass);
        
        // 应用查询条件
        Predicate predicate = buildPredicate(cb, root, context.getCriteria());
        if (predicate != null) {
            cq.where(predicate);
        }
        
        // 应用排序
        if (!context.getOrders().isEmpty()) {
            List<javax.persistence.criteria.Order> jpaOrders = context.getOrders().stream()
                .map(order -> order.toJpaOrder(cb, root))
                .collect(Collectors.toList());
            cq.orderBy(jpaOrders);
        }
        
        // 应用关联查询
        context.getFetchJoins().forEach(joinPath -> {
            root.fetch(joinPath, JoinType.LEFT);
        });
        
        TypedQuery<T> query = em.createQuery(cq);
        
        // 应用锁模式
        if (context.getLockMode() != null) {
            query.setLockMode(context.getLockMode());
        }
        
        return query;
    }
    
    private Long executeCountQuery(QueryContext context) {
        EntityManager em = getEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<T> root = cq.from(entityClass);
        
        cq.select(cb.count(root));
        
        Predicate predicate = buildPredicate(cb, root, context.getCriteria());
        if (predicate != null) {
            cq.where(predicate);
        }
        
        return em.createQuery(cq).getSingleResult();
    }
    
    private Predicate buildPredicate(CriteriaBuilder cb, Root<T> root, List<Criterion> criteria) {
        if (criteria.isEmpty()) {
            return null;
        }
        
        List<Predicate> predicates = criteria.stream()
            .map(criterion -> criterion.toPredicate(cb, root))
            .collect(Collectors.toList());
        
        return cb.and(predicates.toArray(new Predicate[0]));
    }
    
    private EntityManager getEntityManager() {
        return BeanUtils.getBean(EntityManager.class);
    }
}

/**
 * 📊 分页结果
 */
@Value
@Builder
public class PageResult<T> {
    
    List<T> content;
    long total;
    PageRequest pageRequest;
    
    public int getTotalPages() {
        return pageRequest.getPageSize() == 0 ? 1 : 
            (int) Math.ceil((double) total / (double) pageRequest.getPageSize());
    }
    
    public boolean hasNext() {
        return pageRequest.getPageNumber() + 1 < getTotalPages();
    }
    
    public boolean hasPrevious() {
        return pageRequest.getPageNumber() > 0;
    }
    
    public PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(
            content.stream().map(mapper).collect(Collectors.toList()),
            total,
            pageRequest
        );
    }
}
```

## 🎨 **4. Studio 设计器前端深度实现**

### **4.1 Vue 3 组合式API实现**

```typescript
// 📁 bone-smartmeta-studio/web/src/composables/useEntityManager.ts
import { ref, reactive, computed } from 'vue'
import type { SmartEntity, SmartField, SmartRelation, GenerationResult } from '../types/smartmeta'
import { useAIAssistant } from './useAIAssistant'
import { useValidation } from './useValidation'

/**
 * 🎯 实体管理器
 */
export function useEntityManager() {
  const entities = ref<SmartEntity[]>([])
  const currentEntity = ref<SmartEntity>()
  const isGenerating = ref(false)
  
  const { aiSuggestions, getAISuggestions, optimizeWithAI } = useAIAssistant()
  const { validateEntity, validationErrors } = useValidation()
  
  // 📊 计算属性
  const entityStats = computed(() => {
    const totalFields = entities.value.reduce((sum, entity) => sum + entity.fields.length, 0)
    const totalRelations = entities.value.reduce((sum, entity) => sum + entity.relations.length, 0)
    
    return {
      totalEntities: entities.value.length,
      totalFields,
      totalRelations,
      averageFields: entities.value.length > 0 ? totalFields / entities.value.length : 0
    }
  })
  
  const hasChanges = computed(() => {
    return entities.value.some(entity => entity.status === 'MODIFIED')
  })
  
  // 🎯 实体操作
  const createEntity = async (entityData: Partial<SmartEntity>): Promise<SmartEntity> => {
    const newEntity: SmartEntity = {
      name: entityData.name || '',
      tableName: entityData.tableName || `t_${entityData.name?.toLowerCase() || 'entity'}`,
      description: entityData.description || '',
      fields: [],
      relations: [],
      businessRules: [],
      extensions: {},
      aiConfig: { enabled: true },
      status: 'DRAFT',
      createdAt: new Date(),
      updatedAt: new Date()
    }
    
    // 验证实体
    const validation = validateEntity(newEntity)
    if (!validation.isValid) {
      throw new Error(`实体验证失败: ${validation.errors.join(', ')}`)
    }
    
    entities.value.push(newEntity)
    currentEntity.value = newEntity
    
    // 获取AI建议
    await getAISuggestions(newEntity)
    
    return newEntity
  }
  
  const updateEntity = async (entityName: string, updates: Partial<SmartEntity>): Promise<SmartEntity> => {
    const entity = entities.value.find(e => e.name === entityName)
    if (!entity) {
      throw new Error(`实体不存在: ${entityName}`)
    }
    
    Object.assign(entity, updates, { updatedAt: new Date(), status: 'MODIFIED' })
    
    // 重新验证
    validateEntity(entity)
    
    return entity
  }
  
  const deleteEntity = async (entityName: string): Promise<void> => {
    const index = entities.value.findIndex(e => e.name === entityName)
    if (index === -1) {
      throw new Error(`实体不存在: ${entityName}`)
    }
    
    entities.value.splice(index, 1)
    
    if (currentEntity.value?.name === entityName) {
      currentEntity.value = undefined
    }
  }
  
  // 📊 字段操作
  const addField = async (entityName: string, field: SmartField): Promise<void> => {
    const entity = entities.value.find(e => e.name === entityName)
    if (!entity) {
      throw new Error(`实体不存在: ${entityName}`)
    }
    
    // 检查字段名称唯一性
    if (entity.fields.some(f => f.name === field.name)) {
      throw new Error(`字段名称必须唯一: ${field.name}`)
    }
    
    entity.fields.push({
      ...field,
      createdAt: new Date()
    })
    
    entity.updatedAt = new Date()
    entity.status = 'MODIFIED'
    
    // AI优化字段配置
    await optimizeWithAI(entity, 'FIELD_ADDED')
  }
  
  const updateField = async (entityName: string, fieldName: string, updates: Partial<SmartField>): Promise<void> => {
    const entity = entities.value.find(e => e.name === entityName)
    if (!entity) {
      throw new Error(`实体不存在: ${entityName}`)
    }
    
    const field = entity.fields.find(f => f.name === fieldName)
    if (!field) {
      throw new Error(`字段不存在: ${fieldName}`)
    }
    
    Object.assign(field, updates, { updatedAt: new Date() })
    entity.updatedAt = new Date()
    entity.status = 'MODIFIED'
  }
  
  const removeField = async (entityName: string, fieldName: string): Promise<void> => {
    const entity = entities.value.find(e => e.name === entityName)
    if (!entity) {
      throw new Error(`实体不存在: ${entityName}`)
    }
    
    const fieldIndex = entity.fields.findIndex(f => f.name === fieldName)
    if (fieldIndex === -1) {
      throw new Error(`字段不存在: ${fieldName}`)
    }
    
    entity.fields.splice(fieldIndex, 1)
    entity.updatedAt = new Date()
    entity.status = 'MODIFIED'
  }
  
  // 🔗 关系操作
  const addRelation = async (entityName: string, relation: SmartRelation): Promise<void> => {
    const entity = entities.value.find(e => e.name === entityName)
    if (!entity) {
      throw new Error(`实体不存在: ${entityName}`)
    }
    
    entity.relations.push(relation)
    entity.updatedAt = new Date()
    entity.status = 'MODIFIED'
  }
  
  // 🚀 代码生成
  const generateCode = async (targets: string[], options: GenerationOptions = {}): Promise<GenerationResult> => {
    isGenerating.value = true
    
    try {
      const request: GenerationRequest = {
        entities: entities.value.filter(e => e.status !== 'DRAFT'),
        targets,
        options: {
          aiEnhancement: options.aiEnhancement ?? true,
          codeStyle: options.codeStyle ?? 'MODERN',
          outputDir: options.outputDir ?? './generated',
          overwriteExisting: options.overwriteExisting ?? false,
          ...options
        }
      }
      
      const response = await smartMetaApi.generateCode(request)
      
      // 更新实体状态
      entities.value.forEach(entity => {
        if (entity.status === 'MODIFIED') {
          entity.status = 'SYNCED'
        }
      })
      
      return response.data
    } catch (error) {
      console.error('代码生成失败:', error)
      throw error
    } finally {
      isGenerating.value = false
    }
  }
  
  // 💾 持久化操作
  const saveProject = async (projectName: string): Promise<void> => {
    const project = {
      name: projectName,
      entities: entities.value,
      version: '1.0.0',
      createdAt: new Date(),
      updatedAt: new Date()
    }
    
    await smartMetaApi.saveProject(project)
    
    // 重置实体状态
    entities.value.forEach(entity => {
      entity.status = 'SYNCED'
    })
  }
  
  const loadProject = async (projectName: string): Promise<void> => {
    const response = await smartMetaApi.loadProject(projectName)
    entities.value = response.data.entities
    currentEntity.value = undefined
  }
  
  return {
    // 状态
    entities,
    currentEntity,
    isGenerating,
    entityStats,
    hasChanges,
    validationErrors,
    aiSuggestions,
    
    // 操作
    createEntity,
    updateEntity,
    deleteEntity,
    addField,
    updateField,
    removeField,
    addRelation,
    generateCode,
    saveProject,
    loadProject
  }
}
```

### **4.2 模型设计器Vue组件**

```vue
<!-- 📁 bone-smartmeta-studio/web/src/components/EntityDesigner.vue -->
<template>
  <div class="entity-designer">
    <!-- 🎯 顶部工具栏 -->
    <DesignerToolbar
      :has-changes="hasChanges"
      :is-generating="isGenerating"
      @create-entity="handleCreateEntity"
      @generate-code="handleGenerateCode"
      @save-project="handleSaveProject"
      @ai-suggest="handleAISuggest"
    />
    
    <!-- 📊 设计区域 -->
    <div class="designer-layout">
      <!-- 🏗️ 实体列表侧边栏 -->
      <EntityListPanel
        :entities="entities"
        :selected-entity="currentEntity"
        @select-entity="handleSelectEntity"
        @create-entity="handleCreateEntity"
        @delete-entity="handleDeleteEntity"
      />
      
      <!-- 📝 实体编辑器 -->
      <div class="editor-area">
        <div v-if="currentEntity" class="entity-editor">
          <!-- 实体基本信息 -->
          <EntityHeader
            :entity="currentEntity"
            @update="handleEntityUpdate"
          />
          
          <!-- 字段列表 -->
          <FieldList
            :fields="currentEntity.fields"
            :validation-errors="validationErrors"
            @add-field="handleAddField"
            @update-field="handleUpdateField"
            @remove-field="handleRemoveField"
          />
          
          <!-- 关系列表 -->
          <RelationList
            :relations="currentEntity.relations"
            :entities="entities"
            @add-relation="handleAddRelation"
            @remove-relation="handleRemoveRelation"
          />
          
          <!-- AI建议面板 -->
          <AISuggestionPanel
            :suggestions="aiSuggestions"
            @apply-suggestion="handleApplySuggestion"
          />
        </div>
        
        <div v-else class="empty-state">
          <el-empty description="请选择或创建实体">
            <el-button type="primary" @click="handleCreateEntity">
              创建第一个实体
            </el-button>
          </el-empty>
        </div>
      </div>
      
      <!-- 👁️ 实时预览侧边栏 -->
      <PreviewPanel
        :entity="currentEntity"
        :preview-code="previewCode"
        @preview-target="handlePreviewTarget"
      />
    </div>
    
    <!-- 🆕 实体创建对话框 -->
    <EntityCreateDialog
      v-model:visible="showCreateDialog"
      @confirm="handleEntityCreateConfirm"
    />
    
    <!-- ➕ 字段编辑对话框 -->
    <FieldEditDialog
      v-model:visible="showFieldDialog"
      :field="editingField"
      @confirm="handleFieldConfirm"
    />
    
    <!-- 🚀 生成配置对话框 -->
    <GenerationDialog
      v-model:visible="showGenerationDialog"
      @generate="handleGenerationConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DesignerToolbar from './DesignerToolbar.vue'
import EntityListPanel from './EntityListPanel.vue'
import EntityHeader from './EntityHeader.vue'
import FieldList from './FieldList.vue'
import RelationList from './RelationList.vue'
import AISuggestionPanel from './AISuggestionPanel.vue'
import PreviewPanel from './PreviewPanel.vue'
import EntityCreateDialog from './EntityCreateDialog.vue'
import FieldEditDialog from './FieldEditDialog.vue'
import GenerationDialog from './GenerationDialog.vue'

import { useEntityManager } from '../composables/useEntityManager'
import { useCodePreview } from '../composables/useCodePreview'

const {
  entities,
  currentEntity,
  isGenerating,
  hasChanges,
  validationErrors,
  aiSuggestions,
  createEntity,
  updateEntity,
  deleteEntity,
  addField,
  updateField,
  removeField,
  addRelation,
  generateCode,
  saveProject
} = useEntityManager()

const { previewCode, updatePreview } = useCodePreview()

// 🔄 响应式状态
const showCreateDialog = ref(false)
const showFieldDialog = ref(false)
const showGenerationDialog = ref(false)
const editingField = ref<any>(null)

// 🎯 事件处理
const handleCreateEntity = () => {
  showCreateDialog.value = true
}

const handleEntityCreateConfirm = async (entityData: any) => {
  try {
    await createEntity(entityData)
    ElMessage.success('实体创建成功')
  } catch (error: any) {
    ElMessage.error(`创建失败: ${error.message}`)
  }
}

const handleSelectEntity = (entity: any) => {
  currentEntity.value = entity
  updatePreview(entity, 'JAVA_ENTITY')
}

const handleEntityUpdate = async (updates: any) => {
  if (!currentEntity.value) return
  
  try {
    await updateEntity(currentEntity.value.name, updates)
    ElMessage.success('实体更新成功')
  } catch (error: any) {
    ElMessage.error(`更新失败: ${error.message}`)
  }
}

const handleDeleteEntity = async (entityName: string) => {
  try {
    await ElMessageBox.confirm(
      `确定删除实体 "${entityName}" 吗？此操作不可恢复。`,
      '确认删除',
      { type: 'warning' }
    )
    
    await deleteEntity(entityName)
    ElMessage.success('实体删除成功')
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(`删除失败: ${error.message}`)
    }
  }
}

const handleAddField = () => {
  editingField.value = null
  showFieldDialog.value = true
}

const handleUpdateField = (field: any) => {
  editingField.value = field
  showFieldDialog.value = true
}

const handleFieldConfirm = async (fieldData: any) => {
  if (!currentEntity.value) return
  
  try {
    if (editingField.value) {
      // 更新字段
      await updateField(currentEntity.value.name, editingField.value.name, fieldData)
      ElMessage.success('字段更新成功')
    } else {
      // 新增字段
      await addField(currentEntity.value.name, fieldData)
      ElMessage.success('字段添加成功')
    }
    
    updatePreview(currentEntity.value, 'JAVA_ENTITY')
  } catch (error: any) {
    ElMessage.error(`操作失败: ${error.message}`)
  }
}

const handleRemoveField = async (fieldName: string) => {
  if (!currentEntity.value) return
  
  try {
    await ElMessageBox.confirm(
      `确定删除字段 "${fieldName}" 吗？`,
      '确认删除',
      { type: 'warning' }
    )
    
    await removeField(currentEntity.value.name, fieldName)
    ElMessage.success('字段删除成功')
    updatePreview(currentEntity.value, 'JAVA_ENTITY')
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(`删除失败: ${error.message}`)
    }
  }
}

const handleAddRelation = async (relationData: any) => {
  if (!currentEntity.value) return
  
  try {
    await addRelation(currentEntity.value.name, relationData)
    ElMessage.success('关系添加成功')
  } catch (error: any) {
    ElMessage.error(`添加失败: ${error.message}`)
  }
}

const handleGenerateCode = () => {
  showGenerationDialog.value = true
}

const handleGenerationConfirm = async (config: any) => {
  try {
    const result = await generateCode(config.targets, config.options)
    ElMessage.success('代码生成成功')
    
    // 显示生成结果
    if (result.generatedFiles) {
      ElMessage.info(`生成了 ${result.generatedFiles.length} 个文件`)
    }
  } catch (error: any) {
    ElMessage.error(`生成失败: ${error.message}`)
  }
}

const handleSaveProject = async () => {
  try {
    const projectName = await ElMessageBox.prompt(
      '请输入项目名称',
      '保存项目',
      { inputValue: 'my-project' }
    )
    
    if (projectName.value) {
      await saveProject(projectName.value)
      ElMessage.success('项目保存成功')
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(`保存失败: ${error.message}`)
    }
  }
}

const handleAISuggest = async () => {
  if (!currentEntity.value) return
  
  try {
    // AI建议逻辑在 useEntityManager 中自动触发
    ElMessage.info('AI正在分析实体...')
  } catch (error: any) {
    ElMessage.error(`AI分析失败: ${error.message}`)
  }
}

const handleApplySuggestion = (suggestion: any) => {
  // 应用AI建议
  console.log('应用AI建议:', suggestion)
}

const handlePreviewTarget = (target: string) => {
  if (currentEntity.value) {
    updatePreview(currentEntity.value, target)
  }
}

onMounted(() => {
  // 加载示例数据或最近的项目
  console.log('EntityDesigner 组件已挂载')
})
</script>

<style scoped>
.entity-designer {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.designer-layout {
  flex: 1;
  display: grid;
  grid-template-columns: 280px 1fr 320px;
  gap: 0;
  height: calc(100vh - 64px);
}

.editor-area {
  background: white;
  border-left: 1px solid #e4e7ed;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
}

.entity-editor {
  padding: 24px;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
</style>
```

## ⚡ **5. Spring Boot Starter 深度集成**

### **5.1 自动配置与条件装配**

```java
// 📁 bone-smartmeta-starter/src/main/java/com/bone/smartmeta/autoconfigure/
package com.bone.smartmeta.autoconfigure;

/**
 * ⚡ SmartMeta 自动配置
 */
@Configuration
@ConditionalOnClass(SmartMetaEngine.class)
@EnableConfigurationProperties(SmartMetaProperties.class)
@AutoConfigureAfter({ DataSourceAutoConfiguration.class, WebMvcAutoConfiguration.class })
@Import({
    SmartMetaCoreConfiguration.class,
    SmartMetaWebConfiguration.class,
    SmartMetaDataConfiguration.class
})
public class SmartMetaAutoConfiguration {
    
    private final SmartMetaProperties properties;
    
    public SmartMetaAutoConfiguration(SmartMetaProperties properties) {
        this.properties = properties;
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "bone.smartmeta", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SmartMetaEngine smartMetaEngine(
            MetadataRegistry metadataRegistry,
            SmartCodeGenerator codeGenerator,
            ObjectProvider<AIService> aiService) {
        return new DefaultSmartMetaEngine(metadataRegistry, codeGenerator, 
            aiService.getIfAvailable());
    }
    
    @Bean
    @ConditionalOnMissingBean
    public MetadataRegistry metadataRegistry() {
        return new SmartMetadataRegistry();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public SmartCodeGenerator codeGenerator(
            ObjectProvider<TemplateEngine> templateEngine,
            ObjectProvider<AIService> aiService,
            MetadataRegistry registry) {
        return new SmartCodeGenerator(
            templateEngine.getIfAvailable(DefaultTemplateEngine::new),
            aiService.getIfAvailable(DefaultAIService::new),
            registry
        );
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SmartMetaController smartMetaController(SmartMetaEngine engine) {
        return new SmartMetaController(engine);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public SmartMetaEndpoint smartMetaEndpoint(MetadataRegistry registry) {
        return new SmartMetaEndpoint(registry);
    }
    
    /**
     * 🎯 配置实体扫描后置处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public static SmartEntityScanner smartEntityScanner() {
        return new SmartEntityScanner();
    }
    
    /**
     * 🔧 配置健康检查
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnEnabledHealthIndicator("smartmeta")
    public SmartMetaHealthIndicator smartMetaHealthIndicator(SmartMetaEngine engine) {
        return new SmartMetaHealthIndicator(engine);
    }
}

/**
 * 🧠 核心配置
 */
@Configuration
@ConditionalOnClass(SmartMetaEngine.class)
class SmartMetaCoreConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public TemplateEngine templateEngine() {
        return new SmartTemplateEngine();
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "bone.smartmeta.ai", name = "enabled", havingValue = "true")
    public AIService aiService(SmartMetaProperties properties) {
        return new DefaultAIService(properties.getAi());
    }
    
    @Bean
    @ConditionalOnMissingBean
    public EventPublisher eventPublisher() {
        return new DefaultEventPublisher();
    }
}

/**
 * 🌐 Web配置
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebMvcConfigurer.class)
class SmartMetaWebConfiguration implements WebMvcConfigurer {
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SmartMetaInterceptor())
            .addPathPatterns("/smartmeta/**");
    }
    
    @Bean
    @ConditionalOnMissingBean
    public SmartMetaInterceptor smartMetaInterceptor() {
        return new SmartMetaInterceptor();
    }
}

/**
 * 💾 数据配置
 */
@Configuration
@ConditionalOnClass(EntityManager.class)
class SmartMetaDataConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public SmartQueryFactory smartQueryFactory(EntityManager entityManager) {
        return new SmartQueryFactory(entityManager);
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DataSource.class)
    public DatabaseMetadataExtractor databaseMetadataExtractor(DataSource dataSource) {
        return new DatabaseMetadataExtractor(dataSource);
    }
}

/**
 * 📋 配置属性
 */
@ConfigurationProperties(prefix = "bone.smartmeta")
@Data
@Validated
public class SmartMetaProperties {
    
    /**
     * 是否启用SmartMeta
     */
    private boolean enabled = true;
    
    /**
     * 实体扫描包路径
     */
    @NotEmpty
    private String[] entityPackages = {};
    
    /**
     * 代码生成配置
     */
    @Valid
    private Generation generation = new Generation();
    
    /**
     * AI增强配置
     */
    @Valid
    private AI ai = new AI();
    
    /**
     * 工作室配置
     */
    @Valid
    private Studio studio = new Studio();
    
    @Data
    public static class Generation {
        private String outputDir = "generated-sources";
        private boolean overwrite = false;
        private String templatePath = "classpath:/templates";
        private boolean autoSync = true;
        private CodeStyle codeStyle = CodeStyle.MODERN;
    }
    
    @Data
    public static class AI {
        private boolean enabled = true;
        private String model = "gpt-3.5-turbo";
        private double temperature = 0.7;
        private String apiKey;
        private String baseUrl = "https://api.openai.com/v1";
        private int maxTokens = 2000;
        private double topP = 1.0;
    }
    
    @Data
    public static class Studio {
        private boolean enabled = true;
        private int port = 3000;
        private String host = "localhost";
        private boolean browserAutoOpen = true;
    }
}
```

### **5.2 启用注解与配置**

```java
// 📁 bone-smartmeta-starter/src/main/java/com/bone/smartmeta/annotation/
package com.bone.smartmeta.annotation;

/**
 * 🎯 启用SmartMeta
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(SmartMetaAutoConfiguration.class)
public @interface EnableSmartMeta {
    
    /**
     * 扫描的实体包
     */
    String[] value() default {};
    
    /**
     * 是否启用AI增强
     */
    boolean aiEnhanced() default true;
    
    /**
     * 是否自动生成代码
     */
    boolean autoGenerate() default true;
    
    /**
     * 代码风格
     */
    CodeStyle codeStyle() default CodeStyle.MODERN;
}

/**
 * 🎯 启用SmartMeta工作室
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnWebApplication
@Import(SmartMetaStudioConfiguration.class)
public @interface EnableSmartMetaStudio {
    
    /**
     * 工作室端口
     */
    int port() default 3000;
    
    /**
     * 自动打开浏览器
     */
    boolean autoOpen() default true;
}

/**
 * 🔧 配置示例
 */
@SpringBootApplication
@EnableSmartMeta(
    value = {"com.example.entity", "com.example.model"},
    aiEnhanced = true,
    autoGenerate = true,
    codeStyle = CodeStyle.MODERN
)
@EnableSmartMetaStudio(port = 3000)
public class Application {
    
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## 💻 **6. CLI 工具完整实现**

### **6.1 命令架构与实现**

```javascript
// 📁 bone-smartmeta-cli/src/core/CommandRegistry.js
const { Command } = require('commander')
const chalk = require('chalk')
const { logger } = require('../utils/logger')
const { configManager } = require('../utils/configManager')

/**
 * 🎯 命令注册表
 */
class CommandRegistry {
  constructor() {
    this.program = new Command()
    this.commands = new Map()
    this.setupProgram()
  }

  setupProgram() {
    this.program
      .name('smartmeta')
      .description('🦴 Bone SmartMeta CLI - 智能元数据驱动开发工具')
      .version('2.0.0', '-v, --version', '显示版本信息')
      .option('--config <file>', '配置文件路径', './smartmeta.config.js')
      .option('--verbose', '详细输出', false)
      .hook('preAction', this.preAction.bind(this))
      .hook('postAction', this.postAction.bind(this))
  }

  async preAction(thisCommand, actionCommand) {
    // 加载配置
    const configFile = thisCommand.opts().config
    await configManager.load(configFile)

    // 设置日志级别
    if (thisCommand.opts().verbose) {
      logger.level = 'debug'
    }

    logger.info(`🚀 执行命令: ${actionCommand.name()}`)
  }

  async postAction(thisCommand, actionCommand) {
    const duration = actionCommand._actionResults?.[0]?.duration || 0
    logger.info(`✅ 命令执行完成 (${duration}ms)`)
  }

  register(commandClass) {
    const commandInstance = new commandClass()
    const command = commandInstance.getCommand()
    
    this.commands.set(command.name(), commandInstance)
    this.program.addCommand(command)
    
    logger.debug(`注册命令: ${command.name()}`)
  }

  registerAll(commandClasses) {
    commandClasses.forEach(commandClass => this.register(commandClass))
  }

  async run(argv) {
    try {
      await this.program.parseAsync(argv)
    } catch (error) {
      logger.error(`命令执行失败: ${error.message}`)
      process.exit(1)
    }
  }

  getProgram() {
    return this.program
  }
}

module.exports = CommandRegistry

// 📁 bone-smartmeta-cli/src/commands/InitCommand.js
const { Command } = require('commander')
const fs = require('fs-extra')
const path = require('path')
const { promisify } = require('util')
const exec = promisify(require('child_process').exec)
const { logger } = require('../utils/logger')
const { templateManager } = require('../services/templateManager')

/**
 * 🆕 项目初始化命令
 */
class InitCommand {
  constructor() {
    this.command = new Command('init')
    this.setupOptions()
    this.setupAction()
  }

  setupOptions() {
    this.command
      .description('初始化新的 SmartMeta 项目')
      .argument('<project-name>', '项目名称')
      .option('-t, --template <template>', '项目模板', 'spring-boot')
      .option('--package <package>', 'Java包名')
      .option('--database <db>', '数据库类型', 'mysql')
      .option('--frontend <fe>', '前端框架', 'vue3')
      .option('--description <desc>', '项目描述')
      .option('--skip-install', '跳过依赖安装', false)
      .option('--skip-git', '跳过Git初始化', false)
      .option('-f, --force', '强制覆盖现有目录', false)
  }

  setupAction() {
    this.command.action(async (projectName, options) => {
      const startTime = Date.now()
      
      try {
        await this.execute(projectName, options)
        
        const duration = Date.now() - startTime
        return { success: true, duration }
      } catch (error) {
        const duration = Date.now() - startTime
        logger.error(`初始化失败: ${error.message}`)
        return { success: false, duration, error: error.message }
      }
    })
  }

  async execute(projectName, options) {
    const projectPath = path.resolve(process.cwd(), projectName)
    
    // 验证项目目录
    await this.validateProjectPath(projectPath, options.force)
    
    logger.info(`🎯 初始化项目: ${chalk.cyan(projectName)}`)
    logger.info(`📁 项目路径: ${chalk.gray(projectPath)}`)
    
    // 创建项目结构
    await this.createProjectStructure(projectPath, projectName, options)
    
    // 生成配置文件
    await this.generateConfigFiles(projectPath, projectName, options)
    
    // 初始化Git仓库
    if (!options.skipGit) {
      await this.initGitRepository(projectPath)
    }
    
    // 安装依赖
    if (!options.skipInstall) {
      await this.installDependencies(projectPath, options)
    }
    
    this.printSuccessMessage(projectName, projectPath, options)
  }

  async validateProjectPath(projectPath, force) {
    if (await fs.pathExists(projectPath)) {
      if (!force) {
        throw new Error(
          `目录 ${projectPath} 已存在。使用 ${chalk.yellow('--force')} 参数覆盖现有目录。`
        )
      }
      
      logger.warn(`⚠️  覆盖现有目录: ${projectPath}`)
      await fs.remove(projectPath)
    }
  }

  async createProjectStructure(projectPath, projectName, options) {
    const dirs = [
      'src/main/java',
      'src/main/resources',
      'src/test/java',
      'src/main/frontend/src',
      'src/main/frontend/public',
      'models',
      'generated',
      'docs',
      '.smartmeta'
    ]
    
    for (const dir of dirs) {
      const fullPath = path.join(projectPath, dir)
      await fs.ensureDir(fullPath)
      logger.debug(`创建目录: ${dir}`)
    }
    
    // 创建必要的文件
    await this.createEssentialFiles(projectPath, projectName, options)
  }

  async createEssentialFiles(projectPath, projectName, options) {
    // 创建 README.md
    await this.createReadme(projectPath, projectName, options)
    
    // 创建 .gitignore
    await this.createGitignore(projectPath)
    
    // 创建示例实体
    await this.createExampleEntity(projectPath, options)
  }

  async generateConfigFiles(projectPath, projectName, options) {
    const packageName = options.package || `com.example.${projectName.toLowerCase()}`
    
    // 生成 smartmeta.config.js
    const config = {
      project: {
        name: projectName,
        version: '1.0.0',
        description: options.description || `基于 SmartMeta 的 ${projectName} 项目`,
        package: packageName
      },
      database: {
        type: options.database,
        url: `jdbc:${options.database}://localhost:3306/${projectName}`,
        username: 'root',
        password: 'password',
        driver: this.getDriverClass(options.database)
      },
      generation: {
        targets: ['java', 'vue', 'sql'],
        outputDir: './generated',
        aiEnhancement: true,
        codeStyle: 'MODERN',
        overwriteExisting: true
      },
      server: {
        port: 8080,
        contextPath: '/api'
      },
      frontend: {
        framework: options.frontend,
        port: 3000
      }
    }
    
    const configContent = `module.exports = ${JSON.stringify(config, null, 2)}`
    await fs.writeFile(path.join(projectPath, 'smartmeta.config.js'), configContent)
    logger.info(`📄 生成配置文件: ${chalk.cyan('smartmeta.config.js')}`)
    
    // 生成 Spring Boot 配置
    await this.generateSpringBootConfig(projectPath, packageName, options)
    
    // 生成前端配置
    await this.generateFrontendConfig(projectPath, options)
  }

  async generateSpringBootConfig(projectPath, packageName, options) {
    const resourcesDir = path.join(projectPath, 'src/main/resources')
    
    // application.yml
    const appYml = `
spring:
  application:
    name: ${path.basename(projectPath)}
  datasource:
    url: \${SMARTMETA_DB_URL:jdbc:${options.database}://localhost:3306/${path.basename(projectPath)}}
    username: \${SMARTMETA_DB_USERNAME:root}
    password: \${SMARTMETA_DB_PASSWORD:password}
    driver-class-name: ${this.getDriverClass(options.database)}
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true

bone:
  smartmeta:
    enabled: true
    entity-packages:
      - "${packageName}.entity"
    generation:
      auto-sync: true
      output-dir: "./generated"
    ai:
      enabled: true

server:
  port: \${SMARTMETA_SERVER_PORT:8080}

logging:
  level:
    com.bone.smartmeta: DEBUG
    ${packageName.replace(/\./g, '/'}}.: DEBUG
`
    await fs.writeFile(path.join(resourcesDir, 'application.yml'), appYml.trim())
    
    // 生成主应用类
    await this.generateApplicationClass(projectPath, packageName)
  }

  async generateApplicationClass(projectPath, packageName) {
    const javaDir = path.join(projectPath, 'src/main/java', ...packageName.split('.'))
    await fs.ensureDir(javaDir)
    
    const appClass = `
package ${packageName};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.bone.smartmeta.annotation.EnableSmartMeta;

@SpringBootApplication
@EnableSmartMeta
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
`.trim()
    
    await fs.writeFile(path.join(javaDir, 'Application.java'), appClass)
    logger.info(`📄 生成应用类: ${chalk.cyan('Application.java')}`)
  }

  async generateFrontendConfig(projectPath, options) {
    if (options.frontend === 'vue3') {
      await this.generateVueConfig(projectPath, options)
    } else if (options.frontend === 'react') {
      await this.generateReactConfig(projectPath, options)
    }
  }

  async generateVueConfig(projectPath, options) {
    const frontendDir = path.join(projectPath, 'src/main/frontend')
    
    // package.json
    const packageJson = {
      name: path.basename(projectPath) + '-frontend',
      version: '1.0.0',
      type: 'module',
      scripts: {
        dev: 'vite',
        build: 'vite build',
        preview: 'vite preview'
      },
      dependencies: {
        'vue': '^3.3.0',
        'vue-router': '^4.2.0',
        'element-plus': '^2.3.0',
        'axios': '^1.4.0'
      },
      devDependencies: {
        'vite': '^4.4.0',
        '@vitejs/plugin-vue': '^4.3.0',
        'typescript': '^5.0.0',
        'vue-tsc': '^1.4.0'
      }
    }
    
    await fs.writeJson(path.join(frontendDir, 'package.json'), packageJson, { spaces: 2 })
    
    // vite.config.ts
    const viteConfig = `
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
`.trim()
    
    await fs.writeFile(path.join(frontendDir, 'vite.config.ts'), viteConfig)
    
    logger.info(`📄 生成前端配置: ${chalk.cyan('Vue 3 + Vite')}`)
  }

  async initGitRepository(projectPath) {
    try {
      await exec('git init', { cwd: projectPath })
      await exec('git add .', { cwd: projectPath })
      await exec('git commit -m "Initial commit with SmartMeta"', { cwd: projectPath })
      logger.info(`🔧 初始化 Git 仓库`)
    } catch (error) {
      logger.warn('⚠️  Git 初始化失败，继续执行...')
    }
  }

  async installDependencies(projectPath, options) {
    logger.info('📦 安装依赖...')
    
    try {
      // 安装后端依赖
      if (await fs.pathExists(path.join(projectPath, 'pom.xml'))) {
        logger.info('🔧 安装 Maven 依赖...')
        await exec('mvn clean install -DskipTests', { cwd: projectPath })
      }
      
      // 安装前端依赖
      const frontendDir = path.join(projectPath, 'src/main/frontend')
      if (await fs.pathExists(path.join(frontendDir, 'package.json'))) {
        logger.info('🎨 安装前端依赖...')
        await exec('npm install', { cwd: frontendDir })
      }
      
    } catch (error) {
      logger.warn('⚠️  依赖安装失败，请手动安装')
    }
  }

  printSuccessMessage(projectName, projectPath, options) {
    logger.success(`✅ 项目 ${chalk.cyan(projectName)} 初始化完成!`)
    
    console.log(chalk.green(`
🎉 项目创建成功！

📁 项目路径: ${chalk.cyan(projectPath)}

🚀 开始开发:

  ${chalk.cyan(`cd ${projectName}`)}
  
  后端开发:
  ${chalk.cyan('./mvnw spring-boot:run')}
  
  前端开发:
  ${chalk.cyan(`cd src/main/frontend && npm run dev`)}
  
  启动设计器:
  ${chalk.cyan('smartmeta studio')}

📚 下一步:

  1. 配置数据库连接
  2. 使用 ${chalk.cyan('smartmeta studio')} 设计数据模型
  3. 使用 ${chalk.cyan('smartmeta generate')} 生成代码
  4. 开始业务开发！

💡 提示:

  - 查看 ${chalk.cyan('README.md')} 获取详细指南
  - 访问 ${chalk.cyan('http://localhost:3000')} 打开设计器
  - 访问 ${chalk.cyan('http://localhost:8080')} 查看后端API

 Happy coding! 🎊
    `.trim()))
  }

  getDriverClass(dbType) {
    const drivers = {
      mysql: 'com.mysql.cj.jdbc.Driver',
      postgresql: 'org.postgresql.Driver',
      oracle: 'oracle.jdbc.OracleDriver',
      sqlserver: 'com.microsoft.sqlserver.jdbc.SQLServerDriver'
    }
    return drivers[dbType] || drivers.mysql
  }

  async createReadme(projectPath, projectName, options) {
    const readme = `
# ${projectName}

基于 Bone SmartMeta Engine 构建的智能应用。

## 🚀 特性

- ✅ 智能代码生成
- ✅ AI增强开发  
- ✅ 可视化模型设计
- ✅ 全栈代码同步
- ✅ 企业级架构

## 🛠️ 技术栈

### 后端
- Java 17+
- Spring Boot 3.1
- Bone SmartMeta Engine
- ${options.database} 数据库

### 前端  
- ${options.frontend === 'vue3' ? 'Vue 3 + TypeScript + Vite' : 'React + TypeScript + Vite'}
- Element Plus / Ant Design
- Axios

## 📖 快速开始

### 环境要求

- Java 17+
- Node.js 16+
- ${options.database} 数据库
- Maven 3.6+

### 开发命令

\`\`\`bash
# 启动后端服务
./mvnw spring-boot:run

# 启动前端开发服务器
cd src/main/frontend
npm run dev

# 启动 SmartMeta 设计器
smartmeta studio
\`\`\`

### 智能开发流程

1. **设计数据模型**
   \`\`\`bash
   smartmeta studio
   \`\`\`

2. **生成代码**
   \`\`\`bash
   smartmeta generate --target java,vue,sql
   \`\`\`

3. **运行应用**
   \`\`\`bash
   ./mvnw spring-boot:run
   \`\`\`

## 📁 项目结构

\`\`\`
${projectName}/
├── src/main/java/          # Java 源代码
├── src/main/resources/     # 配置文件
├── src/main/frontend/      # 前端代码
├── models/                 # 数据模型定义
├── generated/              # 生成的代码
├── docs/                   # 项目文档
└── smartmeta.config.js     # SmartMeta 配置
\`\`\`

## 🔧 配置说明

主要配置文件：

- \`smartmeta.config.js\` - SmartMeta 引擎配置
- \`src/main/resources/application.yml\` - Spring Boot 配置
- \`src/main/frontend/vite.config.ts\` - 前端构建配置

## 🤝 开发指南

### 添加新实体

1. 在设计器中创建实体
2. 添加字段和关系
3. 生成代码
4. 实现业务逻辑

### 自定义生成模板

模板文件位于: \`.smartmeta/templates/\`

## 📞 获取帮助

- [SmartMeta 文档](https://smartmeta.bone.dev)
- [问题反馈](https://github.com/bone/smartmeta/issues)
- [社区讨论](https://github.com/bone/smartmeta/discussions)

---

> 由 Bone SmartMeta Engine 强力驱动 🦴
    `.trim()
    
    await fs.writeFile(path.join(projectPath, 'README.md'), readme)
  }

  async createGitignore(projectPath) {
    const gitignore = `
# Java
*.class
*.jar
*.war
*.ear
target/
build/

# Node.js
node_modules/
npm-debug.log*
yarn-debug.log*
yarn-error.log*

# IDE
.vscode/
.idea/
*.iml
*.ipr
*.iws

# OS
.DS_Store
Thumbs.db

# Logs
*.log
logs/

# Generated
generated/
!.smartmeta/templates/

# Environment
.env
.env.local
.env.production

# SmartMeta
.smartmeta/cache/
    `.trim()
    
    await fs.writeFile(path.join(projectPath, '.gitignore'), gitignore)
  }

  async createExampleEntity(projectPath, options) {
    const modelsDir = path.join(projectPath, 'models')
    const exampleEntity = {
      name: "User",
      tableName: "t_user",
      description: "用户信息实体",
      fields: [
        {
          name: "id",
          type: "LONG",
          primaryKey: true,
          autoIncrement: true,
          required: true,
          comment: "用户ID"
        },
        {
          name: "username",
          type: "STRING",
          length: 50,
          required: true,
          unique: true,
          comment: "用户名"
        },
        {
          name: "email",
          type: "STRING",
          length: 100,
          required: true,
          comment: "邮箱地址"
        },
        {
          name: "age",
          type: "INTEGER",
          comment: "用户年龄"
        }
      ],
      relations: [],
      businessRules: [],
      aiConfig: { enabled: true },
      status: "DRAFT"
    }
    
    await fs.writeJson(path.join(modelsDir, 'user.entity.json'), exampleEntity, { spaces: 2 })
    logger.info(`📄 创建示例实体: ${chalk.cyan('user.entity.json')}`)
  }

  getCommand() {
    return this.command
  }
}

module.exports = InitCommand
```

## 📊 **7. 监控与运维实现**

### **7.1 健康检查与指标收集**

```java
// 📁 bone-smartmeta-engine/src/main/java/com/bone/smartmeta/core/monitoring/
package com.bone.smartmeta.core.monitoring;

/**
 * 📈 SmartMeta 健康检查
 */
@Component
@Slf4j
public class SmartMetaHealthIndicator implements HealthIndicator {
    
    private final SmartMetaEngine engine;
    private final MetadataRegistry registry;
    private final SmartCodeGenerator generator;
    
    public SmartMetaHealthIndicator(SmartMetaEngine engine, MetadataRegistry registry, 
                                   SmartCodeGenerator generator) {
        this.engine = engine;
        this.registry = registry;
        this.generator = generator;
    }
    
    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        
        try {
            // 检查实体注册状态
            RegistryStats stats = registry.getStats();
            builder.withDetail("entities.registered", stats.getTotalEntities())
                   .withDetail("entities.fields", stats.getTotalFields())
                   .withDetail("entities.relations", stats.getTotalRelations())
                   .withDetail("entities.packages", stats.getPackages());
            
            // 检查生成器状态
            builder.withDetail("generator.ready", generator.isReady())
                   .withDetail("generator.templates", generator.getAvailableTemplates().size());
            
            // 检查AI服务状态
            builder.withDetail("ai.enabled", engine.isAIEnabled())
                   .withDetail("ai.connected", checkAIConnection());
            
            // 检查系统资源
            builder.withDetail("system.memory", getMemoryUsage())
                   .withDetail("system.threads", getThreadCount());
            
            return builder.build();
            
        } catch (Exception e) {
            log.error("健康检查失败", e);
            return Health.down(e).build();
        }
    }
    
    private boolean checkAIConnection() {
        try {
            return engine.getAIService().isConnected();
        } catch (Exception e) {
            return false;
        }
    }
    
    private Map<String, Object> getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        return Map.of(
            "used", formatMemory(usedMemory),
            "total", formatMemory(totalMemory),
            "max", formatMemory(maxMemory),
            "usage", String.format("%.2f%%", (double) usedMemory / totalMemory * 100)
        );
    }
    
    private String formatMemory(long bytes) {
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
    
    private int getThreadCount() {
        return Thread.activeCount();
    }
}

/**
 * 📊 SmartMeta 指标收集
 */
@Component
@Slf4j
public class SmartMetaMetrics {
    
    private final MeterRegistry meterRegistry;
    private final Counter generationCounter;
    private final Timer generationTimer;
    private final Gauge entityGauge;
    private final Counter errorCounter;
    
    public SmartMetaMetrics(MeterRegistry meterRegistry, MetadataRegistry registry) {
        this.meterRegistry = meterRegistry;
        
        // 代码生成指标
        this.generationCounter = Counter.builder("smartmeta.generation.requests")
            .description("代码生成请求计数")
            .tag("component", "generator")
            .register(meterRegistry);
            
        this.generationTimer = Timer.builder("smartmeta.generation.duration")
            .description("代码生成耗时")
            .tag("component", "generator")
            .register(meterRegistry);
        
        // 实体数量指标
        this.entityGauge = Gauge.builder("smartmeta.entities.count")
            .description("注册的实体数量")
            .tag("component", "registry")
            .register(meterRegistry, registry, r -> r.getStats().getTotalEntities());
        
        // 错误指标
        this.errorCounter = Counter.builder("smartmeta.errors")
            .description("错误计数")
            .tag("component", "core")
            .register(meterRegistry);
    }
    
    public void recordGeneration(String target, long duration, boolean success) {
        generationCounter.increment();
        generationTimer.record(duration, TimeUnit.MILLISECONDS);
        
        meterRegistry.counter("smartmeta.generation.target", 
            "target", target,
            "success", String.valueOf(success)
        ).increment();
    }
    
    public void recordError(String errorType, String component) {
        errorCounter.increment();
        
        meterRegistry.counter("smartmeta.errors.detail",
            "type", errorType,
            "component", component
        ).increment();
    }
    
    public void recordEntityOperation(String operation, String entityType) {
        meterRegistry.counter("smartmeta.entity.operations",
            "operation", operation,
            "type", entityType
        ).increment();
    }
}

/**
 * 📈 性能监控切面
 */
@Aspect
@Component
@Slf4j
public class PerformanceMonitorAspect {
    
    private final SmartMetaMetrics metrics;
    
    public PerformanceMonitorAspect(SmartMetaMetrics metrics) {
        this.metrics = metrics;
    }
    
    @Around("@annotation(MonitorPerformance)")
    public Object monitorPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        long startTime = System.currentTimeMillis();
        boolean success = false;
        
        try {
            Object result = joinPoint.proceed();
            success = true;
            return result;
            
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            // 记录指标
            metrics.recordGeneration(className, duration, success);
            
            // 记录慢查询
            if (duration > 1000) { // 1秒阈值
                log.warn("慢方法执行: {}.{} 耗时 {}ms", className, methodName, duration);
            }
            
            log.debug("方法执行: {}.{} 耗时 {}ms", className, methodName, duration);
        }
    }
}
```

这个完整的实现方案提供了从核心引擎到开发工具的全套解决方案，具有以下特点：

## 🏆 **方案核心优势**

### **1. 架构优势**
- ✅ **DDD领域驱动设计** - 清晰的领域模型和界限上下文
- ✅ **六边形架构** - 内外分离，易于测试和维护
- ✅ **事件驱动** - 松耦合的组件通信
- ✅ **响应式编程** - 高性能的非阻塞处理

### **2. 技术优势**
- ✅ **类型安全** - 全面的泛型和类型约束
- ✅ **函数式编程** - 流式API和不可变对象
- ✅ **AOP切面** - 统一的横切关注点处理
- ✅ **SPI扩展** - 灵活的插件化架构

### **3. 工程优势**
- ✅ **完整工具链** - 从CLI到IDE的全面支持
- ✅ **监控运维** - 完善的指标收集和健康检查
- ✅ **配置化** - 高度可配置的生成策略
- ✅ **文档完整** - 详细的代码注释和使用示例

### **4. 智能优势**
- ✅ **AI增强** - 智能代码生成和优化
- ✅ **自适应** - 基于使用模式的自我优化
- ✅ **预测性** - 智能建议和预警
- ✅ **学习能力** - 持续改进的生成质量

这个方案为企业级元数据驱动开发提供了完整的解决方案，可以显著提升开发效率和质量。