## 🏆 **Bone SmartMeta — 智能元数据引擎完整技术方案**

## 🎯 **1. 核心架构设计**

### **整体技术栈**
```yaml
技术架构:
  后端核心:
    - Java 17 + Spring Boot 3.1
    - 响应式编程: Project Reactor
    - 数据访问: R2DBC + MyBatis Plus
  前端设计器:
    - Vue 3 + TypeScript + Vite
    - Element Plus + Naive UI
    - Monaco Editor (VS Code内核)
  智能增强:
    - 规则引擎: Drools + Easy Rules
    - AI集成: OpenAI API + 本地ML模型
    - 向量计算: Apache Spark ML
  基础设施:
    - 服务网格: Spring Cloud Gateway
    - 配置中心: Nacos
    - 监控: Micrometer + Prometheus
```

### **核心模块架构**
```bash
bone-smartmeta/
├── bone-smartmeta-engine/              # 🧠 核心引擎
├── bone-smartmeta-sdk/                 # 🛠️ 开发工具包
├── bone-smartmeta-studio/              # 🎨 可视化设计平台
├── bone-smartmeta-starter/             # ⚡ Spring Boot启动器
├── bone-smartmeta-cli/                 # 💻 命令行工具
├── bone-smartmeta-examples/            # 📚 示例工程
└── bone-smartmeta-bom/                 # 📋 依赖管理
```

## 🚀 **2. 核心引擎详细实现**

### **2.1 智能元数据建模核心**

```java
// 📁 bone-smartmeta-engine/src/main/java/com/bone/smartmeta/core/metamodel/
package com.bone.smartmeta.core.metamodel;

/**
 * 🏗️ 智能实体元数据定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartEntity implements Serializable {

    @NotBlank
    private String name;

    private String tableName;

    private String description;

    @Builder.Default
    private List<SmartField> fields = new ArrayList<>();

    @Builder.Default
    private List<SmartRelation> relations = new ArrayList<>();

    @Builder.Default
    private List<BusinessRule> businessRules = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> extensions = new HashMap<>();

    private AIConfig aiConfig;

    /**
     * 🎯 添加智能字段
     */
    public SmartEntity addField(SmartField field) {
        this.fields.add(field);
        return this;
    }

    /**
     * 🔍 查找字段
     */
    public Optional<SmartField> findField(String fieldName) {
        return fields.stream()
            .filter(f -> f.getName().equals(fieldName))
            .findFirst();
    }
}

/**
 * 📊 智能字段定义
 */
@Data
@Builder
public class SmartField {

    @NotBlank
    private String name;

    private FieldType type;

    private Integer length;

    private Integer precision;

    private Boolean required;

    private Boolean unique;

    private String defaultValue;

    private String comment;

    @Builder.Default
    private List<ValidationRule> validationRules = new ArrayList<>();

    private UIConfig uiConfig;

    private AISuggestion aiSuggestion;
}

/**
 * 🔗 智能关系定义
 */
@Data
@Builder
public class SmartRelation {

    private String name;

    private RelationType type;

    private String targetEntity;

    private String joinField;

    private CascadeType cascade;

    private FetchType fetchType;

    private String description;
}
```

### **2.2 智能元数据注册中心**

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

    private final EventPublisher eventPublisher;

    /**
     * 📝 注册智能实体
     */
    public void registerEntity(SmartEntity entity) {
        String entityName = entity.getName();

        if (entityRegistry.containsKey(entityName)) {
            log.warn("实体 {} 已存在，执行更新操作", entityName);
            // 发布实体更新事件
            eventPublisher.publishEvent(new EntityUpdateEvent(this, entity));
        }

        entityRegistry.put(entityName, entity);
        log.info("✅ 成功注册智能实体: {}", entityName);

        // 发布实体注册事件
        eventPublisher.publishEvent(new EntityRegisterEvent(this, entity));
    }

    /**
     * 🔍 获取实体定义
     */
    public Optional<SmartEntity> getEntity(String entityName) {
        return Optional.ofNullable(entityRegistry.get(entityName));
    }

    /**
     * 📋 获取所有实体
     */
    public List<SmartEntity> getAllEntities() {
        return new ArrayList<>(entityRegistry.values());
    }

    /**
     * 🗑️ 注销实体
     */
    public boolean unregisterEntity(String entityName) {
        SmartEntity removed = entityRegistry.remove(entityName);
        if (removed != null) {
            eventPublisher.publishEvent(new EntityUnregisterEvent(this, removed));
            log.info("🗑️ 注销实体: {}", entityName);
            return true;
        }
        return false;
    }
}
```

### **2.3 智能代码生成引擎**

```java
// 📁 bone-smartmeta-engine/src/main/java/com/bone/smartmeta/core/generator/
package com.bone.smartmeta.core.generator;

