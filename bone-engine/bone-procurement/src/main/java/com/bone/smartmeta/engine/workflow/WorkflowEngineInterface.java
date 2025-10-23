package com.bone.smartmeta.engine.workflow;

import java.util.Map;

/**
 * 工作流引擎接口
 * 定义工作流引擎的核心功能，包括工作流实例创建、转换执行和任务完成
 */
public interface WorkflowEngineInterface {

    /**
     * 启动工作流实例
     * @param entity 实体对象
     * @param workflowName 工作流名称
     * @return 工作流实例ID
     * @throws IllegalArgumentException 当工作流名称为空时抛出
     */
    String startWorkflow(Object entity, String workflowName);

    /**
     * 启动工作流实例（带变量）
     * @param workflowName 工作流名称
     * @param businessKey 业务键
     * @param variables 工作流变量
     * @throws IllegalArgumentException 当工作流名称或业务键为空时抛出
     */
    void startWorkflow(String workflowName, String businessKey, Map<String, Object> variables);

    /**
     * 执行工作流转换
     * @param entityId 实体ID
     * @param workflowName 工作流名称
     * @param transitionName 转换名称
     * @param params 转换参数
     * @return 是否成功
     * @throws IllegalArgumentException 当必要参数为空时抛出
     */
    boolean executeTransition(String entityId, String workflowName, String transitionName, Object params);

    /**
     * 完成工作流任务
     * @param workflowName 工作流名称
     * @param businessKey 业务键
     * @param transition 转换名称
     * @param variables 任务变量
     * @throws IllegalArgumentException 当必要参数为空时抛出
     */
    void completeTask(String workflowName, String businessKey, String transition, Map<String, String> variables);
}