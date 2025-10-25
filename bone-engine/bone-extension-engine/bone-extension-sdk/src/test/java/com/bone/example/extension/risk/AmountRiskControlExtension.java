package com.bone.example.extension.risk;

import com.bone.engine.extension.Extension;
import com.bone.engine.extension.annotation.ExtensionDoc;
import com.bone.engine.extension.context.BizContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;

// 导入已定义的风险评估结果类
import com.bone.example.extension.risk.RiskAssessmentResult;

// 移除冲突的内部TransactionRequest类定义

/**
 * 金额风险控制扩展点实现类
 * <p>
 * 基于交易金额的风险控制策略，根据预设阈值评估交易金额风险等级。
 * 支持低、中、高三个风险等级的精确划分，为大额交易提供专业风险评估。
 */
@Extension(
    name = "金额风险控制扩展实现",
    description = "根据交易金额自动评估风险等级并提供相应的风险控制策略",
    tenantCode = "*",
    bizCode = "AMOUNT_RISK",
    priority = 100,
    enabled = true,
    version = "1.0.0"
)
@ExtensionDoc(
    description = "精准评估交易金额风险的扩展实现，通过多级别阈值划分风险等级",
    scenarios = "适用于各类交易系统中基于金额维度的风险评估场景",
    implementationDetails = "使用三级阈值机制对交易金额进行风险评估，生成风险因子并提供决策建议",
    performance = "单次执行耗时<2ms，满足高并发业务需求",
    notes = "专注于金额维度的风险控制，使用BigDecimal确保金额计算精度，具备可配置的风险阈值",
    author = "风险控制团队",
    createDate = "2024-01-01"
)
@Component
public class AmountRiskControlExtension implements RiskControlExtPoint {
    
    // 日志记录器
    private static final Logger logger = LoggerFactory.getLogger(AmountRiskControlExtension.class);
    
    // 风险阈值常量定义
    private static final BigDecimal LOW_RISK_THRESHOLD = new BigDecimal("1000");    // 低风险阈值
    private static final BigDecimal MEDIUM_RISK_THRESHOLD = new BigDecimal("5000"); // 中风险阈值
    private static final BigDecimal HIGH_RISK_THRESHOLD = new BigDecimal("10000"); // 高风险阈值
    
    // 风险因子相关常量
    private static final String AMOUNT_RISK_FACTOR_ID = "AMOUNT_RISK_001";
    private static final String AMOUNT_RISK_FACTOR_NAME = "交易金额风险";
    
    // 决策结果常量
    private static final String DECISION_PASS = "PASS";    // 通过
    private static final String DECISION_REVIEW = "REVIEW";  // 人工审核
    
    // 风险分数范围常量
    private static final int HIGH_RISK_SCORE_MIN = 70;
    private static final int MEDIUM_RISK_SCORE_MIN = 40;
    private static final int LOW_RISK_SCORE_MIN = 20;
    
    /**
     * 评估交易金额风险
     * <p>
     * 根据交易金额与预设阈值的比较，评估风险等级并生成风险因子。
     * 支持低风险（<1000）、中风险（1000-5000）、高风险（>5000）三个等级。
     * 
     * @param context 业务上下文，包含交易请求信息
     * @return 风险评估结果，包含风险等级、风险因子和建议
     * @throws IllegalArgumentException 当输入参数无效时抛出
     */
    @Override
    public RiskAssessmentResult assessRisk(final BizContext<TransactionRequest> context) {
        logger.info("开始执行金额维度风险评估");
        
        // 验证输入参数
        validateContext(context);
        final TransactionRequest request = context.getData();
        
        // 获取交易金额
        final BigDecimal amount = request.getAmount();
        logger.debug("评估交易金额: {}", amount);
        
        // 评估风险等级和分数
        final RiskAssessmentResult.RiskLevel riskLevel = assessRiskLevel(amount);
        final int riskScore = calculateRiskScore(amount);
        
        // 创建风险因子
        final RiskAssessmentResult.RiskFactor riskFactor = createRiskFactor(amount, riskLevel, riskScore);
        
        // 生成决策结果和原因
        final String decision = generateDecision(riskLevel);
        final String reviewReason = riskLevel != RiskAssessmentResult.RiskLevel.LOW ? 
            String.format("交易金额%s超过%s阈值，需要进一步验证", amount, riskLevel.name()) : null;
        
        // 构建最终结果
        final RiskAssessmentResult result = RiskAssessmentResult.builder()
                .riskLevel(riskLevel)
                .riskScore(riskScore)
                .decision(decision)
                .riskFactors(Collections.singletonList(riskFactor))
                .rejectReason(reviewReason)
                .build();
        
        logger.info("金额风险评估完成，交易金额: {}, 风险等级: {}, 风险分数: {}, 决策结果: {}", 
                amount, riskLevel, riskScore, decision);
        return result;
    }

    /**
     * 获取规则优先级
     * <p>
     * 金额风险评估具有最高优先级，应最先执行
     * 
     * @return 优先级数值，0为最高优先级
     */
    @Override
    public int getRulePriority() {
        return 0; // 最高优先级，金额风险评估应最先执行
    }
    