/**
 * 🚀 智能代码生成引擎
 */
@Component
@Slf4j
public class SmartCodeGenerator {

    private final TemplateEngine templateEngine;
    private final AIService aiService;
    private final MetadataRegistry metadataRegistry;

    /**
     * 🎯 智能生成代码
     */
    public GenerationResult generateIntelligently(GenerationRequest request) {
        log.info("开始智能代码生成，目标: {}", request.getTarget());

        // 1. 🧠 AI增强分析
        AIAnalysisResult analysis = aiService.analyzeGeneration(request);

        // 2. 🎨 智能模板选择
        TemplateConfig template = selectOptimalTemplate(request, analysis);

        // 3. ⚡ 执行代码生成
        GenerationContext context = buildGenerationContext(request, analysis);
        String generatedCode = templateEngine.process(template, context);

        // 4. 🔧 AI代码优化
        if (request.isAiOptimization()) {
            generatedCode = aiService.optimizeCode(generatedCode, request.getCodeStyle());
        }

        // 5. ✅ 质量验证
        QualityCheckResult quality = validateCodeQuality(generatedCode);

        return GenerationResult.builder()
            .success(true)
            .generatedCode(generatedCode)
            .outputPath(buildOutputPath(request))
            .qualityReport(quality)
            .aiSuggestions(analysis.getSuggestions())
            .build();
    }

    /**
     * 🎪 批量生成
     */
    public BatchGenerationResult generateBatch(List<GenerationRequest> requests) {
        return requests.parallelStream()
            .map(this::generateIntelligently)
            .collect(Collectors.collectingAndThen(
                Collectors.toList(),
                BatchGenerationResult::new
            ));
    }

    /**
     * 🔄 实时预览生成
     */
    public String previewGeneration(GenerationRequest request) {
        GenerationRequest previewRequest = request.toBuilder()
            .outputMode(OutputMode.PREVIEW)
            .build();

        GenerationResult result = generateIntelligently(previewRequest);
        return result.getGeneratedCode();
    }
}
```

### **2.4 智能模板引擎**

```java
// 📁 bone-smartmeta-engine/src/main/java/com/bone/smartmeta/core/template/
package com.bone.smartmeta.core.template;

/**
 * 🎨 智能模板引擎
 */
@Component
public class SmartTemplateEngine {

    private final Map<String, TemplateProcessor> processors;
    private final AIService aiService;

    /**
     * ⚡ 处理模板
     */
    public String process(TemplateConfig config, GenerationContext context) {
        TemplateProcessor processor = processors.get(config.getType());
        if (processor == null) {
            throw new TemplateException("不支持的模板类型: " + config.getType());
        }

        // 1. 🧠 AI增强的上下文处理
        GenerationContext enhancedContext = enhanceContextWithAI(context);

        // 2. 🎯 执行模板处理
        String result = processor.process(config, enhancedContext);

        // 3. ✨ AI后处理优化
        if (config.isAiPostProcess()) {
            result = aiService.postProcessTemplate(result, config, enhancedContext);
        }

        return result;
    }

    /**
     * 🔍 查找最优模板
     */
    public TemplateConfig findOptimalTemplate(GenerationRequest request) {
        List<TemplateConfig> candidates = findCandidateTemplates(request);

        // 使用AI推荐最优模板
        return aiService.recommendTemplate(candidates, request);
    }

    /**
     * 📝 注册模板处理器
     */
    public void registerProcessor(String type, TemplateProcessor processor) {
        processors.put(type, processor);
    }
}

/**
 * 📄 Java实体模板
 */
@Component
@Slf4j
public class JavaEntityTemplate implements TemplateProcessor {

    @Override
    public String process(TemplateConfig config, GenerationContext context) {
        SmartEntity entity = context.getEntity();

        return """
            package %s;

            import lombok.Data;
            import lombok.EqualsAndHashCode;
            import javax.persistence.*;
            import java.time.LocalDateTime;

            /**
             * %s
             *
             * @author SmartMeta Engine
             * @generated %s
             */
            @Data
            @Entity
            @Table(name = "%s")
            @EqualsAndHashCode(callSuper = %s)
            public class %s %s {
                %s
            }
            """.formatted(
                context.getPackageName(),
                entity.getDescription(),
                LocalDateTime.now(),
                entity.getTableName(),
                context.hasBaseEntity(),
                entity.getName(),
                context.hasBaseEntity() ? "extends BaseEntity" : "",
                generateFieldsCode(entity)
            );
    }

