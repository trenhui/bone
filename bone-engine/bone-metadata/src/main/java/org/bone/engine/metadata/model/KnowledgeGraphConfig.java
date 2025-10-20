package org.bone.engine.metadata.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 知识图谱配置类 - 用于定义实体在知识图谱中的映射关系和配置
 * 
 * @author Bone Engine Team
 */
public class KnowledgeGraphConfig {
    
    // 知识图谱启用状态
    private boolean enabled = false;
    
    // 实体在知识图谱中的类型标识
    private String entityType;
    
    // 实体在知识图谱中的标签
    private List<String> tags = new ArrayList<>();
    
    // 实体在知识图谱中的属性映射
    private Map<String, PropertyMapping> propertyMappings = new HashMap<>();
    
    // 实体在知识图谱中的关系映射
    private Map<String, RelationshipMapping> relationshipMappings = new HashMap<>();
    
    // 知识图谱索引配置
    private List<GraphIndex> indexes = new ArrayList<>();
    
    // 知识图谱命名空间
    private String namespace;
    
    // 知识图谱属性
    private Map<String, Object> graphProperties = new HashMap<>();
    
    // 嵌入向量配置
    private EmbeddingConfig embeddingConfig;
    
    // 推理规则配置
    private List<InferenceRule> inferenceRules = new ArrayList<>();
    
    // 实体属性配置
    private EntityProperty entityProperty;
    
    // 唯一标识符字段
    private String uniqueIdentifierField;
    
    // 版本字段
    private String versionField;
    
    // 描述字段
    private String descriptionField;
    
    // ========== 内部类定义 ==========
    
    /**
     * 属性映射类 - 定义实体字段到知识图谱属性的映射关系
     */
    public static class PropertyMapping {
        // 实体字段名
        private String fieldName;
        
        // 知识图谱属性名
        private String propertyName;
        
        // 属性数据类型
        private PropertyType propertyType = PropertyType.STRING;
        
        // 属性权重（用于搜索和推荐）
        private double weight = 1.0;
        
        // 是否用于全文检索
        private boolean fullTextSearchable = false;
        
        // 是否用于向量嵌入
        private boolean embeddingField = false;
        
        // 映射转换表达式
        private String transformationExpression;
        
        // 属性分组
        private String propertyGroup;
        
        // 默认值
        private Object defaultValue;
        
        // 多语言配置
        private boolean multilingual = false;
        
        // 索引类型
        private IndexType indexType = IndexType.NONE;
        
        // 构造函数
        public PropertyMapping() {
        }
        
        public PropertyMapping(String fieldName, String propertyName) {
            this.fieldName = fieldName;
            this.propertyName = propertyName;
        }
        
        // Getters and Setters
        public String getFieldName() {
            return fieldName;
        }
        
        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }
        
        public String getPropertyName() {
            return propertyName;
        }
        
        public void setPropertyName(String propertyName) {
            this.propertyName = propertyName;
        }
        
        public PropertyType getPropertyType() {
            return propertyType;
        }
        
        public void setPropertyType(PropertyType propertyType) {
            this.propertyType = propertyType;
        }
        
        public double getWeight() {
            return weight;
        }
        
        public void setWeight(double weight) {
            this.weight = weight;
        }
        
        public boolean isFullTextSearchable() {
            return fullTextSearchable;
        }
        
        public void setFullTextSearchable(boolean fullTextSearchable) {
            this.fullTextSearchable = fullTextSearchable;
        }
        
        public boolean isEmbeddingField() {
            return embeddingField;
        }
        
        public void setEmbeddingField(boolean embeddingField) {
            this.embeddingField = embeddingField;
        }
        
        public String getTransformationExpression() {
            return transformationExpression;
        }
        
        public void setTransformationExpression(String transformationExpression) {
            this.transformationExpression = transformationExpression;
        }
        
        public String getPropertyGroup() {
            return propertyGroup;
        }
        
        public void setPropertyGroup(String propertyGroup) {
            this.propertyGroup = propertyGroup;
        }
        
        public Object getDefaultValue() {
            return defaultValue;
        }
        
