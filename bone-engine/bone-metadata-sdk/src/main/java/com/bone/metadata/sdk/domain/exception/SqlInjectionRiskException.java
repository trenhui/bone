package com.bone.metadata.sdk.domain.exception;

import java.util.Arrays;
import java.util.List;

/** SQL注入风险异常 当检测到潜在的SQL注入风险时抛出 */
public class SqlInjectionRiskException extends SDKException {
  private static final long serialVersionUID = 1L;

  // 触发风险检测的SQL语句片段
  private String suspiciousSqlFragment;
  // 检测到的注入关键词
  private List<String> detectedKeywords;
  // 风险级别 (HIGH, MEDIUM, LOW)
  private String riskLevel;

  /**
   * 创建SQL注入风险异常
   *
   * @param message 异常消息
   */
  public SqlInjectionRiskException(String message) {
    super("SQL_INJECTION_RISK", message);
  }

  /**
   * 创建SQL注入风险异常
   *
   * @param message 异常消息
   * @param cause 根本原因
   */
  public SqlInjectionRiskException(String message, Throwable cause) {
    super("SQL_INJECTION_RISK", message, cause);
  }

  /**
   * 创建SQL注入风险异常
   *
   * @param message 异常消息
   * @param suspiciousFragment 可疑的SQL片段
   * @param keywords 检测到的关键词
   */
  public SqlInjectionRiskException(String message, String suspiciousFragment, String... keywords) {
    super("SQL_INJECTION_RISK", message);
    this.suspiciousSqlFragment = suspiciousFragment;
    this.detectedKeywords = Arrays.asList(keywords);
    addContext("suspiciousFragment", suspiciousFragment);
    addContext("detectedKeywords", detectedKeywords);
  }

  /**
   * 获取可疑的SQL片段
   *
   * @return SQL片段
   */
  public String getSuspiciousSqlFragment() {
    return suspiciousSqlFragment;
  }

  /**
   * 设置可疑的SQL片段
   *
   * @param fragment SQL片段
   * @return 当前异常实例
   */
  public SqlInjectionRiskException setSuspiciousSqlFragment(String fragment) {
    this.suspiciousSqlFragment = fragment;
    addContext("suspiciousFragment", fragment);
    return this;
  }

  /**
   * 获取检测到的注入关键词
   *
   * @return 关键词列表
   */
  public List<String> getDetectedKeywords() {
    return detectedKeywords;
  }

  /**
   * 设置检测到的注入关键词
   *
   * @param keywords 关键词列表
   * @return 当前异常实例
   */
  public SqlInjectionRiskException setDetectedKeywords(List<String> keywords) {
    this.detectedKeywords = keywords;
    addContext("detectedKeywords", keywords);
    return this;
  }

  /**
   * 获取风险级别
   *
   * @return 风险级别
   */
  public String getRiskLevel() {
    return riskLevel;
  }

  /**
   * 设置风险级别
   *
   * @param level 风险级别 (HIGH, MEDIUM, LOW)
   * @return 当前异常实例
   */
  public SqlInjectionRiskException setRiskLevel(String level) {
    this.riskLevel = level;
    addContext("riskLevel", level);
    return this;
  }
}