    private String generateFieldsCode(SmartEntity entity) {
        return entity.getFields().stream()
            .map(this::generateFieldCode)
            .collect(Collectors.joining("\n    "));
    }

    private String generateFieldCode(SmartField field) {
        return """
            /**
             * %s
             */
            @Column(name = "%s"%s)
            private %s %s;
            """.formatted(
                field.getComment(),
                field.getName(),
                buildColumnAnnotations(field),
                field.getType().getJavaType(),
                field.getName()
            );
    }
}
```

## 🛠️ **3. SDK 开发工具包实现**

### **3.1 智能注解驱动**

```java
// 📁 bone-smartmeta-sdk/src/main/java/com/bone/smartmeta/annotation/
package com.bone.smartmeta.annotation;

/**
 * 🏷️ 智能实体注解
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SmartEntity {

    /**
     * 实体名称
     */
    String name() default "";

    /**
     * 数据库表名
     */
    String tableName() default "";

    /**
     * 实体描述
     */
    String description() default "";

    /**
     * 启用AI增强
     */
    boolean aiEnhanced() default true;

    /**
     * 业务域
     */
    String domain() default "default";
}

/**
 * 📊 智能字段注解
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SmartField {

    String name() default "";

    FieldType type() default FieldType.STRING;

    int length() default 255;

    boolean required() default false;

    boolean unique() default false;

    String defaultValue() default "";

    String comment() default "";

    String validationRule() default "";
}

/**
 * 🔗 智能关系注解
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SmartRelation {

    RelationType type();

    String targetEntity();

    String mappedBy() default "";

    CascadeType[] cascade() default {};

    FetchType fetch() default FetchType.LAZY;
}
```

### **3.2 流式查询DSL**

```java
// 📁 bone-smartmeta-sdk/src/main/java/com/bone/smartmeta/dsl/
package com.bone.smartmeta.dsl;

/**
 * 🔤 智能查询DSL
 */
public class SmartQuery<T> {

    private final Class<T> entityClass;
    private final List<Criterion> criteria = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private PageRequest pageRequest;
    private final AIService aiService;

