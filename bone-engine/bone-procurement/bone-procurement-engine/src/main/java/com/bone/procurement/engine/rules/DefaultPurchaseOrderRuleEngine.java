package com.bone.procurement.engine.rules;

import com.bone.procurement.engine.model.*;
import com.bone.core.util.StringUtils;
import com.bone.core.util.CollectionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 采购订单业务规则引擎默认实现
 * 实现采购订单相关的业务规则验证和审批流程判断
 */
@Slf4j
@Component
public class DefaultPurchaseOrderRuleEngine implements PurchaseOrderRuleEngine {
    
    // 内部常量枚举类
    private enum Constants {
        // 审批级别
        LEVEL_1("LEVEL_1", 1, "部门经理审批"),
        LEVEL_2("LEVEL_2", 2, "总监审批"),
        LEVEL_3("LEVEL_3", 3, "副总经理审批"),
        LEVEL_4("LEVEL_4", 4, "总经理审批"),
        
        // 严重性级别
        SEVERITY_ERROR("ERROR"),
        SEVERITY_WARNING("WARNING"),
        SEVERITY_INFO("INFO"),
        
        // 审批流程定义
        SIMPLE_PURCHASE_FLOW("SIMPLE_PURCHASE_FLOW"),
        STANDARD_PURCHASE_FLOW("STANDARD_PURCHASE_FLOW"),
        COMPLEX_PURCHASE_FLOW("COMPLEX_PURCHASE_FLOW"),
        VIP_PURCHASE_FLOW("VIP_PURCHASE_FLOW"),
        EMERGENCY_PURCHASE_FLOW("EMERGENCY_PURCHASE_FLOW"),
        
        // 审批人ID
        DIRECTOR_ID("director_001", "总监"),
        VP_ID("vp_001", "副总经理"),
        GM_ID("gm_001", "总经理");
        
        private final String value;
        private final int level; // 仅用于LEVEL枚举
        private final String description; // 描述信息
        
        Constants(String value) {
            this(value, 0, "");
        }
        
        Constants(String value, String description) {
            this(value, 0, description);
        }
        
        Constants(String value, int level, String description) {
            this.value = value;
            this.level = level;
            this.description = description;
        }
        
        public String getValue() {
            return value;
        }
        
        public int getLevel() {
            return level;
        }
        
        public String getDescription() {
            return description;
        }
        
        // 根据级别获取常量枚举
        public static Constants getByLevel(int level) {
            for (Constants constant : values()) {
                if (constant.getLevel() == level) {
                    return constant;
                }
            }
            return null;
        }
    }
    
    // 为方便使用，创建常用常量的引用
    private static final String SEVERITY_ERROR = Constants.SEVERITY_ERROR.getValue();
    private static final String SEVERITY_WARNING = Constants.SEVERITY_WARNING.getValue();
    private static final String SEVERITY_INFO = Constants.SEVERITY_INFO.getValue();
    private static final String EMERGENCY_PURCHASE_FLOW = Constants.EMERGENCY_PURCHASE_FLOW.getValue();
    private static final String SIMPLE_PURCHASE_FLOW = Constants.SIMPLE_PURCHASE_FLOW.getValue();
    private static final String STANDARD_PURCHASE_FLOW = Constants.STANDARD_PURCHASE_FLOW.getValue();
    private static final String COMPLEX_PURCHASE_FLOW = Constants.COMPLEX_PURCHASE_FLOW.getValue();
    private static final String VIP_PURCHASE_FLOW = Constants.VIP_PURCHASE_FLOW.getValue();
    
    // 日志工具方法，减少重复代码
    private void logWarning(String requestId, String orderCode, String message, Object... args) {
        log.warn("[{}] Order {} {}", requestId, orderCode, message, args);
    }
    
    private void logError(String requestId, String orderCode, String message, Throwable e) {
        log.error("[{}] Order {} {}", requestId, orderCode, message, e);
    }
    
    private void logDebug(String requestId, String orderCode, String message, Object... args) {
        log.debug("[{}] Order {} {}", requestId, orderCode, message, args);
    }
    
    private void logInfo(String requestId, String orderCode, String message, Object... args) {
        log.info("[{}] Order {} {}", requestId, orderCode, message, args);
    }
    
    // 优化的日志错误方法（包含异常信息和额外参数）
    private void logErrorWithArgs(String requestId, String orderCode, String message, Throwable e, Object... args) {
        // 在SLF4J中，异常应该作为最后一个参数传入
        int argsLength = args != null ? args.length : 0;
        Object[] allArgs = new Object[argsLength + 3];
        allArgs[0] = requestId;
        allArgs[1] = orderCode;
        allArgs[2] = String.format(message, args); // 先格式化带参数的消息
        allArgs[argsLength + 2] = e; // 异常作为最后一个参数
        log.error("{} {} {}", allArgs);
    }
    // 审批金额阈值配置
    private final Map<String, BigDecimal> approvalThresholds = new HashMap<>();
    
    // 紧急采购定义：交货日期在7天内
    @Value("${procurement.order.approval.emergency.threshold:7}")
    private int urgentDeliveryDays;
    
    // 默认税率
    @Value("${procurement.order.tax-rate.default:0.13}")
    private String defaultTaxRate;
    
    @PostConstruct
    public void init() {
        try {
            log.info("Initializing PurchaseOrderRuleEngine...");
            
            // 确保Map已初始化
            if (approvalThresholds == null) {
                approvalThresholds = new HashMap<>();
                log.debug("Created new approval thresholds map");
            }
            
            // 清除现有的阈值配置，确保重新初始化
            approvalThresholds.clear();
            
            // 使用常量枚举加载审批阈值，并添加详细注释
            log.debug("Loading approval thresholds from constants...");
            
            // 级别1: 5000元以下 - 总监审批
            approvalThresholds.put(Constants.LEVEL_1.getValue(), new BigDecimal(5000));
            log.debug("Loaded LEVEL_1 threshold: 5000");
            
            // 级别2: 2万元以下 - 副总经理审批
            approvalThresholds.put(Constants.LEVEL_2.getValue(), new BigDecimal(20000));
            log.debug("Loaded LEVEL_2 threshold: 20000");
            
            // 级别3: 10万元以下 - 总经理审批
            approvalThresholds.put(Constants.LEVEL_3.getValue(), new BigDecimal(100000));
            log.debug("Loaded LEVEL_3 threshold: 100000");
            
            // 级别4: 50万元以下 - 高级管理层审批
            approvalThresholds.put(Constants.LEVEL_4.getValue(), new BigDecimal(500000));
            log.debug("Loaded LEVEL_4 threshold: 500000");
            
            // 记录紧急采购相关配置
            log.info("Emergency purchase threshold: {} days", urgentDeliveryDays);
            log.info("Default tax rate: {}", defaultTaxRate);
            
            // 记录初始化完成信息
            log.info("PurchaseOrderRuleEngine initialized successfully with {} approval thresholds", 
                    approvalThresholds.size());
        } catch (Exception e) {
            log.error("Failed to initialize PurchaseOrderRuleEngine: {}", e.getMessage(), e);
            // 即使初始化失败，也创建一个默认的阈值配置以确保系统可以继续运行
            if (approvalThresholds == null || approvalThresholds.isEmpty()) {
                log.warn("Creating minimal default threshold configuration due to initialization error");
                approvalThresholds = new HashMap<>();
                approvalThresholds.put(Constants.LEVEL_1.getValue(), new BigDecimal(10000));
            }
        }
    }
    
    @Override
    public List<RuleValidationResult> validateOrder(PurchaseOrder order, RuleExecutionContext context) {
        List<RuleValidationResult> results = new ArrayList<>();
        String requestId = getRequestId(context);
        
        log.debug("[{}] Starting order validation process", requestId);
        
        try {
            if (order == null) {
                log.error("[{}] Order validation failed: null order object", requestId);
                results.add(RuleValidationResult.failure(
                        "PO001", "订单对象为空", "PO_VALIDATION_001", "无法验证空订单对象"
                ));
                return results;
            }
            
            String orderCode = getOrderCode(order);
            log.info("[{}] Validating order: {}", requestId, orderCode);
            
            if (context != null) {
                log.debug("[{}] Validation context properties: {}", requestId, context.getProperties());
            }
            
            long startTime = System.currentTimeMillis();
            
            // 调用各个验证方法
            validateBasicInfo(order, results);
            log.debug("[{}] Basic info validation completed for order: {}", requestId, orderCode);
            
            validateAmountInfo(order, results);
            log.debug("[{}] Amount info validation completed for order: {}", requestId, orderCode);
            
            validateOrderItems(order, results);
            log.debug("[{}] Order items validation completed for order: {}", requestId, orderCode);
            
            validateSupplierInfo(order, results);
            log.debug("[{}] Supplier info validation completed for order: {}", requestId, orderCode);
            
            validateDeliveryDate(order, results);
            log.debug("[{}] Delivery date validation completed for order: {}", requestId, orderCode);
            
            validateUrgentPurchaseRule(order, results);
            log.debug("[{}] Urgent purchase rule validation completed for order: {}", requestId, orderCode);
            
            // 统计验证结果
            long errorCount = results.stream().filter(r -> r.getSeverity() == SEVERITY_ERROR).count();
            long warningCount = results.stream().filter(r -> r.getSeverity() == SEVERITY_WARNING).count();
            long infoCount = results.stream().filter(r -> r.getSeverity() == SEVERITY_INFO).count();
            long validationTime = System.currentTimeMillis() - startTime;
            
            log.info("[{}] Order {} validation completed in {}ms - Errors: {}, Warnings: {}, Info: {}", 
                    requestId, orderCode, validationTime, errorCount, warningCount, infoCount);
            
        } catch (Exception e) {
            String orderCode = order != null ? getOrderCode(order) : "<unknown>";
            log.error("[{}] Unexpected error during order {} validation: {}", 
                    requestId, orderCode, e.getMessage(), e);
            // 添加通用错误结果
            results.add(RuleValidationResult.failure(
                    "PO098", "订单验证异常", "PO_VALIDATION_098", "订单验证过程中发生未预期的异常"
            ));
        }
        
        log.debug("[{}] Order validation process finished with {} results", requestId, results.size());
        return results;
    }
    
