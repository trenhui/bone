package com.bone.metadata.engine.rule;

import com.bone.metadata.engine.model.BusinessRuleMetadata;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** 业务规则注册表接口 用于管理和检索业务规则元数据 */
public interface BusinessRuleRegistry {

  /**
   * 注册业务规则
   *
   * @param rule 业务规则元数据
   */
  void registerRule(BusinessRuleMetadata rule);

  /**
   * 批量注册业务规则
   *
   * @param rules 业务规则元数据列表
   */
  void registerRules(List<BusinessRuleMetadata> rules);

  /**
   * 更新业务规则
   *
   * @param rule 业务规则元数据
   * @return 更新是否成功
   */
  boolean updateRule(BusinessRuleMetadata rule);

  /**
   * 注销业务规则
   *
   * @param ruleId 规则ID
   * @return 注销是否成功
   */
  boolean unregisterRule(String ruleId);

  /**
   * 按ID获取业务规则
   *
   * @param ruleId 规则ID
   * @return 业务规则元数据，不存在则返回null
   */
  BusinessRuleMetadata getRuleById(String ruleId);

  /**
   * 获取实体相关的所有业务规则
   *
   * @param entityApiName 实体API名称
   * @return 业务规则元数据列表
   */
  List<BusinessRuleMetadata> getRulesByEntity(String entityApiName);

  /**
   * 获取特定事件触发的业务规则
   *
   * @param entityApiName 实体API名称
   * @param eventType 事件类型
   * @return 业务规则元数据列表
   */
  List<BusinessRuleMetadata> getRulesByEvent(String entityApiName, String eventType);

  /**
   * 根据规则类型获取业务规则
   *
   * @param entityApiName 实体API名称
   * @param ruleType 规则类型
   * @return 业务规则元数据列表
   */
  List<BusinessRuleMetadata> getRulesByType(String entityApiName, String ruleType);

  /**
   * 搜索业务规则
   *
   * @param predicate 过滤条件
   * @return 业务规则元数据列表
   */
  List<BusinessRuleMetadata> searchRules(Predicate<BusinessRuleMetadata> predicate);

  /**
   * 获取所有实体API名称
   *
   * @return 实体API名称集合
   */
  Set<String> getAllEntityApiNames();

  /**
   * 检查规则ID是否存在
   *
   * @param ruleId 规则ID
   * @return 是否存在
   */
  boolean exists(String ruleId);

  /** 清空注册表 */
  void clear();

  /**
   * 获取注册表中的规则数量
   *
   * @return 规则数量
   */
  int size();

  /**
   * 添加规则变更监听器
   *
   * @param listener 监听器
   */
  void addRuleChangeListener(BusinessRuleChangeListener listener);

  /**
   * 移除规则变更监听器
   *
   * @param listener 监听器
   */
  void removeRuleChangeListener(BusinessRuleChangeListener listener);

  /** 业务规则变更监听器接口 */
  interface BusinessRuleChangeListener {

    /**
     * 当规则被注册时调用
     *
     * @param rule 被注册的规则
     */
    void onRuleRegistered(BusinessRuleMetadata rule);

    /**
     * 当规则被更新时调用
     *
     * @param rule 被更新的规则
     */
    void onRuleUpdated(BusinessRuleMetadata rule);

    /**
     * 当规则被注销时调用
     *
     * @param ruleId 被注销的规则ID
     */
    void onRuleUnregistered(String ruleId);

    /** 当注册表被清空时调用 */
    void onRegistryCleared();
  }
}