    private SmartQuery(Class<T> entityClass) {
        this.entityClass = entityClass;
        this.aiService = ApplicationContextHolder.getBean(AIService.class);
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
     * 🔍 模糊查询
     */
    public SmartQuery<T> like(SFunction<T, String> field, String value) {
        criteria.add(Criterion.like(FieldResolver.resolve(field), value));
        return this;
    }

    /**
     * 📈 范围查询
     */
    public <V extends Comparable<V>> SmartQuery<T> between(
            SFunction<T, V> field, V start, V end) {
        criteria.add(Criterion.between(FieldResolver.resolve(field), start, end));
        return this;
    }

    /**
     * 🧠 AI优化查询
     */
    public SmartQuery<T> withAIOptimization() {
        List<Criterion> optimized = aiService.optimizeQuery(criteria, entityClass);
        this.criteria.clear();
        this.criteria.addAll(optimized);
        return this;
    }

    /**
     * 🎯 执行查询
     */
    public List<T> execute() {
        QueryContext context = buildQueryContext();
        return getEntityManager().createQuery(buildJPAQuery(context)).getResultList();
    }

    /**
     * 📄 分页查询
     */
    public PageResult<T> page(int page, int size) {
        this.pageRequest = PageRequest.of(page, size);
        QueryContext context = buildQueryContext();

        TypedQuery<T> query = getEntityManager().createQuery(buildJPAQuery(context));
        query.setFirstResult((int) pageRequest.getOffset());
        query.setMaxResults(pageRequest.getPageSize());

        List<T> content = query.getResultList();
        Long total = executeCountQuery(context);

        return new PageResult<>(content, total, pageRequest);
    }
}
```

## 🎨 **4. Studio 可视化设计器**

### **4.1 前端架构设计**

```typescript
// 📁 bone-smartmeta-studio/web/src/composables/useSmartMeta.ts
import { ref, reactive } from 'vue'
import type { SmartEntity, SmartField, GenerationResult } from '../types/smartmeta'

/**
 * 🎨 SmartMeta 组合式API
 */
export function useSmartMeta() {
  const currentEntity = ref<SmartEntity>()
  const entities = ref<SmartEntity[]>([])
  const generationResult = ref<GenerationResult>()
  const isGenerating = ref(false)

  // 🎯 加载实体列表
  const loadEntities = async () => {
    try {
      const response = await smartMetaApi.getEntities()
      entities.value = response.data
    } catch (error) {
      console.error('加载实体失败:', error)
      throw error
    }
  }

  // 🆕 创建新实体
  const createEntity = async (entityData: Partial<SmartEntity>) => {
    const newEntity: SmartEntity = {
      name: entityData.name || '',
      tableName: entityData.tableName || '',
      description: entityData.description || '',
      fields: [],
      relations: [],
      businessRules: [],
      extensions: {},
      aiConfig: { enabled: true }
    }

    const response = await smartMetaApi.createEntity(newEntity)
    entities.value.push(response.data)
    currentEntity.value = response.data
    return response.data
  }

  // ➕ 添加字段
  const addField = (field: SmartField) => {
    if (!currentEntity.value) return

    currentEntity.value.fields.push(field)
    updateEntity(currentEntity.value)
  }

  // 🚀 智能生成代码
  const generateCode = async (targets: string[]) => {
    if (!currentEntity.value) return

    isGenerating.value = true
    try {
      const request = {
        entity: currentEntity.value,
        targets,
        options: {
          aiEnhancement: true,
          codeStyle: 'modern',
          optimization: true
        }
      }

      const response = await smartMetaApi.generateCode(request)
      generationResult.value = response.data
      return response.data
    } finally {
      isGenerating.value = false
    }
  }

  // 🧠 AI建议字段
  const getAIFieldSuggestions = async (entity: SmartEntity) => {
    try {
      const response = await smartMetaApi.getAISuggestions(entity)
      return response.data.suggestions
    } catch (error) {
      console.error('获取AI建议失败:', error)
      return []
    }
  }

  return {
    currentEntity,
    entities,
    generationResult,
    isGenerating,
    loadEntities,
    createEntity,
    addField,
    generateCode,
    getAIFieldSuggestions
  }
}
```

### **4.2 模型设计器组件**

```vue
<!-- 📁 bone-smartmeta-studio/web/src/components/ModelDesigner.vue -->
<template>
  <div class="model-designer">
    <!-- 🎯 顶部工具栏 -->
    <div class="designer-toolbar">
      <el-button type="primary" @click="handleCreateEntity">
        <i class="el-icon-plus"></i>
        新建实体
      </el-button>
      <el-button @click="handleAISuggest">
        <i class="el-icon-magic"></i>
        AI智能建议
      </el-button>
      <el-button @click="handleGenerateCode">
        <i class="el-icon-cpu"></i>
        生成代码
      </el-button>
    </div>

    <!-- 📊 设计区域 -->
    <div class="designer-content">
      <!-- 🏗️ 实体列表 -->
      <div class="entity-list-panel">
        <h3>实体列表</h3>
        <el-tree
          :data="entityTree"
          node-key="id"
          :props="treeProps"
          @node-click="handleEntitySelect"
        />
      </div>

      <!-- 📝 实体编辑器 -->
      <div class="entity-editor-panel">
        <el-form v-if="currentEntity" :model="currentEntity" label-width="100px">
          <el-form-item label="实体名称">
            <el-input v-model="currentEntity.name" />
          </el-form-item>

          <el-form-item label="表名">
            <el-input v-model="currentEntity.tableName" />
          </el-form-item>

          <el-form-item label="描述">
            <el-input
              v-model="currentEntity.description"
              type="textarea"
              :rows="3"
            />
          </el-form-item>

          <!-- 📊 字段列表 -->
          <el-form-item label="字段列表">
            <div class="field-list">
              <div
                v-for="field in currentEntity.fields"
                :key="field.name"
                class="field-item"
              >
                <el-tag>{{ field.type }}</el-tag>
                <span class="field-name">{{ field.name }}</span>
                <span class="field-comment">{{ field.comment }}</span>
                <el-button
                  size="mini"
                  type="danger"
                  @click="removeField(field)"
                >
                  删除
                </el-button>
              </div>
            </div>

            <el-button @click="showFieldDialog = true">
              添加字段
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 👁️ 实时预览 -->
      <div class="preview-panel">
        <h3>代码预览</h3>
        <CodePreview :code="previewCode" language="java" />
      </div>
    </div>

