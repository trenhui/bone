package com.bone.metadata.engine.metadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** 增强的AI元数据模型类 支持多种AI功能配置，包括查询优化、自动建议、异常检测等 */
@Getter
@Setter
public class AiMetadata {

  // 是否启用AI功能
  private boolean enabled = false;

  // AI模型名称
  private String aiModel = "GPT-4";

  // AI描述模板
  private String descriptionTemplate;

  // AI生成字段配置
  private List<AiGeneratedField> generatedFields = new ArrayList<>();

  // AI搜索配置
  private AiSearchConfig searchConfig = new AiSearchConfig();

  // 自定义AI属性
  private Map<String, Object> customProperties = new HashMap<>();

  // 代理式AI配置列表
  private List<AgentMetadata> agenticAI = new ArrayList<>();

  // 建议配置列表
  private List<AgentMetadata> suggestions = new ArrayList<>();

  // AI特征列表
  private List<String> features = new ArrayList<>();

  // AI提示模板
  private Map<String, String> promptTemplates = new HashMap<>();

  // 是否启用AI查询优化
  private boolean aiQueryOptimizationEnabled = true;

  // 是否启用AI自动建议
  private boolean aiAutoSuggestionEnabled = true;

  // 是否启用AI异常检测
  private boolean aiAnomalyDetectionEnabled = false;

  // 是否启用AI预测
  private boolean aiPredictionEnabled = false;

  /** 获取代理式AI配置列表 */
  public List<AgentMetadata> getAgenticAI() {
    return this.agenticAI;
  }

  /** 设置代理式AI配置列表 */
  public void setAgenticAI(List<AgentMetadata> agenticAI) {
    this.agenticAI = agenticAI;
  }

  /** 添加AI代理 */
  public void addAgent(AgentMetadata agent) {
    this.agenticAI.add(agent);
  }

  /** 添加AI特征 */
  public void addFeature(String feature) {
    this.features.add(feature);
  }

  /** 添加AI提示模板 */
  public void addPromptTemplate(String key, String template) {
    this.promptTemplates.put(key, template);
  }

  /** 获取AI建议配置 */
  public List<AgentMetadata> getSuggestions() {
    return this.suggestions;
  }

  /** 设置AI建议配置 */
  public void setSuggestions(List<AgentMetadata> suggestions) {
    this.suggestions = suggestions;
  }

  /** 设置是否启用AI查询优化 */
  public void setAiQueryOptimizationEnabled(boolean enabled) {
    this.aiQueryOptimizationEnabled = enabled;
  }

  /** 设置AI特征列表 */
  public void setAiFeatures(List<String> features) {
    this.features = features;
  }

  /** 设置AI模型名称 */
  public void setAiModel(String aiModel) {
    this.aiModel = aiModel;
  }
}
