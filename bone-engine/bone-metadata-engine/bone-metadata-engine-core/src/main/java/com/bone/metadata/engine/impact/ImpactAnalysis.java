package com.bone.metadata.engine.impact;

import java.util.List;
import java.util.Map;

/** 元数据影响分析结果类 用于存储元数据变更的影响分析结果 */
public class ImpactAnalysis {

  private boolean breakingChange;
  private List<String> affectedEntities;
  private List<String> affectedFields;
  private List<String> potentialIssues;
  private Map<String, String> recommendations;

  // 构造方法
  public ImpactAnalysis() {
    this.breakingChange = false;
  }

  // getter和setter方法
  public boolean isBreakingChange() {
    return breakingChange;
  }

  public void setBreakingChange(boolean breakingChange) {
    this.breakingChange = breakingChange;
  }

  public List<String> getAffectedEntities() {
    return affectedEntities;
  }

  public void setAffectedEntities(List<String> affectedEntities) {
    this.affectedEntities = affectedEntities;
  }

  public List<String> getAffectedFields() {
    return affectedFields;
  }

  public void setAffectedFields(List<String> affectedFields) {
    this.affectedFields = affectedFields;
  }

  public List<String> getPotentialIssues() {
    return potentialIssues;
  }

  public void setPotentialIssues(List<String> potentialIssues) {
    this.potentialIssues = potentialIssues;
  }

  public Map<String, String> getRecommendations() {
    return recommendations;
  }

  public void setRecommendations(Map<String, String> recommendations) {
    this.recommendations = recommendations;
  }
}