    <!-- 🆕 字段添加对话框 -->
    <el-dialog
      v-model="showFieldDialog"
      title="添加字段"
      width="600px"
    >
      <FieldEditor @submit="handleFieldSubmit" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import CodePreview from './CodePreview.vue'
import FieldEditor from './FieldEditor.vue'
import { useSmartMeta } from '../composables/useSmartMeta'

const {
  currentEntity,
  entities,
  generationResult,
  createEntity,
  addField,
  generateCode
} = useSmartMeta()

const showFieldDialog = ref(false)
const previewCode = ref('')

// 🌳 实体树数据
const entityTree = computed(() => {
  return entities.value.map(entity => ({
    id: entity.name,
    label: entity.name,
    data: entity
  }))
})

// 🎯 处理实体选择
const handleEntitySelect = (node: any) => {
  currentEntity.value = node.data
  updatePreview()
}

// 🆕 创建实体
const handleCreateEntity = async () => {
  const name = prompt('请输入实体名称:')
  if (name) {
    await createEntity({ name, tableName: `t_${name.toLowerCase()}` })
    ElMessage.success('实体创建成功')
  }
}

// ➕ 处理字段提交
const handleFieldSubmit = (field: any) => {
  addField(field)
  showFieldDialog.value = false
  updatePreview()
}

// 🚀 生成代码
const handleGenerateCode = async () => {
  const result = await generateCode(['java', 'vue', 'sql'])
  ElMessage.success('代码生成成功')
  previewCode.value = result.generatedCode['java'] || ''
}

// 🧠 AI智能建议
const handleAISuggest = async () => {
  // AI建议逻辑
}

// 👁️ 更新预览
const updatePreview = () => {
  if (currentEntity.value) {
    // 生成预览代码
    previewCode.value = generatePreviewCode(currentEntity.value)
  }
}

const treeProps = {
  children: 'children',
  label: 'label'
}
</script>

<style scoped>
.model-designer {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.designer-toolbar {
  padding: 16px;
  border-bottom: 1px solid #e4e7ed;
  background: #f5f7fa;
}

.designer-content {
  flex: 1;
  display: grid;
  grid-template-columns: 250px 1fr 300px;
  gap: 16px;
  padding: 16px;
}

.entity-list-panel,
.entity-editor-panel,
.preview-panel {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 16px;
  background: white;
}

.field-list {
  margin-bottom: 16px;
}

.field-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  margin-bottom: 8px;
}

.field-name {
  font-weight: bold;
}

.field-comment {
  color: #909399;
  flex: 1;
}
</style>
```

## ⚡ **5. Spring Boot Starter 实现**

### **5.1 自动配置类**

```java
// 📁 bone-smartmeta-starter/src/main/java/com/bone/smartmeta/autoconfigure/
package com.bone.smartmeta.autoconfigure;

/**
 * ⚡ SmartMeta 自动配置
 */
@Configuration
@ConditionalOnClass(SmartMetaEngine.class)
@EnableConfigurationProperties(SmartMetaProperties.class)
@AutoConfigureAfter({DataSourceAutoConfiguration.class, WebMvcAutoConfiguration.class})
public class SmartMetaAutoConfiguration {

    private final SmartMetaProperties properties;