    @Override
    public boolean requiresApproval(PurchaseOrder order, RuleExecutionContext context) {
        String requestId = getRequestId(context);
        log.debug("[{}] Checking if order requires approval", requestId);
        
        try {
            // 如果订单为空，默认需要审批
            if (order == null) {
                log.warn("[{}] Null order passed to requiresApproval check", requestId);
                return true;
            }
            
            String orderCode = getOrderCode(order);
            log.debug("[{}] Processing order {} for approval requirement check", requestId, orderCode);
            
            // 紧急采购逻辑 - 检查是否符合紧急采购条件
            if (order.isEmergencyPurchase()) {
                log.info("[{}] Order {} is marked as emergency purchase, special approval required", 
                        requestId, orderCode);
                // 紧急采购需要审批
                return true;
            }
            
            // 获取订单总金额
            BigDecimal orderAmount = order.getTotalAmount();
            if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("[{}] Order {} has invalid amount: {}", requestId, orderCode, orderAmount);
                // 金额无效时默认需要审批
                return true;
            }
            
            // 根据不同审批级别的阈值判断是否需要审批
            int approvalLevel = getApprovalLevelInternal(orderAmount, context);
            log.debug("[{}] Order {} approval level: {}, requires approval: true", 
                    requestId, orderCode, approvalLevel);
            
            // 使用常量枚举进行级别判断，任何大于0的审批级别都需要审批
            Constants levelConstant = Constants.getByLevel(approvalLevel);
            if (levelConstant != null) {
                log.debug("[{}] Order {} requires {} ({})", 
                        requestId, orderCode, levelConstant.getDescription(), approvalLevel);
            }
            
            // 金额大于0的订单都需要审批
            return true;
        } catch (Exception e) {
            log.error("[{}] Error checking approval requirement: {}", requestId, e.getMessage(), e);
            // 发生异常时默认返回true，确保安全性
            return true;
        }
    }
    
    @Override
    public String getApprovalFlowDefinition(PurchaseOrder order, RuleExecutionContext context) {
        String requestId = getRequestId(context);
        String orderCode = getOrderCode(order);
        
        log.debug("[{}] Determining approval flow for order: {}, isEmergency: {}, amount: {}", 
                 requestId, orderCode, order.isEmergencyPurchase(), order.getTotalAmount());
        
        if (order.isEmergencyPurchase()) {
            log.debug("[{}] Order {} is emergency purchase, using {}", 
                     requestId, orderCode, EMERGENCY_PURCHASE_FLOW);
            return EMERGENCY_PURCHASE_FLOW;
        }
        
        BigDecimal amount = order.getTotalAmount();
        // 空值安全检查
        if (amount == null) {
            log.warn("[{}] Order {} has null amount, defaulting to SIMPLE_PURCHASE_FLOW", 
                     requestId, orderCode);
            return SIMPLE_PURCHASE_FLOW;
        }
        
        // 根据金额获取审批级别，然后映射到对应的流程
        int level = getApprovalLevelInternal(amount, context);
        switch (level) {
            case 1: return SIMPLE_PURCHASE_FLOW;
            case 2: return STANDARD_PURCHASE_FLOW;
            case 3: return COMPLEX_PURCHASE_FLOW;
            default: return VIP_PURCHASE_FLOW;
        }
    }
    
    @Override
    public int getApprovalLevel(PurchaseOrder order, RuleExecutionContext context) {
        String requestId = getRequestId(context);
        String orderCode = getOrderCode(order);
        BigDecimal amount = order.getTotalAmount();
        
        log.debug("[{}] Calculating approval level for order: {}, amount: {}", 
                 requestId, orderCode, amount);
        
        int level = getApprovalLevelInternal(amount, context);
        
        // 记录审批级别信息
        Constants levelConstant = Constants.getByLevel(level);
        if (levelConstant != null) {
            log.debug("[{}] Order {} assigned to level {} approval ({})", 
                     requestId, orderCode, level, levelConstant.getDescription());
        }
        
        return level;
    }
    
    // 内部方法：根据金额获取审批级别，避免代码重复
    private int getApprovalLevelInternal(BigDecimal amount, RuleExecutionContext context) {
        String requestId = getRequestId(context);
        
        // 空值安全检查
        if (amount == null) {
            log.warn("[{}] Amount is null, defaulting to level 1", requestId);
            return 1;
        }
        
        // 负数金额检查
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("[{}] Negative amount detected: {}, defaulting to level 1", requestId, amount);
            return 1;
        }
        
        try {
            // 确保阈值配置存在，避免空指针异常
            if (approvalThresholds.isEmpty()) {
                log.error("[{}] Approval thresholds not initialized, using default level 1", requestId);
                return 1;
            }
            
            // 使用常量枚举进行级别判断并记录详细信息
            BigDecimal level1Threshold = approvalThresholds.get(Constants.LEVEL_1.getValue());
            BigDecimal level2Threshold = approvalThresholds.get(Constants.LEVEL_2.getValue());
            BigDecimal level3Threshold = approvalThresholds.get(Constants.LEVEL_3.getValue());
            
            if (level1Threshold != null && amount.compareTo(level1Threshold) <= 0) {
                log.debug("[{}] Amount {} <= level 1 threshold {}, assigning level 1", 
                         requestId, amount, level1Threshold);
                return 1;
            } else if (level2Threshold != null && amount.compareTo(level2Threshold) <= 0) {
                log.debug("[{}] Amount {} <= level 2 threshold {}, assigning level 2", 
                         requestId, amount, level2Threshold);
                return 2;
            } else if (level3Threshold != null && amount.compareTo(level3Threshold) <= 0) {
                log.debug("[{}] Amount {} <= level 3 threshold {}, assigning level 3", 
                         requestId, amount, level3Threshold);
                return 3;
            } else {
                // 高于所有阈值，返回最高级别
                log.debug("[{}] Amount {} > level 3 threshold {}, assigning level 4", 
                         requestId, amount, level3Threshold != null ? level3Threshold : "N/A");
                return 4;
            }
        } catch (Exception e) {
            log.error("[{}] Error determining approval level for amount {}: {}", 
                     requestId, amount, e.getMessage(), e);
            // 发生异常时默认返回级别1，确保系统继续运行
            return 1;
        }
    }
    
    @Override
    public String getNextApprover(PurchaseOrder order, String currentApprover, RuleExecutionContext context) {
        String requestId = getRequestId(context);
        String orderCode = getOrderCode(order);
        int currentLevel = getApprovalLevel(order, context);
        
        log.debug("[{}] Getting next approver for order: {}, current level: {}, current approver: {}", 
                 requestId, orderCode, currentLevel, currentApprover);
        
        // 这里应该根据实际的审批流程和组织结构来获取下一审批人
        // 简化实现，返回模拟的审批人ID
        switch (currentLevel) {
            case 1:
                log.debug("[{}] Next approver for order {} at level 1 is {}", 
                         requestId, orderCode, Constants.DIRECTOR_ID.getDescription());
                return Constants.DIRECTOR_ID.getValue();  // 总监
            case 2:
                log.debug("[{}] Next approver for order {} at level 2 is {}", 
                         requestId, orderCode, Constants.VP_ID.getDescription());
                return Constants.VP_ID.getValue();  // 副总经理
            case 3:
                log.debug("[{}] Next approver for order {} at level 3 is {}", 
                         requestId, orderCode, Constants.GM_ID.getDescription());
                return Constants.GM_ID.getValue();  // 总经理
            default:
                log.debug("[{}] No next approver for order {}, approval complete", 
                         requestId, orderCode);
                return null;  // 审批结束
        }
    }
    
    @Override
    public ApprovalResult executeEmergencyPurchaseRule(PurchaseOrder order, RuleExecutionContext context) {
        String requestId = getRequestId(context);
        String orderCode = getOrderCode(order);
        
        log.debug("[{}] Executing emergency purchase rule for order: {}, isEmergency: {}", 
                 requestId, orderCode, order.isEmergencyPurchase());
        
        try {
            if (order.isEmergencyPurchase()) {
                // 紧急采购特殊处理：
                // 1. 缩短审批时间
                // 2. 允许跳过某些常规审批节点
                
                // 检查是否符合紧急采购条件
                LocalDateTime expectedDate = order.getExpectedDeliveryDate();
                if (expectedDate == null) {
                    log.warn("[{}] Emergency purchase marked but no delivery date set for order: {}", 
                             requestId, orderCode);
                    return ApprovalResult.builder()
                            .status(SEVERITY_WARNING)
                            .approved(false)
                            .comment("标记为紧急采购但未设置交货日期")
                            .build();
                }
                
                LocalDateTime currentTime = LocalDateTime.now();
                long daysUntilDelivery = ChronoUnit.DAYS.between(currentTime, expectedDate);
                log.debug("[{}] Days until delivery: {}, threshold: {}", 
                         requestId, daysUntilDelivery, urgentDeliveryDays);
                
                if (daysUntilDelivery <= urgentDeliveryDays) {
                    // 符合紧急采购条件，可以走快速审批
                    String nextApprover = getNextApprover(order, null, context);
                    int approvalLevel = getApprovalLevel(order, context);
                    log.info("[{}] Order {} qualifies for emergency purchase flow, next approver: {}, level: {}", 
                             requestId, orderCode, nextApprover, approvalLevel);
                    
                    return ApprovalResult.pending(EMERGENCY_PURCHASE_FLOW, nextApprover, approvalLevel);
                } else {
                    // 不符合紧急采购条件，但标记为紧急，返回警告
                    log.warn("[{}] Order {} marked as emergency but delivery date exceeds threshold ({} days)", 
                             requestId, orderCode, urgentDeliveryDays);
                    return ApprovalResult.builder()
                            .status(SEVERITY_WARNING)
                            .approved(false)
                            .comment("标记为紧急采购但交货日期超过" + urgentDeliveryDays + "天")
                            .build();
                }
            }
            
            // 非紧急采购，返回标准结果
            String flowDefinition = getApprovalFlowDefinition(order, context);
            String nextApprover = getNextApprover(order, null, context);
            int approvalLevel = getApprovalLevel(order, context);
            
            log.debug("[{}] Regular purchase flow for order {}: {}, approver: {}, level: {}", 
                     requestId, orderCode, flowDefinition, nextApprover, approvalLevel);
            
            return ApprovalResult.pending(flowDefinition, nextApprover, approvalLevel);
        } catch (Exception e) {
            log.error("[{}] Error executing emergency purchase rule for order {}: {}", 
                     requestId, orderCode, e.getMessage(), e);
            // 添加异常处理
            return ApprovalResult.builder()
                    .status(SEVERITY_ERROR)
                    .approved(false)
                    .comment("执行紧急采购规则时发生异常: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 获取请求ID
     * @param context 规则执行上下文
     * @return 请求ID，如果无法获取则返回默认值
     */
    private String getRequestId(RuleExecutionContext context) {
        try {
            if (context == null) {
                log.warn("Attempting to get request ID from null context object");
                return "N/A"; // 默认值
            }
            
            String requestId = context.getRequestId();
            if (requestId == null || requestId.trim().isEmpty()) {
                log.debug("Context has null or empty requestId");
                return "NO_REQUEST_ID"; // 使用更具描述性的默认值
            }
            
            return requestId.trim(); // 去除首尾空格
        } catch (Exception e) {
            log.error("Error getting request ID: {}", e.getMessage(), e);
            return "ERROR_REQUEST_ID"; // 错误状态的默认值
        }
    }
    
    /**
     * 获取请求ID (从订单上下文Map中)
     * @param contextMap 订单上下文Map
     * @return 请求ID，如果无法获取则返回默认值
     */
    private String getRequestId(Map<String, String> contextMap) {
        try {
            if (contextMap == null || contextMap.isEmpty()) {
                log.warn("Attempting to get request ID from null or empty context map");
                return "NO_CONTEXT_MAP"; // 使用更具描述性的默认值
            }
            
            String requestId = contextMap.get("requestId");
            if (requestId == null || requestId.trim().isEmpty()) {
                log.debug("Context map has null or empty requestId");
                // 尝试从上下文获取其他可能的ID标识
                String altId = contextMap.getOrDefault("traceId", contextMap.getOrDefault("correlationId", "NO_REQUEST_ID"));
                log.debug("Using alternative ID: {}", altId);
                return altId;
            }
            
            return requestId.trim(); // 去除首尾空格
        } catch (Exception e) {
            log.error("Error getting request ID from context map: {}", e.getMessage(), e);
            return "ERROR_REQUEST_ID"; // 错误状态的默认值
        }
    }
    
    /**
     * 获取订单编号
     * @param order 采购订单对象
     * @return 订单编号，如果无法获取则返回默认值
     */
    private String getOrderCode(PurchaseOrder order) {
        try {
            if (order == null) {
                log.warn("Attempting to get order code from null order object");
                return "N/A"; // 默认值
            }
            
            String orderCode = order.getOrderCode();
            if (orderCode == null || orderCode.trim().isEmpty()) {
                log.debug("Order has null or empty order code");
                return "<new_order>"; // 保持与原默认值一致
            }
            
            return orderCode.trim(); // 去除首尾空格
        } catch (Exception e) {
            log.error("Error getting order code: {}", e.getMessage(), e);
            return "ERROR_ORDER_CODE"; // 错误状态的默认值
        }
    }
    /**
     * 验证订单基本信息
     * <p>验证内容包括：
     * <ul>
     * <li>订单编号不能为空且符合格式要求</li>
     * <li>订单类型不能为空且为有效值</li>
     * <li>创建人不能为空且符合命名规范</li>
     * <li>其他可能的基本信息验证</li>
     * </ul>
     * 
     * @param order 采购订单对象
     * @param results 验证结果列表
     */
    private void validateBasicInfo(PurchaseOrder order, List<RuleValidationResult> results) {
        String orderCode = getOrderCode(order);
        String requestId = getRequestId(order.getContext());
        logDebug(requestId, orderCode, "Validating basic info");
        
        int validationErrors = 0;
        
        try {
            // 验证订单编号
            String currentOrderCode = order.getOrderCode();
            if (currentOrderCode == null || currentOrderCode.trim().isEmpty()) {
                results.add(RuleValidationResult.failure(
                        "PO001", "订单编号不能为空", "PO_VALIDATION_001", "订单编号是必填项"
                ));
                logWarning(requestId, orderCode, "Order code is empty");
                validationErrors++;
            } else {
                // 验证订单编号格式（例如：以PO开头，后跟日期和序号）
                currentOrderCode = currentOrderCode.trim();
                if (!currentOrderCode.matches("^PO\\d{8}-\\d{4}$")) {
                    results.add(RuleValidationResult.warning(
                            "PO001A", "订单编号格式不规范", 
                            "建议订单编号格式为：PO+日期(8位)+-+序号(4位)，当前值：" + currentOrderCode
                    ));
                    logWarning(requestId, orderCode, "has non-standard order code format: {}", currentOrderCode);
                }
                
                // 验证订单编号长度
                if (currentOrderCode.length() > 50) {
                    results.add(RuleValidationResult.failure(
                            "PO001B", "订单编号过长", "PO_VALIDATION_001B", 
                            "订单编号长度不能超过50个字符，当前长度：" + currentOrderCode.length()
                    ));
                    logWarning(requestId, orderCode, "has too long order code: {} characters", currentOrderCode.length());
                    validationErrors++;
                }
            }
            
            // 验证订单类型
            String orderType = order.getOrderType();
            if (orderType == null || orderType.trim().isEmpty()) {
                results.add(RuleValidationResult.failure(
                        "PO002", "订单类型不能为空", "PO_VALIDATION_002", "订单类型是必填项"
                ));
                logWarning(requestId, orderCode, "Order type is empty");
                validationErrors++;
            } else {
                // 验证订单类型是否为有效值（假设有效值为：REGULAR, URGENT, CONTRACT等）
                orderType = orderType.trim().toUpperCase();
                List<String> validOrderTypes = Arrays.asList("REGULAR", "URGENT", "CONTRACT", "SPECIAL");
                if (!validOrderTypes.contains(orderType)) {
                    results.add(RuleValidationResult.failure(
                            "PO002A", "订单类型无效", "PO_VALIDATION_002A", 
                            "订单类型必须为以下之一：" + String.join(", ", validOrderTypes) + "，当前值：" + orderType
                    ));
                    logWarning(requestId, orderCode, "has invalid order type: {}", orderType);
                    validationErrors++;
                }
            }
            
            // 验证创建人
            String createdBy = order.getCreatedBy();
            if (createdBy == null || createdBy.trim().isEmpty()) {
                results.add(RuleValidationResult.failure(
                        "PO003", "创建人不能为空", "PO_VALIDATION_003", "创建人是必填项"
                ));
                logWarning(requestId, orderCode, "Created by is empty");
                validationErrors++;
            } else {
                // 验证创建人名称格式
                createdBy = createdBy.trim();
                if (!createdBy.matches("^[\\u4e00-\\u9fa5a-zA-Z0-9_-]{1,50}$")) {
                    results.add(RuleValidationResult.failure(
                            "PO003A", "创建人名称格式错误", "PO_VALIDATION_003A", 
                            "创建人名称只能包含中文、英文、数字、下划线和连字符，长度1-50，当前值：" + createdBy
                    ));
                    logWarning(requestId, orderCode, "has invalid createdBy format: {}", createdBy);
                    validationErrors++;
                }
            }
            
            // 验证创建时间
            if (order.getCreatedAt() == null) {
                results.add(RuleValidationResult.warning(
                        "PO003B", "创建时间未设置", "将使用当前时间作为创建时间"
                ));
                order.setCreatedAt(new Date());
                logInfo(requestId, orderCode, "Default created time set");
            }
            
            // 记录验证结果
            if (validationErrors > 0) {
                logInfo(requestId, orderCode, "basic info validation completed with {} errors", validationErrors);
            } else {
                logDebug(requestId, orderCode, "basic info validation passed");
            }
            
        } catch (Exception e) {
            logError(requestId, orderCode, e, "Error validating basic order info: {}", e.getMessage());
            // 添加一个通用错误结果
            results.add(RuleValidationResult.failure(
                    "PO099", "基本信息验证异常", "PO_VALIDATION_099", 
                    "订单基本信息验证过程中发生异常：" + e.getMessage()
            ));
        }
    }
    
    /**
     * 验证订单金额相关信息
     * <p>验证内容包括：
     * <ul>
     * <li>订单总金额不能为空且必须大于0</li>
     * <li>订单总金额精度检查（最多2位小数）</li>
     * <li>币种有效性检查</li>
     * <li>税率合理性验证</li>
     * </ul>
     * 
     * @param order 采购订单对象
     * @param results 验证结果列表
     */
    private void validateAmountInfo(PurchaseOrder order, List<RuleValidationResult> results) {
        String orderCode = getOrderCode(order);
        String requestId = getRequestId(order.getContext());
        logDebug(requestId, orderCode, "Validating amount info");
        
        int validationErrors = 0;
        
        try {
            // 验证订单总金额
            if (order.getTotalAmount() == null) {
                results.add(RuleValidationResult.failure(
                        "PO004", "订单总金额为空", "PO_VALIDATION_004", "订单总金额不能为空"
                ));
                logWarning(requestId, orderCode, "Total amount is null");
                validationErrors++;
            } else {
                // 验证金额必须大于0
                if (order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    results.add(RuleValidationResult.failure(
                            "PO004A", "订单总金额无效", "PO_VALIDATION_004A", "订单总金额必须大于0"
                    ));
                    logWarning(requestId, orderCode, "Total amount is invalid ({} <= 0)", order.getTotalAmount());
                    validationErrors++;
                }
                
                // 验证金额精度（最多2位小数）
                try {
                    int scale = order.getTotalAmount().scale();
                    if (scale > 2) {
                        results.add(RuleValidationResult.failure(
                                "PO004B", "订单总金额精度错误", "PO_VALIDATION_004B", 
                                "订单总金额小数位数不能超过2位，当前为" + scale + "位"
                        ));
                        logWarning(requestId, orderCode, "total amount has invalid precision: {} decimal places", scale);
                        validationErrors++;
                    }
                } catch (Exception e) {
                    logError(requestId, orderCode, e, "Error checking amount precision: {}", e.getMessage());
                    results.add(RuleValidationResult.failure(
                            "PO004C", "订单金额精度检查异常", "PO_VALIDATION_004C", 
                            "验证订单金额精度时发生异常：" + e.getMessage()
                    ));
                    validationErrors++;
                }
            }
            
            // 验证币种
            if (order.getCurrency() == null || order.getCurrency().trim().isEmpty()) {
                results.add(RuleValidationResult.warning(
                        "PO005", "币种类别未指定", "将使用默认币种：CNY"
                ));
                order.setCurrency("CNY");
                logInfo(requestId, orderCode, "Default currency CNY set");
            } else {
                // 验证币种代码格式（ISO 4217标准，3个大写字母）
                String currency = order.getCurrency().trim();
                if (!currency.matches("^[A-Z]{3}$")) {
                    results.add(RuleValidationResult.failure(
                            "PO005A", "币种类别格式错误", "PO_VALIDATION_005A", 
                            "币种代码必须为3位大写字母，当前值：" + currency
                    ));
                    logWarning(requestId, orderCode, "has invalid currency format: {}", currency);
                    validationErrors++;
                }
            }
            
            // 验证税率
            if (order.getTaxRate() == null) {
                results.add(RuleValidationResult.warning(
                        "PO006", "税率未指定", "将使用默认税率：" + defaultTaxRate
                ));
                order.setTaxRate(defaultTaxRate);
                logInfo(requestId, orderCode, "Default tax rate {} set", defaultTaxRate);
            } else {
                // 验证税率范围合理性（0-100%）
                if (order.getTaxRate() < 0 || order.getTaxRate() > 100) {
                    results.add(RuleValidationResult.failure(
                            "PO006A", "税率范围错误", "PO_VALIDATION_006A", 
                            "税率必须在0-100之间，当前值：" + order.getTaxRate()
                    ));
                    logWarning(requestId, orderCode, "has invalid tax rate: {}", order.getTaxRate());
                    validationErrors++;
                }
            }
            
            // 记录验证结果
            if (validationErrors > 0) {
                logInfo(requestId, orderCode, "amount validation completed with {} errors", validationErrors);
            } else {
                logDebug(requestId, orderCode, "amount validation passed");
            }
            
        } catch (Exception e) {
            logError(requestId, orderCode, e, "Error validating amount info: {}", e.getMessage());
            // 添加一个通用错误结果
            results.add(RuleValidationResult.failure(
                    "PO100", "金额信息验证异常", "PO_VALIDATION_100", 
                    "订单金额信息验证过程中发生异常：" + e.getMessage()
            ));
        }
    }
    
    /**
     * 验证订单明细
     * @param order 采购订单对象
     * 验证订单明细列表
     * <p>对订单中的所有订单项进行全面验证，确保每个订单项的数据完整性和有效性
     * <p>验证流程：
     * <ul>
     * <li>验证订单项列表是否为null或空</li>
     * <li>对每个订单项进行单独验证</li>
     * <li>重新计算订单总金额以确保金额准确性</li>
     * </ul>
     * 
     * @param order 采购订单对象
     * @param results 验证结果列表，用于存储验证失败的结果
    private void validateOrderItems(PurchaseOrder order, List<RuleValidationResult> results) {
        // 参数验证
        if (order == null) {
            results.add(RuleValidationResult.failure(
                    "PO101A", "订单对象为null", "PO_VALIDATION_101", "订单对象不能为空"
            ));
            log.warn("[N/A] Attempting to validate items for null order");
            return;
        }
        
        if (results == null) {
            log.error("[N/A] Results list is null, cannot add validation failures");
            return;
        }
        
        String orderCode = getOrderCode(order);
        String requestId = getRequestId(order.getContext());
        log.debug("[{}] Starting order items validation for order: {}", requestId, orderCode);
        
        try {
            List<PurchaseOrderItem> items = order.getOrderItems();
            int validationErrors = 0;
            
            // 验证订单项列表是否存在
            if (items == null) {
                String errorCode = "PO007A";
                results.add(RuleValidationResult.failure(
                        errorCode, "订单明细列表为null", "PO_VALIDATION_007", "订单明细列表对象不能为空"
                ));
                logWarning(requestId, orderCode, "has null order items list");
                validationErrors++;
                return;
            }
            
            // 验证订单项列表是否为空
            if (items.isEmpty()) {
                String errorCode = "PO007B";
                results.add(RuleValidationResult.failure(
                        errorCode, "订单明细为空", "PO_VALIDATION_007", "订单至少需要包含一个订单项"
                ));
                logWarning(requestId, orderCode, "has empty order items list");
                validationErrors++;
                return;
            }
            
            // 验证订单项数量是否超过限制（如果有业务规则的话）
            final int MAX_ITEMS_ALLOWED = 1000;
            if (items.size() > MAX_ITEMS_ALLOWED) {
                String errorCode = "PO007C";
                results.add(RuleValidationResult.failure(
                        errorCode, "订单项数量超过限制", "PO_VALIDATION_007", 
                        "订单订单项数量不能超过" + MAX_ITEMS_ALLOWED + "个"
                ));
                logWarning(requestId, orderCode, "has too many items: {}, maximum allowed: {}", 
                        requestId, orderCode, items.size(), MAX_ITEMS_ALLOWED);
                validationErrors++;
            }
            
            log.info("[{}] Order {} has {} items to validate", requestId, orderCode, items.size());
            
            long startTime = System.currentTimeMillis();
            int validItemsCount = 0;
            int invalidItemsCount = 0;
            int skippedItemsCount = 0;
            
            // 验证每个订单项
            for (int i = 0; i < items.size(); i++) {
                PurchaseOrderItem item = items.get(i);
                
                // 增加空值检查
                if (item == null) {
                    String errorCode = "PO008A_" + i;
                    results.add(RuleValidationResult.failure(
                            errorCode, "订单项为空", 
                            "PO_VALIDATION_008", 
                            "第" + (i + 1) + "个订单项为空"
                    ));
                    logWarning(requestId, orderCode, "item #{}/{} is null", i + 1, items.size());
                    invalidItemsCount++;
                    skippedItemsCount++;
                    validationErrors++;
                    continue;
                }
                
                try {
                    // 验证订单项基本信息
                    validateSingleOrderItem(item, i, results, orderCode);
                    validItemsCount++;
                } catch (Exception e) {
                    String errorCode = "PO008B_" + i;
                    log.error("[{}] Error validating item #{}/{} for order {}: {}", 
                            requestId, i + 1, items.size(), orderCode, e.getMessage(), e);
                    results.add(RuleValidationResult.failure(
                            errorCode, "订单项验证异常", 
                            "PO_VALIDATION_008", 
                            "第" + (i + 1) + "个订单项验证异常: " + e.getMessage()
                    ));
                    invalidItemsCount++;
                    skippedItemsCount++;
                    validationErrors++;
                }
            }
            
            // 重新计算订单总金额并验证
            try {
                order.calculateTotalAmount();
                BigDecimal totalAmount = order.getTotalAmount();
                
                // 验证计算后的总金额
                if (totalAmount == null) {
                    String errorCode = "PO015A";
                    results.add(RuleValidationResult.failure(
                            errorCode, "订单总金额为null", 
                            "PO_VALIDATION_015", 
                            "订单总金额计算结果为null"
                    ));
                    log.error("[{}] Order {} total amount calculation returned null", requestId, orderCode);
                    validationErrors++;
                } else if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    String errorCode = "PO015B";
                    results.add(RuleValidationResult.failure(
                            errorCode, "订单总金额无效", 
                            "PO_VALIDATION_015", 
                            "订单总金额必须大于0，当前值: " + totalAmount
                    ));
                    log.warn("[{}] Order {} has invalid total amount: {}", requestId, orderCode, totalAmount);
                    validationErrors++;
                } else {
                    log.debug("[{}] Order {} total amount recalculated successfully: {}", 
                            requestId, orderCode, totalAmount);
                }
            } catch (Exception e) {
                String errorCode = "PO015C";
                log.error("[{}] Error calculating order total amount for order {}", 
                         requestId, orderCode, e);
                results.add(RuleValidationResult.failure(
                        errorCode, "订单总金额计算失败", 
                        "PO_VALIDATION_015", 
                        "订单总金额计算失败：" + e.getMessage()
                ));
                validationErrors++;
            }
            
            long endTime = System.currentTimeMillis();
            log.info("[{}] Order items validation completed for order {} in {}ms: {} valid, {} invalid, {} skipped, {} total errors", 
                    requestId, orderCode, (endTime - startTime), validItemsCount, invalidItemsCount, skippedItemsCount, validationErrors);
            
        } catch (Exception e) {
            log.error("[{}] Critical error validating order items for order {}: {}", 
                    requestId, orderCode, e.getMessage(), e);
            // 添加通用错误结果
            results.add(RuleValidationResult.failure(
                    "PO101B", "订单明细验证异常", "PO_VALIDATION_101", "订单明细验证过程中发生异常: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 验证单个订单项的完整性和有效性
     * <p>对订单项进行全面验证，确保所有关键字段符合业务规则
     * <p>验证流程：
     * <ul>
     * <li>验证订单项基本有效性</li>
     * <li>验证商品ID、价格、数量等关键字段</li>
     * <li>验证金额精度和有效性</li>
     * <li>尝试计算各项价格信息并验证</li>
     * </ul>
     * 
     * @param item 订单项对象
     * @param index 订单项在列表中的索引位置
     * @param results 验证结果列表，用于存储验证失败的结果
     * @param orderCode 订单编号
     */
    private void validateSingleOrderItem(PurchaseOrderItem item, int index, 
                                       List<RuleValidationResult> results, String orderCode) {
        // 参数验证
        if (item == null) {
            String errorCode = "PO008_NULL_" + index;
            results.add(RuleValidationResult.failure(
                    errorCode, "订单项为空", 
                    "PO_VALIDATION_008", 
                    "第" + (index + 1) + "个订单项为空对象"
            ));
            log.warn("[N/A] Attempting to validate null order item at index {}", index);
            return;
        }
        
        if (results == null) {
            log.error("[N/A] Results list is null, cannot add validation failures for order {} item {}", 
                    orderCode, index + 1);
            return;
        }
        
        // 由于调用处没有传递requestId，暂时使用默认值
        String requestId = "N/A"; 
        String itemId = item.getItemId() != null ? item.getItemId() : "N/A";
        
        log.debug("[{}] Validating single order item #{}/{} (ID: {}) for order: {}", 
                requestId, index + 1, index + 1, itemId, orderCode);
        
        try {
            long startTime = System.currentTimeMillis();
            int validationErrors = 0;
            
            // 首先检查订单项是否有效
            if (!item.isValid()) {
                String errorCode = "PO008_" + index;
                results.add(RuleValidationResult.failure(
                        errorCode, "订单项无效", 
                        "PO_VALIDATION_008", 
                        "第" + (index + 1) + "个订单项无效，请检查产品信息和数量价格"
                ));
                logWarning(requestId, orderCode, "item #{}/{} (ID: {}) is invalid", 
                        index + 1, index + 1, itemId);
                validationErrors++;
            }
            
            // 验证商品ID
            if (item.getItemId() == null || item.getItemId().trim().isEmpty()) {
                String errorCode = "PO008A_" + index;
                results.add(RuleValidationResult.failure(
                        errorCode, "商品ID不能为空", 
                        "PO_VALIDATION_008A", 
                        "第" + (index + 1) + "个订单项商品ID为空"
                ));
                logWarning(requestId, orderCode, "item #{}/{} has empty item ID", 
                        index + 1, index + 1);
                validationErrors++;
            } else {
                // 验证商品ID格式
                String trimmedItemId = item.getItemId().trim();
                if (!trimmedItemId.matches("[a-zA-Z0-9_\\-]{1,50}")) {
                    String errorCode = "PO008A1_" + index;
                    results.add(RuleValidationResult.failure(
                            errorCode, "商品ID格式不正确", 
                            "PO_VALIDATION_008A", 
                            "第" + (index + 1) + "个订单项商品ID格式不正确，只能包含字母、数字、下划线和连字符，且长度不超过50个字符"
                    ));
                    logWarning(requestId, orderCode, "item #{}/{} (ID: {}) has invalid item ID format", 
                        index + 1, index + 1, trimmedItemId);
                    validationErrors++;
                }
            }
            
            // 验证价格
            if (item.getPrice() == null) {
                String errorCode = "PO008B_" + index;
                results.add(RuleValidationResult.failure(
                        errorCode, "商品价格不能为空", 
                        "PO_VALIDATION_008B", 
                        "第" + (index + 1) + "个订单项商品价格为空"
                ));
                logWarning(requestId, orderCode, "item #{}/{} (ID: {}) has null price", 
                        index + 1, index + 1, itemId);
                validationErrors++;
            } else {
                // 验证价格必须大于0
                if (item.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    String errorCode = "PO008C_" + index;
                    results.add(RuleValidationResult.failure(
                            errorCode, "商品价格必须大于0", 
                            "PO_VALIDATION_008C", 
                            "第" + (index + 1) + "个订单项商品价格必须大于0"
                    ));
                    logWarning(requestId, orderCode, "item #{}/{} (ID: {}) has invalid price: {}", 
                        index + 1, index + 1, itemId, item.getPrice());
                    validationErrors++;
                }
                
                // 验证价格精度（最多4位小数）
                try {
                    int scale = item.getPrice().scale();
                    if (scale > 4) {
                        String errorCode = "PO008C1_" + index;
                        results.add(RuleValidationResult.failure(
                                errorCode, "商品价格精度不正确", 
                                "PO_VALIDATION_008C", 
                                "第" + (index + 1) + "个订单项商品价格小数位数不能超过4位"
                        ));
                        logWarning(requestId, orderCode, "item #{}/{} (ID: {}) has invalid price precision: {} decimal places", 
                        index + 1, index + 1, itemId, scale);
                        validationErrors++;
                    }
                } catch (Exception e) {
                    logWarning(requestId, orderCode, "Error checking price precision for item #{}/{}(ID: {})", 
                            index + 1, index + 1, itemId);
                }
            }
            
            // 验证数量
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                String errorCode = "PO008D_" + index;
                results.add(RuleValidationResult.failure(
                        errorCode, "商品数量无效", 
                        "PO_VALIDATION_008D", 
                        "第" + (index + 1) + "个订单项商品数量必须大于0"
                ));
                logWarning(requestId, orderCode, "item #{}/{} (ID: {}) has invalid quantity: {}", 
                        index + 1, index + 1, itemId, item.getQuantity());
                validationErrors++;
            } else if (item.getQuantity() > Integer.MAX_VALUE / 1000) {
                // 防止数量过大导致金额计算溢出
                String errorCode = "PO008D1_" + index;
                results.add(RuleValidationResult.failure(
                        errorCode, "商品数量过大", 
                        "PO_VALIDATION_008D", 
                        "第" + (index + 1) + "个订单项商品数量过大"
                ));
                logWarning(requestId, orderCode, "item #{}/{} (ID: {}) has excessive quantity: {}", 
                        index + 1, index + 1, itemId, item.getQuantity());
                validationErrors++;
            }
            
            // 验证商品名称
            String productName = item.getProductName();
            if (productName == null || productName.trim().isEmpty()) {
                String errorCode = "PO008E_" + index;
                results.add(RuleValidationResult.failure(
                        errorCode, "商品名称不能为空", 
                        "PO_VALIDATION_008E", 
                        "第" + (index + 1) + "个订单项商品名称为空"
                ));
                logWarning(requestId, orderCode, "item #{}/{} (ID: {}) has empty product name", 
                        index + 1, index + 1, itemId);
                validationErrors++;
            } else if (productName.trim().length() > 200) {
                String errorCode = "PO008E1_" + index;
                results.add(RuleValidationResult.failure(
                        errorCode, "商品名称过长", 
                        "PO_VALIDATION_008E", 
                        "第" + (index + 1) + "个订单项商品名称不能超过200个字符"
                ));
                logWarning(requestId, orderCode, "item #{}/{} (ID: {}) has product name too long: {} characters", 
                        index + 1, index + 1, itemId, productName.trim().length());
                validationErrors++;
            }
            
            // 如果有验证错误，记录但继续尝试计算（容错设计）
            if (validationErrors > 0) {
                logWarning(requestId, orderCode, "item #{}/{} (ID: {}) has {} validation errors but will attempt calculation", 
                        index + 1, index + 1, itemId, validationErrors);
            }
            
            // 尝试计算各种价格信息
            try {
                // 计算订单项金额
                item.calculateTotalPrice();
                
                // 计算不含税价格
                try {
                    item.calculatePriceWithoutTax();
                } catch (Exception e) {
                    logWarning(requestId, orderCode, "Error calculating price without tax for item #{}/{}/{} (ID: {}): {}", 
                            index + 1, index + 1, itemId, e.getMessage());
                }
                
                // 计算税额
                try {
                    item.calculateTaxAmount();
                } catch (Exception e) {
                    logWarning(requestId, orderCode, "Error calculating tax amount for item #{}/{}/{} (ID: {}): {}", 
                            index + 1, index + 1, itemId, e.getMessage());
                }
                
                // 验证计算结果
                BigDecimal totalPrice = item.getTotalPrice();
                if (totalPrice == null) {
                    String errorCode = "PO008F_" + index;
                    results.add(RuleValidationResult.failure(
                            errorCode, "订单项金额为null", 
                            "PO_VALIDATION_008F", 
                            "第" + (index + 1) + "个订单项金额计算结果为null"
                    ));
                    log.warn("[{}] Order {} item #{}/{} (ID: {}) has null calculated total price", 
                            requestId, orderCode, index + 1, index + 1, itemId);
                    validationErrors++;
                } else if (totalPrice.compareTo(BigDecimal.ZERO) < 0) {
                    String errorCode = "PO008F1_" + index;
                    results.add(RuleValidationResult.failure(
                            errorCode, "订单项金额为负数", 
                            "PO_VALIDATION_008F", 
                            "第" + (index + 1) + "个订单项金额为负数: " + totalPrice
                    ));
                    logWarning(requestId, orderCode, "item #{}/{}/{} (ID: {}) has negative calculated total price: {}", 
                            index + 1, index + 1, itemId, totalPrice);
                    validationErrors++;
                } else {
                    // 验证金额精度（最多2位小数）
                    try {
                        int scale = totalPrice.scale();
                        if (scale > 2) {
                            String errorCode = "PO008F2_" + index;
                            results.add(RuleValidationResult.failure(
                                    errorCode, "订单项金额精度不正确", 
                                    "PO_VALIDATION_008F", 
                                    "第" + (index + 1) + "个订单项金额小数位数不能超过2位"
                            ));
                            logWarning(requestId, orderCode, "item #{}/{}/{} (ID: {}) has invalid total price precision: {} decimal places", 
                                    index + 1, index + 1, itemId, scale);
                            validationErrors++;
                        }
                    } catch (Exception e) {
                        logWarning(requestId, orderCode, "Error checking total price precision for item #{}/{}/{} (ID: {})", 
                                index + 1, index + 1, itemId);
                    }
                    
                    logDebug(requestId, orderCode, "item #{}/{}/{} (ID: {}) calculations completed: total={}", 
                            index + 1, index + 1, itemId, totalPrice);
                }
                
            } catch (Exception e) {
                logError(requestId, orderCode, e, "Error calculating item amount for item #{}/{}/{} (ID: {}): {}", 
                        index + 1, index + 1, itemId, e.getMessage());
                String errorCode = "PO014_" + index;
                results.add(RuleValidationResult.failure(
                        errorCode, "订单项金额计算失败", 
                        "PO_VALIDATION_014", 
                        "第" + (index + 1) + "个订单项金额计算失败：" + e.getMessage()
                ));
                validationErrors++;
            }
            
            long endTime = System.currentTimeMillis();
            logInfo(requestId, orderCode, "item #{}/{}/{} (ID: {}) validation completed in {}ms: {} errors", 
                    index + 1, index + 1, itemId, (endTime - startTime), validationErrors);
            
        } catch (Exception e) {
            logError(requestId, orderCode, e, "Critical error validating order item #{}/{}/{} (ID: {}): {}", 
                    index + 1, index + 1, itemId, e.getMessage());
            String errorCode = "PO015_" + index;
            results.add(RuleValidationResult.failure(
                    errorCode, "订单项验证异常", 
                    "PO_VALIDATION_015", 
                    "第" + (index + 1) + "个订单项验证过程中发生异常: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 验证采购订单的供应商信息完整性和有效性
     * <p>对订单中的供应商相关信息进行全面验证，确保满足业务规则要求
     * <p>验证流程：
     * <ul>
     * <li>验证订单和结果列表参数的有效性</li>
     * <li>验证供应商ID的有效性和格式</li>
     * <li>验证供应商名称的有效性和长度</li>
     * <li>验证供应商联系人信息（如存在）</li>
     * <li>验证供应商电话（如存在）</li>
     * <li>验证供应商邮箱（如存在）</li>
     * <li>验证供应商地址（如存在）</li>
     * </ul>
     * 
     * @param order 采购订单对象
     * @param results 验证结果列表，用于存储验证失败的结果
     */
    private void validateSupplierInfo(PurchaseOrder order, List<RuleValidationResult> results) {
        // 参数验证
        if (order == null) {
            String errorCode = "PO102A"; 
            results.add(RuleValidationResult.failure(
                    errorCode, "订单对象为空", "PO_VALIDATION_102", "无法验证空订单的供应商信息"
            ));
            log.warn("[N/A] Attempting to validate supplier info for null order");
            return;
        }
        
        if (results == null) {
            log.error("[N/A] Results list is null, cannot add supplier validation failures");
            return;
        }
        
        String orderCode = getOrderCode(order);
        String requestId = getRequestId(order.getContext());
        log.debug("[{}] Validating supplier info for order: {}", requestId, orderCode);
        
        try {
            long startTime = System.currentTimeMillis();
            int validationErrors = 0;
            int validationWarnings = 0;
            
            // 验证供应商ID
            String supplierId = order.getSupplierId();
            if (supplierId == null || supplierId.trim().isEmpty()) {
                String errorCode = "PO009"; 
                results.add(RuleValidationResult.failure(
                        errorCode, "供应商ID不能为空", "PO_VALIDATION_009", "供应商ID是必填项"
                ));
                logWarning(requestId, orderCode, "has empty supplier ID");
                validationErrors++;
            } else {
                // 验证供应商ID格式
                supplierId = supplierId.trim();
                if (!supplierId.matches("[a-zA-Z0-9]{3,20}")) {
                    String errorCode = "PO009A"; 
                    results.add(RuleValidationResult.failure(
                            errorCode, "供应商ID格式不正确", "PO_VALIDATION_009A", 
                            "供应商ID必须是3-20个字母或数字的组合"
                    ));
                    logWarning(requestId, orderCode, "has invalid supplier ID format: {}", supplierId);
                    validationErrors++;
                }
            }
            
            // 验证供应商名称
            String supplierName = order.getSupplierName();
            if (supplierName == null || supplierName.trim().isEmpty()) {
                String errorCode = "PO010"; 
                results.add(RuleValidationResult.failure(
                        errorCode, "供应商名称不能为空", "PO_VALIDATION_010", "供应商名称是必填项"
                ));
                logWarning(requestId, orderCode, "has empty supplier name");
                validationErrors++;
            } else {
                // 验证供应商名称长度
                supplierName = supplierName.trim();
                if (supplierName.length() < 2) {
                    String errorCode = "PO010A1"; 
                    results.add(RuleValidationResult.failure(
                            errorCode, "供应商名称过短", "PO_VALIDATION_010A", 
                            "供应商名称不能少于2个字符"
                    ));
                    logWarning(requestId, orderCode, "has supplier name too short: {} characters", supplierName.length());
                    validationErrors++;
                } else if (supplierName.length() > 100) {
                    String errorCode = "PO010A"; 
                    results.add(RuleValidationResult.failure(
                            errorCode, "供应商名称过长", "PO_VALIDATION_010A", 
                            "供应商名称不能超过100个字符"
                    ));
                    logWarning(requestId, orderCode, "has supplier name too long: {} characters", supplierName.length());
                    validationErrors++;
                }
            }
            
            // 验证供应商联系方式（如果有）
            try {
                String supplierContact = order.getSupplierContact();
                if (supplierContact != null) {
                    supplierContact = supplierContact.trim();
                    if (supplierContact.isEmpty()) {
                        String errorCode = "PO010B"; 
                        results.add(RuleValidationResult.warning(
                                errorCode, "供应商联系人为空", "PO_VALIDATION_010B", 
                                "供应商联系人为空字符串，建议提供有效的联系人信息"
                        ));
                        logWarning(requestId, orderCode, "has empty supplier contact");
                        validationWarnings++;
                    } else if (supplierContact.length() > 50) {
                        String errorCode = "PO010B1"; 
                        results.add(RuleValidationResult.warning(
                                errorCode, "供应商联系人过长", "PO_VALIDATION_010B", 
                                "供应商联系人姓名不能超过50个字符"
                        ));
                        logWarning(requestId, orderCode, "has supplier contact name too long: {} characters", supplierContact.length());
                        validationWarnings++;
                    }
                }
            } catch (Exception e) {
                log.warn("[{}] Error validating supplier contact for order {}", 
                        requestId, orderCode, e);
                // 联系方式验证失败仅记录警告，不影响主要验证流程
            }
            
            // 验证供应商电话（如果有）
            try {
                String supplierPhone = order.getSupplierPhone();
                if (supplierPhone == null || supplierPhone.trim().isEmpty()) {
                    String errorCode = "PO010C"; 
                    results.add(RuleValidationResult.warning(
                            errorCode, "供应商电话缺失", "PO_VALIDATION_010C", 
                            "建议提供供应商联系电话，以便联系确认订单"
                    ));
                    logWarning(requestId, orderCode, "has no supplier phone number");
                    validationWarnings++;
                } else {
                    // 验证电话格式（简单验证，支持多种格式）
                    String trimmedPhone = supplierPhone.trim();
                    if (!trimmedPhone.matches("[0-9\\+\\-\\s\\(\\)]{5,20}")) {
                        String errorCode = "PO010C1"; 
                        results.add(RuleValidationResult.warning(
                                errorCode, "供应商电话格式不正确", "PO_VALIDATION_010C", 
                                "供应商电话格式不正确，建议提供有效的电话号码"
                        ));
                        logWarning(requestId, orderCode, "has invalid supplier phone format: {}", trimmedPhone);
                        validationWarnings++;
                    }
                }
            } catch (Exception e) {
                log.warn("[{}] Error validating supplier phone for order {}", 
                        requestId, orderCode, e);
            }
            
            // 验证供应商邮箱（如果有）
            try {
                String supplierEmail = order.getSupplierEmail();
                if (supplierEmail != null && !supplierEmail.trim().isEmpty()) {
                    String trimmedEmail = supplierEmail.trim();
                    // 简单的邮箱格式验证
                    if (!trimmedEmail.matches("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")) {
                        String errorCode = "PO010D"; 
                        results.add(RuleValidationResult.warning(
                                errorCode, "供应商邮箱格式不正确", "PO_VALIDATION_010D", 
                                "供应商邮箱格式不正确，建议提供有效的邮箱地址"
                        ));
                        logWarning(requestId, orderCode, "has invalid supplier email format: {}", trimmedEmail);
                        validationWarnings++;
                    } else if (trimmedEmail.length() > 100) {
                        String errorCode = "PO010D1"; 
                        results.add(RuleValidationResult.warning(
                                errorCode, "供应商邮箱过长", "PO_VALIDATION_010D", 
                                "供应商邮箱地址不能超过100个字符"
                        ));
                        logWarning(requestId, orderCode, "has supplier email too long: {} characters", trimmedEmail.length());
                        validationWarnings++;
                    }
                }
            } catch (Exception e) {
                log.warn("[{}] Error validating supplier email for order {}", 
                        requestId, orderCode, e);
            }
            
            // 验证供应商地址（如果有）
            try {
                String supplierAddress = order.getSupplierAddress();
                if (supplierAddress != null) {
                    String trimmedAddress = supplierAddress.trim();
                    if (trimmedAddress.isEmpty()) {
                        String errorCode = "PO010E"; 
                        results.add(RuleValidationResult.warning(
                                errorCode, "供应商地址为空", "PO_VALIDATION_010E", 
                                "供应商地址为空字符串，建议提供有效的地址信息"
                        ));
                        logWarning(requestId, orderCode, "has empty supplier address");
                        validationWarnings++;
                    } else if (trimmedAddress.length() > 200) {
                        String errorCode = "PO010E1"; 
                        results.add(RuleValidationResult.warning(
                                errorCode, "供应商地址过长", "PO_VALIDATION_010E", 
                                "供应商地址不能超过200个字符"
                        ));
                        logWarning(requestId, orderCode, "has supplier address too long: {} characters", trimmedAddress.length());
                        validationWarnings++;
                    }
                }
            } catch (Exception e) {
                log.warn("[{}] Error validating supplier address for order {}", 
                        requestId, orderCode, e);
            }
            
            long endTime = System.currentTimeMillis();
            
            // 记录验证结果统计
            if (validationErrors > 0) {
                logWarning(requestId, orderCode, "supplier validation completed in {}ms with {} errors and {} warnings", 
                        (endTime - startTime), validationErrors, validationWarnings);
            } else if (validationWarnings > 0) {
                logInfo(requestId, orderCode, "supplier validation completed in {}ms with {} warnings", 
                        (endTime - startTime), validationWarnings);
            } else {
                logDebug(requestId, orderCode, "supplier validation passed successfully in {}ms", 
                        (endTime - startTime));
            }
            
        } catch (Exception e) {
            log.error("[{}] Error validating supplier info for order {}: {}", 
                    requestId, orderCode, e.getMessage(), e);
            // 添加一个通用错误结果
            String errorCode = "PO102"; 
            results.add(RuleValidationResult.failure(
                    errorCode, "供应商信息验证异常", "PO_VALIDATION_102", 
                    "订单供应商信息验证过程中发生异常: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 验证交货日期
     * @param order 采购订单对象
     * @param results 验证结果列表
     */
    private void validateDeliveryDate(PurchaseOrder order, List<RuleValidationResult> results) {
        String orderCode = getOrderCode(order);
        String requestId = getRequestId(order.getContext());
        log.debug("[{}] Validating delivery date for order: {}", requestId, orderCode);
        
        try {
            LocalDateTime expectedDeliveryDate = order.getExpectedDeliveryDate();
            
            // 验证交货日期是否为空
            if (expectedDeliveryDate == null) {
                results.add(RuleValidationResult.failure(
                        "PO011", "交货日期不能为空", "PO_VALIDATION_011", "期望交货日期是必填项"
                ));
                logWarning(requestId, orderCode, "has null expected delivery date");
                return;
            }
            
            LocalDateTime currentTime = LocalDateTime.now();
            
            // 验证交货日期不能早于当前日期
            if (expectedDeliveryDate.isBefore(currentTime)) {
                results.add(RuleValidationResult.failure(
                        "PO012", "交货日期无效", "PO_VALIDATION_012", "交货日期不能早于当前日期"
                ));
                logWarning(requestId, orderCode, "has invalid delivery date: {} (before current time: {})", 
                        expectedDeliveryDate, currentTime);
                return;
            }
            
            // 计算交货日期距离现在的天数
            long daysUntilDelivery = ChronoUnit.DAYS.between(currentTime, expectedDeliveryDate);
            logDebug(requestId, orderCode, "delivery date is valid, {} days from now", 
                    daysUntilDelivery);
            
            // 验证交货日期是否合理（例如：不能超过1年）
            LocalDateTime maxAllowedDate = currentTime.plusYears(1);
            if (expectedDeliveryDate.isAfter(maxAllowedDate)) {
                results.add(RuleValidationResult.failure(
                        "PO012A", "交货日期过远", "PO_VALIDATION_012A", 
                        "交货日期不能超过当前日期1年"
                ));
                logWarning(requestId, orderCode, "has delivery date too far in future: {} (max allowed: {})", 
                     expectedDeliveryDate, maxAllowedDate);
                return;
            }
            
            // 验证交货日期是否有足够的提前期（例如：紧急采购除外，需要至少2个工作日）
            boolean isEmergencyPurchase = false;
            try {
                isEmergencyPurchase = order.isEmergencyPurchase();
            } catch (Exception e) {
                logWarning(requestId, orderCode, "Error checking if is emergency purchase: {}", e.getMessage());
            }
            
            if (!isEmergencyPurchase) {
                // 计算2个工作日后的日期作为最小提前期
                LocalDateTime minAllowedDate = calculateMinDeliveryDate(currentTime);
                if (expectedDeliveryDate.isBefore(minAllowedDate)) {
                    results.add(RuleValidationResult.warning(
                            "PO012B", "交货日期可能不足", "PO_VALIDATION_012B", 
                            "标准采购订单建议至少提前2个工作日下单"
                    ));
                    logWarning(requestId, orderCode, "delivery date may be too soon: {} (min suggested: {})", 
                    expectedDeliveryDate, minAllowedDate);
                }
            }
            
            // 记录有效的交货日期信息
            logDebug(requestId, orderCode, "delivery date validation passed: {}, {} days from now", 
                    expectedDeliveryDate, daysUntilDelivery);
            
        } catch (Exception e) {
            logError(requestId, orderCode, "Error validating delivery date: " + e.getMessage(), e);
            // 添加一个通用错误结果
            results.add(RuleValidationResult.failure(
                    "PO103", "交货日期验证异常", "PO_VALIDATION_103", "订单交货日期验证过程中发生异常"
            ));
        }
    }
    
    /**
     * 计算最小允许的交货日期（2个工作日后的日期）
     * @param currentTime 当前时间
     * @return 最小允许的交货日期
     */
    private LocalDateTime calculateMinDeliveryDate(LocalDateTime currentTime) {
        LocalDateTime minDate = currentTime;
        int businessDaysAdded = 0;
        
        // 添加2个工作日
        while (businessDaysAdded < 2) {
            minDate = minDate.plusDays(1);
            // 判断是否为工作日（周一至周五）
            DayOfWeek dayOfWeek = minDate.getDayOfWeek();
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                businessDaysAdded++;
            }
        }
        
        return minDate;
    }
    
    /**
     * 验证紧急采购规则
     * @param order 采购订单对象
     * @param results 验证结果列表
     */
    private void validateUrgentPurchaseRule(PurchaseOrder order, List<RuleValidationResult> results) {
        String orderCode = getOrderCode(order);
        String requestId = getRequestId(order.getContext());
        log.debug("[{}] Validating urgent purchase rule for order: {}", requestId, orderCode);
        
        try {
            // 检查是否为紧急采购
            boolean isEmergency = false;
            try {
                isEmergency = order.isEmergencyPurchase();
            } catch (Exception e) {
                logError(requestId, orderCode, e, "Error checking if order is emergency purchase: {}", e.getMessage());
                results.add(RuleValidationResult.failure(
                        "PO104A", "紧急采购标志检查失败", 
                        "PO_VALIDATION_104A", 
                        "无法确定订单是否为紧急采购: " + e.getMessage()
                ));
                return;
            }
            
            if (isEmergency) {
                logDebug(requestId, orderCode, "is marked as emergency purchase");
                int validationWarnings = 0;
                
                // 验证紧急采购原因
                String emergencyReason = null;
                try {
                    emergencyReason = order.getEmergencyReason();
                    if (emergencyReason == null || emergencyReason.trim().isEmpty()) {
                        results.add(RuleValidationResult.warning(
                                "PO013", "紧急采购原因缺失", 
                                "PO_VALIDATION_013", 
                                "标记为紧急采购但未提供紧急原因，请补充说明"
                        ));
                        logWarning(requestId, orderCode, "is marked as emergency purchase but has no emergency reason");
                        validationWarnings++;
                    } else {
                        String trimmedReason = emergencyReason.trim();
                        if (trimmedReason.length() < 10) {
                            results.add(RuleValidationResult.warning(
                                    "PO013A", "紧急采购原因过于简单", 
                                    "PO_VALIDATION_013A", 
                                    "紧急采购原因描述过于简单，请提供更详细的说明"
                            ));
                            logWarning(requestId, orderCode, "emergency reason is too simple: '{}'", trimmedReason);
                            validationWarnings++;
                        } else if (trimmedReason.length() > 200) {
                            results.add(RuleValidationResult.warning(
                                    "PO013B", "紧急采购原因过长", 
                                    "PO_VALIDATION_013B", 
                                    "紧急采购原因描述过长，请简明扼要"
                            ));
                            logWarning(requestId, orderCode, "emergency reason is too long: {} characters", trimmedReason.length());
                            validationWarnings++;
                        }
                    }
                } catch (Exception e) {
                    logWarning(requestId, orderCode, "Error accessing emergency reason: {}", e.getMessage());
                }
                
                // 验证交货日期
                LocalDateTime expectedDate = order.getExpectedDeliveryDate();
                if (expectedDate != null) {
                    LocalDateTime currentTime = LocalDateTime.now();
                    long daysUntilDelivery = ChronoUnit.DAYS.between(currentTime, expectedDate);
                    
                    logDebug(requestId, orderCode, "days until delivery: {}, urgent threshold: {}", 
                            daysUntilDelivery, urgentDeliveryDays);
                    
                    if (daysUntilDelivery > urgentDeliveryDays) {
                        results.add(RuleValidationResult.warning(
                                "PO013C", "紧急采购交货日期警告", 
                                "PO_VALIDATION_013C", 
                                "标记为紧急采购但交货日期超过" + urgentDeliveryDays + "天，建议确认是否真的紧急"
                        ));
                        logWarning(requestId, orderCode, "is marked as emergency but delivery date is {} days away (threshold: {} days)", 
                                daysUntilDelivery, urgentDeliveryDays);
                        validationWarnings++;
                    } else {
                        logInfo(requestId, orderCode, "qualifies as emergency purchase ({} days until delivery, threshold: {} days)", 
                                daysUntilDelivery, urgentDeliveryDays);
                    }
                } else {
                    // 添加对空交货日期的处理
                    results.add(RuleValidationResult.warning(
                            "PO016", "紧急采购交货日期缺失", 
                            "PO_VALIDATION_016", 
                            "标记为紧急采购但未设置交货日期，请检查"
                    ));
                    logWarning(requestId, orderCode, "is marked as emergency but has no delivery date set");
                    validationWarnings++;
                }
                
                // 验证紧急采购金额限制（如果有设置）
                try {
                    BigDecimal totalAmount = order.getTotalAmount();
                    if (totalAmount != null && emergencyPurchaseThreshold != null) {
                        if (totalAmount.compareTo(emergencyPurchaseThreshold) > 0) {
                            results.add(RuleValidationResult.warning(
                                    "PO013D", "紧急采购金额超限", 
                                    "PO_VALIDATION_013D", 
                                    "紧急采购订单金额超过推荐限额" + emergencyPurchaseThreshold + "，建议特殊审批"
                            ));
                            logWarning(requestId, orderCode, "emergency purchase amount ({}) exceeds threshold ({})", 
                                    totalAmount, emergencyPurchaseThreshold);
                            validationWarnings++;
                        }
                    }
                } catch (Exception e) {
                    logWarning(requestId, orderCode, "Error validating emergency purchase amount: {}", e.getMessage());
                }
                
                // 记录验证结果统计
                if (validationWarnings > 0) {
                    logWarning(requestId, orderCode, "emergency purchase validation completed with {} warnings", validationWarnings);
                } else {
                    logInfo(requestId, orderCode, "emergency purchase validation passed successfully");
                }
            } else {
                logDebug(requestId, orderCode, "is not marked as emergency purchase, skipping emergency validation");
            }
        } catch (Exception e) {
            logError(requestId, orderCode, e, "Critical error validating urgent purchase rule: {}", e.getMessage());
            // 添加一个通用错误结果
            results.add(RuleValidationResult.failure(
                    "PO104", "紧急采购规则验证异常", 
                    "PO_VALIDATION_104", 
                    "订单紧急采购规则验证过程中发生异常: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 计算订单总金额
     * <p>遍历所有订单项，计算每个有效的订单项金额并累加得到订单总金额
     * <p>处理逻辑：
     * <ul>
     * <li>验证输入参数的有效性</li>
     * <li>跳过无效的订单项（null、价格为null、数量无效等）</li>
     * <li>确保金额计算结果的有效性</li>
     * <li>对最终结果进行精度处理（保留2位小数）</li>
     * </ul>
     * 
     * @param items 订单项列表
     * @param requestId 请求ID，用于日志跟踪
     * @param orderCode 订单编号
     * @return 计算得到的订单总金额，如果计算失败则返回0
     */
    private BigDecimal calculateTotalOrderAmount(List<PurchaseOrderItem> items, String requestId, String orderCode) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 参数验证
            if (requestId == null || requestId.trim().isEmpty()) {
                requestId = "UNKNOWN";
                log.debug("[{}] Using default requestId for order amount calculation", requestId);
            }
            
            if (orderCode == null || orderCode.trim().isEmpty()) {
                orderCode = "UNKNOWN_ORDER";
                log.debug("[{}] Using default orderCode: {}", requestId, orderCode);
            }
            
            log.debug("[{}] Calculating total order amount for order {} with {} items", 
                    requestId, orderCode, items != null ? items.size() : 0);
            
            BigDecimal total = BigDecimal.ZERO;
            
            if (items == null || items.isEmpty()) {
                log.warn("[{}] Empty or null items list when calculating total for order {}", 
                        requestId, orderCode);
                return total;
            }
            
            // 定义常量用于金额验证
            final BigDecimal MAX_SINGLE_ITEM_AMOUNT = new BigDecimal("1000000000"); // 10亿
            final BigDecimal MAX_TOTAL_ORDER_AMOUNT = new BigDecimal("10000000000"); // 100亿
            
            int processedItems = 0;
            int skippedItems = 0;
            int pricePrecisionErrors = 0;
            
            for (PurchaseOrderItem item : items) {
                try {
                    if (item == null) {
                        logDebug(requestId, orderCode, "Skipping null item");
                        skippedItems++;
                        continue;
                    }
                    
                    BigDecimal price = item.getPrice();
                    Integer quantity = item.getQuantity();
                    String itemId = item.getItemId() != null ? item.getItemId() : "N/A";
                    String itemName = "";
                    try {
                        itemName = item.getItemName() != null ? item.getItemName() : "Unnamed Item";
                    } catch (Exception e) {
                        itemName = "Name Unavailable";
                    }
                    
                    if (price == null) {
                        logWarning(requestId, orderCode, "item has null price, ID: {}, Name: {}", itemId, itemName);
                        skippedItems++;
                        continue;
                    }
                    
                    // 检查价格精度
                    int priceScale = price.scale();
                    if (priceScale > 4) {
                        logWarning(requestId, orderCode, "item has price with excessive precision ({} decimal places): {}, ID: {}, Name: {}", 
                                priceScale, price, itemId, itemName);
                        price = price.setScale(4, RoundingMode.HALF_UP);
                        pricePrecisionErrors++;
                    }
                    
                    if (price.compareTo(BigDecimal.ZERO) < 0) {
                        logWarning(requestId, orderCode, "item has negative price: {}, ID: {}, Name: {}", price, itemId, itemName);
                        skippedItems++;
                        continue;
                    }
                    
                    if (quantity == null || quantity <= 0) {
                        logWarning(requestId, orderCode, "item has invalid quantity: {}, ID: {}, Name: {}", quantity, itemId, itemName);
                        skippedItems++;
                        continue;
                    }
                    
                    // 计算单个订单项的总金额
                    BigDecimal itemTotal;
                    try {
                        itemTotal = price.multiply(BigDecimal.valueOf(quantity));
                        
                        // 检查单项目金额限制
                        if (itemTotal.compareTo(MAX_SINGLE_ITEM_AMOUNT) > 0) {
                            logWarning(requestId, orderCode, "item amount exceeds maximum allowed value: {}, ID: {}, Name: {}", 
                                    itemTotal, itemId, itemName);
                            skippedItems++;
                            continue;
                        }
                        
                        itemTotal = itemTotal.setScale(2, RoundingMode.HALF_UP);
                    } catch (ArithmeticException e) {
                        logError(requestId, orderCode, e, "Arithmetic error calculating amount for item {}: {}", itemId, e.getMessage());
                        skippedItems++;
                        continue;
                    }
                    
                    if (itemTotal.compareTo(BigDecimal.ZERO) < 0) {
                        logWarning(requestId, orderCode, "item has negative total amount: {}, ID: {}, Name: {}", itemTotal, itemId, itemName);
                        skippedItems++;
                        continue;
                    }
                    
                    // 检查累计后是否会溢出
                    if (total.add(itemTotal).compareTo(MAX_TOTAL_ORDER_AMOUNT) > 0) {
                        logWarning(requestId, orderCode, "total amount would exceed maximum allowed value after adding item: {}, ID: {}, Name: {}", 
                                itemTotal, itemId, itemName);
                        skippedItems++;
                        continue;
                    }
                    
                    total = total.add(itemTotal);
                    processedItems++;
                    
                    logDebug(requestId, orderCode, "item {} ({}) processed: price={}, quantity={}, subtotal={}", 
                            itemId, itemName, price, quantity, itemTotal);
                    
                } catch (Exception e) {
                    logError(requestId, orderCode, e, "Error processing item: {}", e.getMessage());
                    skippedItems++;
                }
            }
            
            // 检查最终计算结果
            if (total.compareTo(BigDecimal.ZERO) < 0) {
                logError(requestId, orderCode, "calculated negative total amount: {}", total);
                return BigDecimal.ZERO;
            }
            
            // 对最终结果进行精度处理
            BigDecimal finalTotal = total.setScale(2, RoundingMode.HALF_UP);
            
            long endTime = System.currentTimeMillis();
            long processingTime = endTime - startTime;
            
            // 详细的处理结果日志
            if (skippedItems > 0 || pricePrecisionErrors > 0) {
                logWarning(requestId, orderCode, "total amount calculated: {}, processed {}/{}" +
                           " items, skipped: {}, price precision warnings: {}, time: {}ms", 
                           finalTotal, processedItems, items.size(), skippedItems, 
                           pricePrecisionErrors, processingTime);
            } else {
                logInfo(requestId, orderCode, "total amount calculated: {}, processed {}/{}" +
                        " items, skipped: {}, price precision warnings: {}, time: {}ms", 
                        finalTotal, processedItems, items.size(), skippedItems, 
                        pricePrecisionErrors, processingTime);
            }
            
            // 性能监控
            if (processingTime > 1000) {
                logWarning(requestId, orderCode, "amount calculation took unusually long: {}ms", processingTime);
            }
            
            return finalTotal;
            
        } catch (Exception e) {
            logError(requestId, orderCode, e, "Critical error calculating order total: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }
}