        public void setDefaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
        }
        
        public boolean isMultilingual() {
            return multilingual;
        }
        
        public void setMultilingual(boolean multilingual) {
            this.multilingual = multilingual;
        }
        
        public IndexType getIndexType() {
            return indexType;
        }
        
        public void setIndexType(IndexType indexType) {
            this.indexType = indexType;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PropertyMapping that = (PropertyMapping) o;
            return Objects.equals(fieldName, that.fieldName) && 
                   Objects.equals(propertyName, that.propertyName);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(fieldName, propertyName);
        }
        
        @Override
        public String toString() {
            return "PropertyMapping{" +
                   "fieldName='" + fieldName + '\'' +
                   ", propertyName='" + propertyName + '\'' +
                   ", propertyType=" + propertyType +
                   ", weight=" + weight +
                   ", fullTextSearchable=" + fullTextSearchable +
                   ", embeddingField=" + embeddingField +
                   "}";
        }
    }
    
    /**
     * 关系映射类 - 定义实体间关系到知识图谱关系的映射
     */
    public static class RelationshipMapping {
        // 关系名称
        private String relationshipName;
        
        // 知识图谱关系类型
        private String relationshipType;
        
        // 目标实体类型
        private String targetEntityType;
        
        // 源字段
        private String sourceField;
        
        // 目标字段
        private String targetField;
        
        // 关系方向
        private RelationshipDirection direction = RelationshipDirection.OUTGOING;
        
        // 关系权重
        private double weight = 1.0;
        
        // 关系属性
        private Map<String, PropertyMapping> relationshipProperties = new HashMap<>();
        
        // 最小基数
        private int minCardinality = 0;
        
        // 最大基数
        private int maxCardinality = Integer.MAX_VALUE;
        
        // 是否启用推理
        private boolean inferable = false;
        
        // 是否聚合关系
        private boolean aggregated = false;
        
        // 聚合函数
        private String aggregationFunction;
        
        // 构造函数
        public RelationshipMapping() {
        }
        
        public RelationshipMapping(String relationshipName, String relationshipType, String targetEntityType) {
            this.relationshipName = relationshipName;
            this.relationshipType = relationshipType;
            this.targetEntityType = targetEntityType;
        }
        
        // Getters and Setters
        public String getRelationshipName() {
            return relationshipName;
        }
        
        public void setRelationshipName(String relationshipName) {
            this.relationshipName = relationshipName;
        }
        
        public String getRelationshipType() {
            return relationshipType;
        }
        
        public void setRelationshipType(String relationshipType) {
            this.relationshipType = relationshipType;
        }
        
        public String getTargetEntityType() {
            return targetEntityType;
        }
        
        public void setTargetEntityType(String targetEntityType) {
            this.targetEntityType = targetEntityType;
        }
        
        public String getSourceField() {
            return sourceField;
        }
        
        public void setSourceField(String sourceField) {
            this.sourceField = sourceField;
        }
        
        public String getTargetField() {
            return targetField;
        }
        
        public void setTargetField(String targetField) {
            this.targetField = targetField;
        }
        
        public RelationshipDirection getDirection() {
            return direction;
        }
        
        public void setDirection(RelationshipDirection direction) {
            this.direction = direction;
        }
        
        public double getWeight() {
            return weight;
        }
        
        public void setWeight(double weight) {
            this.weight = weight;
        }
        
        public Map<String, PropertyMapping> getRelationshipProperties() {
            return relationshipProperties;
        }
        
        public void setRelationshipProperties(Map<String, PropertyMapping> relationshipProperties) {
            this.relationshipProperties = relationshipProperties;
        }
        
        public void addRelationshipProperty(String name, PropertyMapping mapping) {
            this.relationshipProperties.put(name, mapping);
        }
        
        public int getMinCardinality() {
            return minCardinality;
        }
        
        public void setMinCardinality(int minCardinality) {
            this.minCardinality = minCardinality;
        }
        
        public int getMaxCardinality() {
            return maxCardinality;
        }
        
        public void setMaxCardinality(int maxCardinality) {
            this.maxCardinality = maxCardinality;
        }
        
        public boolean isInferable() {
            return inferable;
        }
        
        public void setInferable(boolean inferable) {
            this.inferable = inferable;
        }
        
        public boolean isAggregated() {
            return aggregated;
        }
        
        public void setAggregated(boolean aggregated) {
            this.aggregated = aggregated;
        }
        
        public String getAggregationFunction() {
            return aggregationFunction;
        }
        
        public void setAggregationFunction(String aggregationFunction) {
            this.aggregationFunction = aggregationFunction;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RelationshipMapping that = (RelationshipMapping) o;
            return Objects.equals(relationshipName, that.relationshipName) &&
                   Objects.equals(relationshipType, that.relationshipType);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(relationshipName, relationshipType);
        }
        
        @Override
        public String toString() {
            return "RelationshipMapping{" +
                   "relationshipName='" + relationshipName + '\'' +
                   ", relationshipType='" + relationshipType + '\'' +
                   ", targetEntityType='" + targetEntityType + '\'' +
                   ", direction=" + direction +
                   "}";
        }
    }
    
    /**
     * 知识图谱索引类
     */
    public static class GraphIndex {
        // 索引名称
        private String name;
        
        // 索引类型
        private IndexType type = IndexType.COMPOSITE;
        
        // 索引属性列表
        private List<String> properties = new ArrayList<>();
        
        // 是否唯一索引
        private boolean unique = false;
        
        // 是否强制索引
        private boolean enforced = false;
        
        // 索引配置
        private Map<String, Object> configuration = new HashMap<>();
        
        // 构造函数
        public GraphIndex() {
        }
        
        public GraphIndex(String name, List<String> properties) {
            this.name = name;
            this.properties = properties;
        }
        
        // Getters and Setters
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public IndexType getType() {
            return type;
        }
        
        public void setType(IndexType type) {
            this.type = type;
        }
        
        public List<String> getProperties() {
            return properties;
        }
        
        public void setProperties(List<String> properties) {
            this.properties = properties;
        }
        
        public void addProperty(String property) {
            this.properties.add(property);
        }
        
        public boolean isUnique() {
            return unique;
        }
        
        public void setUnique(boolean unique) {
            this.unique = unique;
        }
        
        public boolean isEnforced() {
            return enforced;
        }
        
        public void setEnforced(boolean enforced) {
            this.enforced = enforced;
        }
        
        public Map<String, Object> getConfiguration() {
            return configuration;
        }
        
        public void setConfiguration(Map<String, Object> configuration) {
            this.configuration = configuration;
        }
        
        public void addConfiguration(String key, Object value) {
            this.configuration.put(key, value);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GraphIndex that = (GraphIndex) o;
            return Objects.equals(name, that.name);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
        
        @Override
        public String toString() {
            return "GraphIndex{" +
                   "name='" + name + '\'' +
                   ", type=" + type +
                   ", properties=" + properties +
                   ", unique=" + unique +
                   "}";
        }
    }
    
    /**
     * 嵌入向量配置类
     */
    public static class EmbeddingConfig {
        // 嵌入模型名称
        private String modelName;
        
        // 嵌入维度
        private int dimension = 1536;
        
        // 嵌入字段列表
        private List<String> embeddingFields = new ArrayList<>();
        
        // 嵌入向量字段名
        private String embeddingVectorField;
        
        // 嵌入更新策略
        private UpdateStrategy updateStrategy = UpdateStrategy.ON_CREATE;
        
        // 相似度度量方法
        private SimilarityMetric similarityMetric = SimilarityMetric.COSINE;
        
        // 嵌入生成器配置
        private Map<String, Object> generatorConfig = new HashMap<>();
        
        // 是否启用近似最近邻
        private boolean useApproximateNearestNeighbors = true;
        
        // 构造函数
        public EmbeddingConfig() {
        }
        
        // Getters and Setters
        public String getModelName() {
            return modelName;
        }
        
        public void setModelName(String modelName) {
            this.modelName = modelName;
        }
        
        public int getDimension() {
            return dimension;
        }
        
        public void setDimension(int dimension) {
            this.dimension = dimension;
        }
        
        public List<String> getEmbeddingFields() {
            return embeddingFields;
        }
        
        public void setEmbeddingFields(List<String> embeddingFields) {
            this.embeddingFields = embeddingFields;
        }
        
        public void addEmbeddingField(String field) {
            this.embeddingFields.add(field);
        }
        
        public String getEmbeddingVectorField() {
            return embeddingVectorField;
        }
        
        public void setEmbeddingVectorField(String embeddingVectorField) {
            this.embeddingVectorField = embeddingVectorField;
        }
        
        public UpdateStrategy getUpdateStrategy() {
            return updateStrategy;
        }
        
        public void setUpdateStrategy(UpdateStrategy updateStrategy) {
            this.updateStrategy = updateStrategy;
        }
        
        public SimilarityMetric getSimilarityMetric() {
            return similarityMetric;
        }
        
        public void setSimilarityMetric(SimilarityMetric similarityMetric) {
            this.similarityMetric = similarityMetric;
        }
        
        public Map<String, Object> getGeneratorConfig() {
            return generatorConfig;
        }
        
        public void setGeneratorConfig(Map<String, Object> generatorConfig) {
            this.generatorConfig = generatorConfig;
        }
        
        public boolean isUseApproximateNearestNeighbors() {
            return useApproximateNearestNeighbors;
        }
        
        public void setUseApproximateNearestNeighbors(boolean useApproximateNearestNeighbors) {
            this.useApproximateNearestNeighbors = useApproximateNearestNeighbors;
        }
    }
    
    /**
     * 推理规则类
     */
    public static class InferenceRule {
        // 规则名称
        private String name;
        
        // 规则类型
        private RuleType ruleType = RuleType.DEDUCTIVE;
        
        // 规则定义
        private String definition;
        
        // 规则优先级
        private int priority = 0;
        
        // 规则条件
        private String condition;
        
        // 规则动作
        private String action;
        
        // 是否启用
        private boolean enabled = true;
        
        // 构造函数
        public InferenceRule() {
        }
        
        public InferenceRule(String name, String definition) {
            this.name = name;
            this.definition = definition;
        }
        
        // Getters and Setters
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public RuleType getRuleType() {
            return ruleType;
        }
        
        public void setRuleType(RuleType ruleType) {
            this.ruleType = ruleType;
        }
        
        public String getDefinition() {
            return definition;
        }
        
        public void setDefinition(String definition) {
            this.definition = definition;
        }
        
        public int getPriority() {
            return priority;
        }
        
        public void setPriority(int priority) {
            this.priority = priority;
        }
        
        public String getCondition() {
            return condition;
        }
        
        public void setCondition(String condition) {
            this.condition = condition;
        }
        
        public String getAction() {
            return action;
        }
        
        public void setAction(String action) {
            this.action = action;
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
    
    /**
     * 实体属性配置类
     */
    public static class EntityProperty {
        // 实体标签字段
        private List<String> labelFields = new ArrayList<>();
        
        // 实体分类字段
        private List<String> categoryFields = new ArrayList<>();
        
        // 实体权重字段
        private String weightField;
        
        // 实体评分字段
        private String scoreField;
        
        // 实体状态字段
        private String statusField;
        
        // 实体创建时间字段
        private String createdAtField;
        
        // 实体更新时间字段
        private String updatedAtField;
        
        // 实体时间线配置
        private TimelineConfig timelineConfig;
        
        // 构造函数
        public EntityProperty() {
        }
        
        // Getters and Setters
        public List<String> getLabelFields() {
            return labelFields;
        }
        
        public void setLabelFields(List<String> labelFields) {
            this.labelFields = labelFields;
        }
        
        public void addLabelField(String field) {
            this.labelFields.add(field);
        }
        
        public List<String> getCategoryFields() {
            return categoryFields;
        }
        
        public void setCategoryFields(List<String> categoryFields) {
            this.categoryFields = categoryFields;
        }
        
        public void addCategoryField(String field) {
            this.categoryFields.add(field);
        }
        
        public String getWeightField() {
            return weightField;
        }
        
        public void setWeightField(String weightField) {
            this.weightField = weightField;
        }
        
        public String getScoreField() {
            return scoreField;
        }
        
        public void setScoreField(String scoreField) {
            this.scoreField = scoreField;
        }
        
        public String getStatusField() {
            return statusField;
        }
        
        public void setStatusField(String statusField) {
            this.statusField = statusField;
        }
        
        public String getCreatedAtField() {
            return createdAtField;
        }
        
        public void setCreatedAtField(String createdAtField) {
            this.createdAtField = createdAtField;
        }
        
        public String getUpdatedAtField() {
            return updatedAtField;
        }
        
        public void setUpdatedAtField(String updatedAtField) {
            this.updatedAtField = updatedAtField;
        }
        
        public TimelineConfig getTimelineConfig() {
            return timelineConfig;
        }
        
        public void setTimelineConfig(TimelineConfig timelineConfig) {
            this.timelineConfig = timelineConfig;
        }
    }
    
    /**
     * 时间线配置类
     */
    public static class TimelineConfig {
        // 时间线字段
        private String timelineField;
        
        // 时间线事件类型字段
        private String eventTypeField;
        
        // 时间线事件时间字段
        private String eventTimeField;
        
        // 时间线事件描述字段
        private String eventDescriptionField;
        
        // 时间线事件操作者字段
        private String operatorField;
        
        // 构造函数
        public TimelineConfig() {
        }
        
        // Getters and Setters
        public String getTimelineField() {
            return timelineField;
        }
        
        public void setTimelineField(String timelineField) {
            this.timelineField = timelineField;
        }
        
        public String getEventTypeField() {
            return eventTypeField;
        }
        
        public void setEventTypeField(String eventTypeField) {
            this.eventTypeField = eventTypeField;
        }
        
        public String getEventTimeField() {
            return eventTimeField;
        }
        
        public void setEventTimeField(String eventTimeField) {
            this.eventTimeField = eventTimeField;
        }
        
        public String getEventDescriptionField() {
            return eventDescriptionField;
        }
        
        public void setEventDescriptionField(String eventDescriptionField) {
            this.eventDescriptionField = eventDescriptionField;
        }
        
        public String getOperatorField() {
            return operatorField;
        }
        
        public void setOperatorField(String operatorField) {
            this.operatorField = operatorField;
        }
    }
    
    // ========== 枚举定义 ==========
    
    /**
     * 属性数据类型枚举
     */
    public enum PropertyType {
        STRING,
        INTEGER,
        LONG,
        FLOAT,
        DOUBLE,
        BOOLEAN,
        DATE,
        DATETIME,
        TIMESTAMP,
        BINARY,
        ARRAY,
        OBJECT,
        JSON,
        GEOMETRY,
        VECTOR,
        TEXT,
        NUMBER
    }
    
    /**
     * 索引类型枚举
     */
    public enum IndexType {
        NONE,
        COMPOSITE,
        UNIQUE,
        FULLTEXT,
        SPATIAL,
        VECTOR,
        HASH,
        TEXT,
        RANGE
    }
    
    /**
     * 关系方向枚举
     */
    public enum RelationshipDirection {
        OUTGOING,
        INCOMING,
        BOTH
    }
    
    /**
     * 嵌入更新策略枚举
     */
    public enum UpdateStrategy {
        ON_CREATE,
        ON_UPDATE,
        BOTH,
        MANUAL
    }
    
    /**
     * 相似度度量方法枚举
     */
    public enum SimilarityMetric {
        COSINE,
        EUCLIDEAN,
        DOT_PRODUCT,
        JACCARD,
        LEVENSHTEIN
    }
    
    /**
     * 规则类型枚举
     */
    public enum RuleType {
        DEDUCTIVE,
        INDUCTIVE,
        ABDUCTIVE,
        FACT,
        CONSTRAINT
    }
    
    // ========== 主要方法 ==========
    
    /**
     * 添加属性映射
     */
    public void addPropertyMapping(String name, PropertyMapping mapping) {
        this.propertyMappings.put(name, mapping);
    }
    
    /**
     * 添加关系映射
     */
    public void addRelationshipMapping(String name, RelationshipMapping mapping) {
        this.relationshipMappings.put(name, mapping);
    }
    
    /**
     * 添加索引
     */
    public void addIndex(GraphIndex index) {
        this.indexes.add(index);
    }
    
    /**
     * 添加推理规则
     */
    public void addInferenceRule(InferenceRule rule) {
        this.inferenceRules.add(rule);
    }
    
    /**
     * 获取属性映射
     */
    public PropertyMapping getPropertyMapping(String fieldName) {
        return propertyMappings.get(fieldName);
    }
    
    /**
     * 获取关系映射
     */
    public RelationshipMapping getRelationshipMapping(String relationshipName) {
        return relationshipMappings.get(relationshipName);
    }
    
    /**
     * 检查是否有嵌入配置
     */
    public boolean hasEmbeddingConfig() {
        return embeddingConfig != null && 
               embeddingConfig.getModelName() != null && 
               !embeddingConfig.getEmbeddingFields().isEmpty();
    }
    
    /**
     * 获取所有嵌入字段
     */
    public List<String> getAllEmbeddingFields() {
        if (!hasEmbeddingConfig()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(embeddingConfig.getEmbeddingFields());
    }
    
    /**
     * 获取所有全文检索字段
     */
    public List<String> getFullTextSearchableFields() {
        List<String> result = new ArrayList<>();
        for (PropertyMapping mapping : propertyMappings.values()) {
            if (mapping.isFullTextSearchable()) {
                result.add(mapping.getFieldName());
            }
        }
        return result;
    }
    
    // ========== Getters and Setters ==========
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getEntityType() {
        return entityType;
    }
    
    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }
    
    public List<String> getTags() {
        return tags;
    }
    
    public void setTags(List<String> tags) {
        this.tags = tags;
    }
    
    public void addTag(String tag) {
        this.tags.add(tag);
    }
    
    public Map<String, PropertyMapping> getPropertyMappings() {
        return propertyMappings;
    }
    
    public void setPropertyMappings(Map<String, PropertyMapping> propertyMappings) {
        this.propertyMappings = propertyMappings;
    }
    
    public Map<String, RelationshipMapping> getRelationshipMappings() {
        return relationshipMappings;
    }
    
    public void setRelationshipMappings(Map<String, RelationshipMapping> relationshipMappings) {
        this.relationshipMappings = relationshipMappings;
    }
    
    public List<GraphIndex> getIndexes() {
        return indexes;
    }
    
    public void setIndexes(List<GraphIndex> indexes) {
        this.indexes = indexes;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    
    public Map<String, Object> getGraphProperties() {
        return graphProperties;
    }
    
    public void setGraphProperties(Map<String, Object> graphProperties) {
        this.graphProperties = graphProperties;
    }
    
    public void addGraphProperty(String key, Object value) {
        this.graphProperties.put(key, value);
    }
    
    public EmbeddingConfig getEmbeddingConfig() {
        return embeddingConfig;
    }
    
    public void setEmbeddingConfig(EmbeddingConfig embeddingConfig) {
        this.embeddingConfig = embeddingConfig;
    }
    
    public List<InferenceRule> getInferenceRules() {
        return inferenceRules;
    }
    
    public void setInferenceRules(List<InferenceRule> inferenceRules) {
        this.inferenceRules = inferenceRules;
    }
    
    public EntityProperty getEntityProperty() {
        return entityProperty;
    }
    
    public void setEntityProperty(EntityProperty entityProperty) {
        this.entityProperty = entityProperty;
    }
    
    public String getUniqueIdentifierField() {
        return uniqueIdentifierField;
    }
    
    public void setUniqueIdentifierField(String uniqueIdentifierField) {
        this.uniqueIdentifierField = uniqueIdentifierField;
    }
    
    public String getVersionField() {
        return versionField;
    }
    
    public void setVersionField(String versionField) {
        this.versionField = versionField;
    }
    
    public String getDescriptionField() {
        return descriptionField;
    }
    
    public void setDescriptionField(String descriptionField) {
        this.descriptionField = descriptionField;
    }
    
    // ========== Object方法 ==========
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KnowledgeGraphConfig that = (KnowledgeGraphConfig) o;
        return enabled == that.enabled &&
               Objects.equals(entityType, that.entityType) &&
               Objects.equals(namespace, that.namespace) &&
               Objects.equals(propertyMappings, that.propertyMappings) &&
               Objects.equals(relationshipMappings, that.relationshipMappings);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(enabled, entityType, namespace, propertyMappings, relationshipMappings);
    }
    
    @Override
    public String toString() {
        return "KnowledgeGraphConfig{" +
               "enabled=" + enabled +
               ", entityType='" + entityType + '\'' +
               ", namespace='" + namespace + '\'' +
               ", propertyMappings.size=" + propertyMappings.size() +
               ", relationshipMappings.size=" + relationshipMappings.size() +
               ", hasEmbeddingConfig=" + hasEmbeddingConfig() +
               "}";
    }
}