    public SmartMetaAutoConfiguration(SmartMetaProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public MetadataRegistry metadataRegistry() {
        return new SmartMetadataRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public SmartCodeGenerator codeGenerator(
            MetadataRegistry registry,
            ObjectProvider<TemplateEngine> templateEngine,
            ObjectProvider<AIService> aiService) {
        return new SmartCodeGenerator(
            templateEngine.getIfAvailable(DefaultTemplateEngine::new),
            aiService.getIfAvailable(DefaultAIService::new),
            registry
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public SmartMetaController smartMetaController(MetadataRegistry registry) {
        return new SmartMetaController(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public SmartMetaEndpoint smartMetaEndpoint(MetadataRegistry registry) {
        return new SmartMetaEndpoint(registry);
    }

    /**
     * 🎯 配置智能实体扫描
     */
    @Bean
    public static SmartEntityScanner smartEntityScanner() {
        return new SmartEntityScanner();
    }
}

/**
 * 📋 配置属性
 */
@ConfigurationProperties(prefix = "bone.smartmeta")
@Data
public class SmartMetaProperties {

    /**
     * 是否启用SmartMeta
     */
    private boolean enabled = true;

    /**
     * 实体扫描包路径
     */
    private String[] entityPackages = {};

    /**
     * 代码生成配置
     */
    private Generation generation = new Generation();

    /**
     * AI增强配置
     */
    private AI ai = new AI();

    @Data
    public static class Generation {
        private String outputDir = "generated-sources";
        private boolean overwrite = false;
        private String templatePath = "classpath:/templates";
        private boolean autoSync = true;
    }

    @Data
    public static class AI {
        private boolean enabled = true;
        private String model = "gpt-3.5-turbo";
        private double temperature = 0.7;
        private String apiKey;
        private String baseUrl;
    }
}
```

### **5.2 启用注解**

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
}
```

## 💻 **6. CLI 命令行工具**

### **6.1 核心命令实现**

```javascript
// 📁 bone-smartmeta-cli/src/commands/generate.js
const { Command } = require('commander');
const { generateCode } = require('../services/codegen-service');
const { loadConfig } = require('../utils/config-loader');
const { logger } = require('../utils/logger');

/**
 * 🚀 代码生成命令
 */
class GenerateCommand {

  constructor() {
    this.command = new Command('generate');
    this.setupOptions();
    this.setupAction();
  }

  setupOptions() {
    this.command
      .description('智能生成代码')
      .option('-m, --model <model>', '模型文件或模型名称')
      .option('-t, --target <targets...>', '生成目标 (java, vue, sql, mobile)')
      .option('-o, --output <dir>', '输出目录', './generated')
      .option('--ai', '启用AI增强', true)
      .option('--preview', '预览模式，不实际生成文件', false)
      .option('--config <file>', '配置文件路径', './smartmeta.config.js');
  }

  setupAction() {
    this.command.action(async (options) => {
      try {
        await this.execute(options);
      } catch (error) {
        logger.error(`生成失败: ${error.message}`);
        process.exit(1);
      }
    });
  }

  async execute(options) {
    logger.info('🚀 开始智能代码生成...');

    // 1. 📋 加载配置
    const config = await loadConfig(options.config);

    // 2. 🎯 准备生成请求
    const request = {
      model: await this.resolveModel(options.model),
      targets: options.target || config.defaultTargets,
      outputDir: options.output,
      options: {
        aiEnhancement: options.ai,
        preview: options.preview,
        codeStyle: config.codeStyle || 'modern',
        optimization: true
      }
    };

    // 3. ⚡ 执行生成
    const result = await generateCode(request);

    // 4. 📊 输出结果
    this.printResult(result, options);

    if (result.success) {
      logger.success('✅ 代码生成完成!');
    } else {
      logger.warn('⚠️ 生成完成，但有警告信息');
    }
  }

  async resolveModel(modelSpec) {
    if (modelSpec.endsWith('.json') || modelSpec.endsWith('.yaml')) {
      // 从文件加载模型
      return await this.loadModelFromFile(modelSpec);
    } else {
      // 从注册中心获取模型
      return await this.getModelFromRegistry(modelSpec);
    }
  }

  printResult(result, options) {
    if (options.preview) {
      logger.info('📝 预览模式 - 生成代码:');
      Object.entries(result.generatedCode).forEach(([target, code]) => {
        logger.info(`\n--- ${target.toUpperCase()} ---\n${code}`);
      });
    } else {
      logger.info('📁 生成文件:');
      result.generatedFiles.forEach(file => {
        logger.info(`  ${file.path} (${file.size} bytes)`);
      });

      if (result.aiSuggestions && result.aiSuggestions.length > 0) {
        logger.info('\n💡 AI优化建议:');
        result.aiSuggestions.forEach(suggestion => {
          logger.info(`  • ${suggestion}`);
        });
      }
    }

    if (result.qualityReport) {
      logger.info(`\n📊 质量报告: ${result.qualityReport.score}/100`);
    }
  }

  getCommand() {
    return this.command;
  }
}

module.exports = GenerateCommand;
```

### **6.2 项目初始化命令**

```javascript
// 📁 bone-smartmeta-cli/src/commands/init.js
const { Command } = require('commender');
const fs = require('fs-extra');
const path = require('path');
const { logger } = require('../utils/logger');

class InitCommand {

  constructor() {
    this.command = new Command('init');
    this.setupOptions();
    this.setupAction();
  }

  setupOptions() {
    this.command
      .description('初始化 SmartMeta 项目')
      .argument('<project-name>', '项目名称')
      .option('-t, --template <template>', '项目模板', 'spring-boot')
      .option('--package <package>', 'Java包名')
      .option('--database <db>', '数据库类型', 'mysql')
      .option('--frontend <fe>', '前端框架', 'vue3')
      .option('-f, --force', '强制覆盖现有目录', false);
  }

  setupAction() {
    this.command.action(async (projectName, options) => {
      try {
        await this.execute(projectName, options);
      } catch (error) {
        logger.error(`初始化失败: ${error.message}`);
        process.exit(1);
      }
    });
  }

  async execute(projectName, options) {
    const projectPath = path.resolve(process.cwd(), projectName);

    // 检查目录是否存在
    if (await fs.pathExists(projectPath)) {
      if (!options.force) {
        throw new Error(`目录 ${projectName} 已存在，使用 -f 强制覆盖`);
      }
      await fs.remove(projectPath);
    }

    logger.info(`🎯 初始化项目: ${projectName}`);

    // 创建项目结构
    await this.createProjectStructure(projectPath, projectName, options);

    // 生成配置文件
    await this.generateConfigFiles(projectPath, projectName, options);

    // 安装依赖
    await this.installDependencies(projectPath, options);

    logger.success(`✅ 项目 ${projectName} 初始化完成!`);
    logger.info(`📁 项目路径: ${projectPath}`);
    logger.info('🚀 开始开发:');
    logger.info(`  cd ${projectName}`);
    logger.info('  smartmeta studio');
  }

  async createProjectStructure(projectPath, projectName, options) {
    const dirs = [
      'src/main/java',
      'src/main/resources',
      'src/test/java',
      'src/main/frontend',
      'models',
      'generated'
    ];

    for (const dir of dirs) {
      await fs.ensureDir(path.join(projectPath, dir));
    }

    logger.debug('📁 创建项目目录结构完成');
  }

  async generateConfigFiles(projectPath, projectName, options) {
    // 生成 smartmeta.config.js
    const config = {
      project: projectName,
      package: options.package || `com.example.${projectName.toLowerCase()}`,
      database: {
        type: options.database,
        url: `jdbc:${options.database}://localhost:3306/${projectName}`,
        username: 'root',
        password: 'password'
      },
      generation: {
        targets: ['java', 'vue', 'sql'],
        outputDir: './generated',
        aiEnhancement: true
      },
      server: {
        port: 8080
      }
    };

    await fs.writeJson(
      path.join(projectPath, 'smartmeta.config.js'),
      `module.exports = ${JSON.stringify(config, null, 2)}`,
      'utf8'
    );

    // 生成 README.md
    const readme = this.generateReadme(projectName, options);
    await fs.writeFile(path.join(projectPath, 'README.md'), readme);

    logger.debug('📄 生成配置文件完成');
  }

  generateReadme(projectName, options) {
    return `# ${projectName}

基于 Bone SmartMeta Engine 构建的智能应用。

## 🚀 快速开始

### 环境要求
- Java 17+
- Node.js 16+
- ${options.database} 数据库

### 开发命令

\`\`\`bash
# 启动设计器
smartmeta studio

# 生成代码
smartmeta generate --model user --target java,vue

# 启动应用
./mvnw spring-boot:run
\`\`\`

## 📁 项目结构

\`\`\`
${projectName}/
├── models/           # 数据模型定义
├── generated/        # 生成的代码
├── src/main/java/    # Java 源代码
└── src/main/frontend/# 前端代码
\`\`\`

## 💡 特性

- ✅ 智能代码生成
- ✅ AI增强开发
- ✅ 多端代码同步
- ✅ 实时预览
\`\`\`
  `;
  }
}

module.exports = InitCommand;
```

## 📚 **7. 使用示例和最佳实践**

### **7.1 快速开始示例**

```java
// 📁 bone-smartmeta-examples/quickstart/src/main/java/com/example/QuickStartApplication.java
@SpringBootApplication
@EnableSmartMeta
public class QuickStartApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuickStartApplication.class, args);
    }
}