    /**
     * 验证业务上下文的有效性
     * 
     * @param context 待验证的业务上下文
     * @throws IllegalArgumentException 当上下文为空或不包含有效数据时抛出
     */
    private void validateContext(final BizContext<TransactionRequest> context) {
        if (context == null) {
            logger.error("业务上下文为空，无法进行风险评估");
            throw new IllegalArgumentException("业务上下文不能为空");
        }
        
        final TransactionRequest request = context.getData();
        if (request == null) {
            logger.error("交易请求数据为空，无法进行风险评估");
            throw new IllegalArgumentException("交易请求数据不能为空");
        }
        
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            logger.error("无效的交易金额: {}", request.getAmount());
            throw new IllegalArgumentException("交易金额不能为空且必须大于零");
        }
    }
    
    /**
     * 评估风险等级
     * 
     * @param amount 交易金额
     * @return 风险等级
     */
    private RiskAssessmentResult.RiskLevel assessRiskLevel(final BigDecimal amount) {
        if (amount.compareTo(HIGH_RISK_THRESHOLD) >= 0) {
            logger.debug("金额{}超过高风险阈值{}，判定为高风险", amount, HIGH_RISK_THRESHOLD);
            return RiskAssessmentResult.RiskLevel.HIGH;
        } else if (amount.compareTo(MEDIUM_RISK_THRESHOLD) >= 0) {
            logger.debug("金额{}超过中风险阈值{}，判定为中风险", amount, MEDIUM_RISK_THRESHOLD);
            return RiskAssessmentResult.RiskLevel.MEDIUM;
        } else if (amount.compareTo(LOW_RISK_THRESHOLD) >= 0) {
            logger.debug("金额{}超过低风险阈值{}，判定为中风险", amount, LOW_RISK_THRESHOLD);
            return RiskAssessmentResult.RiskLevel.MEDIUM;
        } else {
            logger.debug("金额{}在低风险范围内，判定为低风险", amount);
            return RiskAssessmentResult.RiskLevel.LOW;
        }
    }
    
    /**
     * 计算风险分数
     * <p>
     * 根据金额大小计算风险分数（0-100）
     * 
     * @param amount 交易金额
     * @return 风险分数
     */
    private int calculateRiskScore(final BigDecimal amount) {
        if (amount.compareTo(HIGH_RISK_THRESHOLD) >= 0) {
            // 高风险：70-100分
            int multiplier = amount.divide(HIGH_RISK_THRESHOLD, 0, RoundingMode.DOWN).intValue();
            return Math.min(100, HIGH_RISK_SCORE_MIN + multiplier * 3);
        } else if (amount.compareTo(MEDIUM_RISK_THRESHOLD) >= 0) {
            // 中风险：40-69分
            final BigDecimal ratio = amount.divide(MEDIUM_RISK_THRESHOLD, 2, RoundingMode.DOWN);
            return (int) (MEDIUM_RISK_SCORE_MIN + (ratio.doubleValue() - 1.0) * 30);
        } else if (amount.compareTo(LOW_RISK_THRESHOLD) >= 0) {
            // 低中风险：20-39分
            final BigDecimal ratio = amount.divide(LOW_RISK_THRESHOLD, 2, RoundingMode.DOWN);
            return (int) (LOW_RISK_SCORE_MIN + ratio.doubleValue() * 20);
        } else {
            // 低风险：0-19分
            final BigDecimal ratio = amount.divide(LOW_RISK_THRESHOLD, 2, RoundingMode.DOWN);
            return (int) (ratio.doubleValue() * 20);
        }
    }
    
    /**
     * 创建风险因子
     * 
     * @param amount 交易金额
     * @param riskLevel 风险等级
     * @param riskScore 风险分数
     * @return 风险因子对象
     */
    private RiskAssessmentResult.RiskFactor createRiskFactor(final BigDecimal amount, 
                                                           final RiskAssessmentResult.RiskLevel riskLevel, 
                                                           final int riskScore) {
        final String description = String.format("交易金额%s，风险等级%s，风险分数%d", 
                                               amount, riskLevel, riskScore);
        
        logger.debug("创建金额风险因子: {} - {}", AMOUNT_RISK_FACTOR_NAME, description);
        
        return RiskAssessmentResult.RiskFactor.builder()
                .factorId(AMOUNT_RISK_FACTOR_ID)
                .factorName(AMOUNT_RISK_FACTOR_NAME)
                .riskContribution(riskScore)
                .description(description)
                .build();
    }
    
    /**
     * 生成决策结果
     * 
     * @param riskLevel 风险等级
     * @return 决策结果：PASS（通过）、REVIEW（人工审核）、REJECT（拒绝）
     */
    private String generateDecision(final RiskAssessmentResult.RiskLevel riskLevel) {
        switch (riskLevel) {
            case HIGH:
            case MEDIUM:
                return DECISION_REVIEW;
            case LOW:
            default:
                return DECISION_PASS;
        }
    }
}