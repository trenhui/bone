package com.bone.smartmeta.engine.constant;

import com.bone.smartmeta.engine.common.Constants;

/**
 * 工作流状态常量类
 * 统一管理工作流引擎中的状态码
 * 
 * @deprecated 请使用 {@link Constants.WorkflowStatus} 枚举替代
 */
@Deprecated
public final class WorkflowStatusConstants {
    
    // 私有构造函数，防止实例化
    private WorkflowStatusConstants() {
        throw new AssertionError("不能实例化WorkflowStatusConstants类");
    }
    
    /**
     * 工作流状态：初始化
     * 表示工作流刚刚创建，尚未启动
     */
    public static final String INITIATED = "INITIATED";
    
    /**
     * 工作流状态：运行中
     * 表示工作流正在执行中
     */
    public static final String RUNNING = "RUNNING";
    
    /**
     * 工作流状态：已完成
     * 表示工作流已经成功完成所有任务
     */
    public static final String COMPLETED = "COMPLETED";
    
    /**
     * 工作流状态：已终止
     * 表示工作流由于某种原因被提前终止
     */
    public static final String TERMINATED = "TERMINATED";
}