/**
 * 🏗️ 用户实体示例
 */
@SmartEntity(
    name = "User",
    tableName = "t_user",
    description = "用户信息实体",
    aiEnhanced = true
)
@Data
public class User {

    @SmartField(
        type = FieldType.LONG,
        required = true,
        comment = "用户ID"
    )
    private Long id;

    @SmartField(
        type = FieldType.STRING,
        length = 50,
        required = true,
        unique = true,
        comment = "用户名"
    )
    private String username;

    @SmartField(
        type = FieldType.STRING,
        length = 100,
        required = true,
        comment = "邮箱地址"
    )
    private String email;

    @SmartField(
        type = FieldType.INTEGER,
        comment = "用户年龄"
    )
    private Integer age;

    @SmartRelation(
        type = RelationType.ONE_TO_MANY,
        targetEntity = "Order"
    )
    private List<Order> orders;
}

/**
 * 🔍 智能查询示例
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final SmartQueryFactory queryFactory;

    public List<User> findActiveAdults() {
        return queryFactory.create(User.class)
            .eq(User::getStatus, Status.ACTIVE)
            .gte(User::getAge, 18)
            .orderBy(User::getCreateTime, Sort.Direction.DESC)
            .withAIOptimization()
            .execute();
    }

    public PageResult<User> searchUsers(UserQuery query) {
        return queryFactory.create(User.class)
            .like(User::getUsername, query.getKeyword())
            .between(User::getCreateTime, query.getStartTime(), query.getEndTime())
            .page(query.getPage(), query.getSize());
    }
}
```

### **7.2 配置文件示例**

```yaml
# 📁 application.yml
bone:
  smartmeta:
    enabled: true
    entity-packages:
      - "com.example.models"
    generation:
      output-dir: "generated-sources"
      auto-sync: true
      overwrite: false
    ai:
      enabled: true
      model: "gpt-4"
      api-key: "${OPENAI_API_KEY}"
      base-url: "https://api.openai.com/v1"

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/smartmeta_demo
    username: root
    password: password
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

logging:
  level:
    com.bone.smartmeta: DEBUG
```

## 🚀 **8. 部署和运维**

### **8.1 Docker 部署配置**

```dockerfile
# 📁 Dockerfile
FROM openjdk:17-jdk-slim as builder

WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

FROM openjdk:17-jdk-slim

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

# 创建非root用户
RUN useradd -m -s /bin/bash smartmeta
USER smartmeta

EXPOSE 8080
EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### **8.2 Kubernetes 部署配置**

```yaml
# 📁 k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: smartmeta-engine
  labels:
    app: smartmeta-engine
spec:
  replicas: 3
  selector:
    matchLabels:
      app: smartmeta-engine
  template:
    metadata:
      labels:
        app: smartmeta-engine
    spec:
      containers:
      - name: smartmeta
        image: bone/smartmeta-engine:2.0.0
        ports:
        - containerPort: 8080
        - containerPort: 3000
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: BONE_SMARTMETA_AI_APIKEY
          valueFrom:
            secretKeyRef:
              name: smartmeta-secrets
              key: openai-apikey
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: smartmeta-service
spec:
  selector:
    app: smartmeta-engine
  ports:
  - name: http
    port: 8080
    targetPort: 8080
  - name: studio
    port: 3000
    targetPort: 3000
  type: LoadBalancer
```

## 📊 **9. 监控和指标**

### **9.1 健康检查和指标**

```java
// 📁 bone-smartmeta-engine/src/main/java/com/bone/smartmeta/core/monitoring/
package com.bone.smartmeta.core.monitoring;

/**
 * 📈 SmartMeta 健康检查
 */
