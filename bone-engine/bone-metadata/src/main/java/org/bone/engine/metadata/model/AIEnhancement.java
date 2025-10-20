package org.bone.engine.metadata.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI增强配置模型 - 定义实体的AI增强相关配置
 * 支持智能标签、自动分类、推荐系统、异常检测等AI能力
 * 
 * @author Bone Engine Team
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AIEnhancement {

    // ================ 核心属性 ================
    
    /**
     * 是否启用AI增强
     */
    private Boolean enabled;
    
    /**
     * 智能标签配置
     */
    private List<SmartTagConfig> smartTags;
    
    /**
     * 自动分类配置
     */
    private AutoClassificationConfig autoClassification;
    
    /**
     * 推荐系统配置
     */
    private RecommendationConfig recommendation;
    
    /**
     * 异常检测配置
     */
    private AnomalyDetectionConfig anomalyDetection;
    
    /**
     * 智能填充配置
     */
    private List<AutoFillConfig> autoFillFields;
    
    /**
     * 自然语言处理配置
     */
    private NLPConfig nlpConfig;
    
    /**
     * 知识图谱配置
     */
    private KnowledgeGraphConfig kgConfig;
    
    /**
     * AI模型配置
     */
    private AIModelConfig modelConfig;
    
    /**
     * 触发配置
     */
    private TriggerConfig triggerConfig;
    
    /**
     * 扩展配置
     */
    private Map<String, Object> extensions;
    
    // ================ 构造方法与辅助方法 ================
    
    public AIEnhancement() {
        this.enabled = Boolean.FALSE;
        this.smartTags = new ArrayList<>();
        this.autoClassification = new AutoClassificationConfig();
        this.recommendation = new RecommendationConfig();
        this.anomalyDetection = new AnomalyDetectionConfig();
        this.autoFillFields = new ArrayList<>();
        this.nlpConfig = new NLPConfig();
        this.kgConfig = new KnowledgeGraphConfig();
        this.modelConfig = new AIModelConfig();
        this.triggerConfig = new TriggerConfig();
        this.extensions = new HashMap<>();
    }
    
    /**
     * 添加智能标签配置
     */
    public AIEnhancement addSmartTag(SmartTagConfig tagConfig) {
        if (this.smartTags == null) {
            this.smartTags = new ArrayList<>();
        }
        this.smartTags.add(tagConfig);
        return this;
    }
    
    /**
     * 添加自动填充配置
     */
    public AIEnhancement addAutoFillField(AutoFillConfig autoFillConfig) {
        if (this.autoFillFields == null) {
            this.autoFillFields = new ArrayList<>();
        }
        this.autoFillFields.add(autoFillConfig);
        return this;
    }
    
    /**
     * 检查是否启用智能标签
     */
    public boolean hasSmartTagsEnabled() {
        return this.enabled && this.smartTags != null && !this.smartTags.isEmpty();
    }
    
    /**
     * 检查是否启用自动分类
     */
    public boolean hasAutoClassificationEnabled() {
        return this.enabled && this.autoClassification != null && this.autoClassification.getEnabled();
    }
    
    /**
     * 检查是否启用推荐系统
     */
    public boolean hasRecommendationEnabled() {
        return this.enabled && this.recommendation != null && this.recommendation.getEnabled();
    }
    
    /**
     * 检查是否启用异常检测
     */
    public boolean hasAnomalyDetectionEnabled() {
        return this.enabled && this.anomalyDetection != null && this.anomalyDetection.getEnabled();
    }
    
    // ================ 内部类定义 ================
    
    /**
     * 智能标签配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SmartTagConfig {
        
        /**
         * 标签名称
         */
        private String name;
        
        /**
         * 标签字段
         */
        private String targetField;
        
        /**
         * 源字段
         */
        private List<String> sourceFields;
        
        /**
         * 置信度阈值
         */
        private Double confidenceThreshold;
        
        /**
         * 是否自动应用
         */
        private Boolean autoApply;
        
        public SmartTagConfig() {
            this.sourceFields = new ArrayList<>();
            this.confidenceThreshold = 0.7;
            this.autoApply = Boolean.TRUE;
        }
    }
    
    /**
     * 自动分类配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AutoClassificationConfig {
        
        /**
         * 是否启用
         */
        private Boolean enabled;
        
        /**
         * 目标字段
         */
        private String targetField;
        
        /**
         * 分类模型
         */
        private String modelName;
        
        /**
         * 源字段
         */
        private List<String> sourceFields;
        
        /**
         * 置信度阈值
         */
        private Double confidenceThreshold;
        
        public AutoClassificationConfig() {
            this.enabled = Boolean.FALSE;
            this.sourceFields = new ArrayList<>();
            this.confidenceThreshold = 0.8;
        }
    }
    
    /**
     * 推荐系统配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RecommendationConfig {
        
        /**
         * 是否启用
         */
        private Boolean enabled;
        
        /**
         * 推荐类型
         */
        private String recommendationType;
        
        /**
         * 推荐模型
         */
        private String modelName;
        
        /**
         * 推荐参数
         */
        private Map<String, Object> parameters;
        
        /**
         * 推荐数量
         */
        private Integer recommendationCount;
        
        public RecommendationConfig() {
            this.enabled = Boolean.FALSE;
            this.recommendationType = "ITEM_BASED";
            this.parameters = new HashMap<>();
            this.recommendationCount = 5;
        }
    }
    
    /**
     * 异常检测配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AnomalyDetectionConfig {
        
        /**
         * 是否启用
         */
        private Boolean enabled;
        
        /**
         * 检测模型
         */
        private String modelName;
        
        /**
         * 监控字段
         */
        private List<String> monitoredFields;
        
        /**
         * 检测阈值
         */
        private Double detectionThreshold;
        
        /**
         * 告警配置
         */
        private Map<String, Object> alertConfig;
        
        public AnomalyDetectionConfig() {
            this.enabled = Boolean.FALSE;
            this.monitoredFields = new ArrayList<>();
            this.detectionThreshold = 0.95;
            this.alertConfig = new HashMap<>();
        }
    }
    
    /**
     * 自动填充配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AutoFillConfig {
        
        /**
         * 目标字段
         */
        private String targetField;
        
        /**
         * 源字段
         */
        private List<String> sourceFields;
        
        /**
         * 填充条件
         */
        private String condition;
        
        /**
         * 是否覆盖现有值
         */
        private Boolean overrideExisting;
        
        public AutoFillConfig() {
            this.sourceFields = new ArrayList<>();
            this.overrideExisting = Boolean.FALSE;
        }
    }
    
    /**
     * NLP配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NLPConfig {
        
        /**
         * 是否启用
         */
        private Boolean enabled;
        
        /**
         * 处理字段
         */
        private List<String> textFields;
        
        /**
         * 处理类型
         */
        private List<String> processingTypes;
        
        /**
         * 语言
         */
        private String language;
        
        public NLPConfig() {
            this.enabled = Boolean.FALSE;
            this.textFields = new ArrayList<>();
            this.processingTypes = new ArrayList<>();
            this.language = "zh";
        }
    }
    
    /**
     * 知识图谱配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class KnowledgeGraphConfig {
        
        /**
         * 是否启用
         */
        private Boolean enabled;
        
        /**
         * 实体映射
         */
        private Map<String, String> entityMappings;
        
        /**
         * 关系映射
         */
        private Map<String, String> relationshipMappings;
        
        /**
         * 图数据库配置
         */
        private Map<String, Object> graphDbConfig;
        
        public KnowledgeGraphConfig() {
            this.enabled = Boolean.FALSE;
            this.entityMappings = new HashMap<>();
            this.relationshipMappings = new HashMap<>();
            this.graphDbConfig = new HashMap<>();
        }
    }
    
    /**
     * AI模型配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AIModelConfig {
        
        /**
         * 模型提供方
         */
        private String provider;
        
        /**
         * 模型名称
         */
        private String modelName;
        
        /**
         * API密钥配置
         */
        private Map<String, String> apiConfig;
        
        /**
         * 部署配置
         */
        private Map<String, Object> deploymentConfig;
        
        public AIModelConfig() {
            this.apiConfig = new HashMap<>();
            this.deploymentConfig = new HashMap<>();
        }
    }
    
    /**
     * 触发配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TriggerConfig {
        
        /**
         * 触发事件
         */
        private List<String> triggerEvents;
        
        /**
         * 定时触发配置
         */
        private String cronExpression;
        
        /**
         * 批量处理配置
         */
        private Map<String, Object> batchConfig;
        
        public TriggerConfig() {
            this.triggerEvents = new ArrayList<>();
            this.batchConfig = new HashMap<>();
        }
    }
}