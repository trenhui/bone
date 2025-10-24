package com.bone.example.extension.risk;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.annotation.ExtPointDoc;

/**
 * 风控规则扩展点
 * 定义了风险评估和风险分析的核心方法
 */
// 运行时配置 - 提供扩展点基本信息和默认配置
@ExtPoint(
    name = "风控规则扩展点",
    description = "处理各类交易风险的评估和分析逻辑",
    domain = "风险管理",
    category = "风控规则",
    version = "1.0.0",
    enabled = true,
    priority = 100,
    enableCache = true,
    timeout = 1000
)
// 接口文档 - 详细描述扩展点功能、参数和使用场景（编译时注解，不影响运行时）
@ExtPointDoc(
    title = "风控规则扩展点接口",
    domain = "风险管理",
    category = "风控规则",
    description = "定义了交易风险评估的标准接口，支持不同风控规则的实现。",
    usage = "1. 实现接口并添加@Extension注解\n2. 根据交易类型配置路由条件\n3. 注入到风控服务层使用",
    bestPractices = "1. 确保风险评估的准确性和及时性\n2. 针对不同交易场景提供专门实现\n3. 实现适当的规则优先级机制",
    params = {
        @ExtPointDoc.Param(
            name = "context",
            type = "BizContext<TransactionRequest>",
            description = "包含交易请求信息的业务上下文",
            required = true
        )
    },
    returnInfo = @ExtPointDoc.Return(
        type = "RiskAssessmentResult",
        description = "风险评估结果"
    ),
    notes = "扩展实现需要根据不同的交易类型和风险场景提供专门的风控规则",
    creator = "测试团队",
    createDate = "2024-01-01"
)
public interface RiskControlExtPoint {
    
    /**
     * 评估交易风险
     * @param context 业务上下文，包含交易请求信息
     * @return 风险评估结果
     */
    RiskAssessmentResult assessRisk(BizContext<TransactionRequest> context);
    
    /**
     * 获取规则的优先级
     * @return 优先级，数值越小优先级越高
     */
    int getRulePriority();
}

/**
 * 交易请求类
 */
interface TransactionRequest {
    String getTransactionId();
    String getUserId();
    double getAmount();
    String getTransactionType();
    String getTimestamp();
    String getLocation();
    String getDeviceInfo();
    String getIpAddress();
}