@Component
public class SmartMetaHealthIndicator implements HealthIndicator {

    private final MetadataRegistry registry;
    private final SmartCodeGenerator generator;

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        // 检查实体注册状态
        int entityCount = registry.getAllEntities().size();
        builder.withDetail("entities.registered", entityCount);

        // 检查生成器状态
        builder.withDetail("generator.ready", generator.isReady());

        // 检查AI服务状态
        builder.withDetail("ai.enabled", isAIEnabled());

        return builder.build();
    }
}

/**
 * 📊 SmartMeta 指标收集
 */
@Component
public class SmartMetaMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter generationCounter;
    private final Timer generationTimer;

    public SmartMetaMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.generationCounter = Counter.builder("smartmeta.generation.requests")
            .description("代码生成请求计数")
            .register(meterRegistry);

        this.generationTimer = Timer.builder("smartmeta.generation.duration")
            .description("代码生成耗时")
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
}
```

## 🎯 **总结**

这个完整的 **Bone SmartMeta 智能元数据引擎** 方案提供了：

### **核心优势**
1. **🎯 智能驱动** - AI增强的代码生成和优化
2. **🚀 高效开发** - 从建模到部署的全链路支持
3. **🎨 可视化设计** - 直观的拖拽式模型设计
4. **🔧 企业级特性** - 监控、安全、扩展性
5. **🌐 全栈支持** - Java、Vue、数据库、移动端

### **技术特色**
1. **响应式架构** - 基于 Project Reactor 的高性能处理
2. **模块化设计** - 清晰的职责分离和扩展点
3. **云原生支持** - Docker、Kubernetes 就绪
4. **开发者友好** - 丰富的工具链和文档

### **立即开始**
```bash
# 安装 CLI
npm install -g @bone/smartmeta-cli

# 创建项目
smartmeta init my-project --template spring-boot

# 启动设计器
smartmeta studio

# 开始智能开发！
```

这个方案为企业级应用开发提供了革命性的效率提升，让开发者能够专注于业务逻辑而非重复的编码工作。