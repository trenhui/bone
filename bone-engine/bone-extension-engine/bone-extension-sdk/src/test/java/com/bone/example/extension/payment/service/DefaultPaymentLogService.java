package com.bone.example.extension.payment.service;

import com.bone.example.extension.payment.PaymentResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 默认支付日志服务实现类
 * <p>
 * 提供支付操作的标准化日志记录服务，包括支付请求日志、支付结果日志和异常日志
 * 支持详细的交易信息记录和格式化输出
 */
@Service
public class DefaultPaymentLogService implements PaymentLogService {
    
    // 日志格式和常量定义
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String NA_PLACEHOLDER = "N/A";
    private static final String ZERO_AMOUNT_PLACEHOLDER = "0";
    private static final String SUCCESS_STATUS = "SUCCESS";
    private static final String FAILURE_STATUS = "FAILURE";
    private static final String UNKNOWN_ERROR = "未知错误";
    private static final String UNKNOWN_CODE = "UNKNOWN";
    
    // 日志前缀常量
    private static final String PAYMENT_LOG_PREFIX = "[PAYMENT_LOG]";
    private static final String PAYMENT_EXCEPTION_LOG_PREFIX = "[PAYMENT_EXCEPTION_LOG]";
    private static final String PAYMENT_REQUEST_LOG_PREFIX = "[PAYMENT_REQUEST_LOG]";
    
    /**
     * 记录支付结果日志
     * <p>
     * 记录完整的支付结果信息，包括交易ID、金额、状态等关键信息
     * 成功时记录常规信息，失败时包含详细的错误信息
     * 
     * @param result 支付结果对象，包含完整的支付信息，不能为空
     * @throws IllegalArgumentException 当支付结果为空时抛出
     */
    @Override
    public void logPayment(final PaymentResult result) {
        // 严格的参数验证
        validatePaymentResult(result);
        
        // 构建结构化的日志内容
        final StringBuilder logBuilder = new StringBuilder()
                .append(PAYMENT_LOG_PREFIX)
                .append(formatCurrentTimestamp())
                .append(formatField("用户ID", safeValue(result.getUserId())))
                .append(formatField("交易ID", safeValue(result.getTransactionId())))
                .append(formatField("状态", result.isSuccess() ? SUCCESS_STATUS : FAILURE_STATUS))
                .append(formatField("最终金额", safeValue(result.getFinalAmount() != null ? result.getFinalAmount().toString() : ZERO_AMOUNT_PLACEHOLDER)))
                .append(formatField("扣除积分", String.valueOf(result.getPointsDeducted())));
        
        // 失败时添加错误详情
        if (!result.isSuccess()) {
            logBuilder.append(formatField("错误码", safeValue(result.getErrorCode())))                
                    .append(formatField("错误信息", safeValue(result.getErrorMessage())));
        }
        
        // 输出日志
        System.out.println(logBuilder.toString());
    }
    
    /**
     * 记录支付异常日志
     * <p>
     * 专门用于记录支付过程中发生的异常情况，包含完整的错误上下文信息
     * 
     * @param transactionId 交易ID，如果有
     * @param orderId 订单ID
     * @param errorMessage 错误消息
     * @param errorCode 错误码
     */
    @Override
    public void logPaymentException(final String transactionId, final String orderId, 
                                   final String errorMessage, final String errorCode) {
        // 构建结构化的异常日志
        final StringBuilder logBuilder = new StringBuilder()
                .append(PAYMENT_EXCEPTION_LOG_PREFIX)
                .append(formatCurrentTimestamp())
                .append(formatField("订单ID", safeValue(orderId)))
                .append(formatField("交易ID", safeValue(transactionId)))
                .append(formatField("错误码", safeValue(errorCode, UNKNOWN_CODE)))
                .append(formatField("错误消息", safeValue(errorMessage, UNKNOWN_ERROR)));
        
        // 输出到标准错误流
        System.err.println(logBuilder.toString());
    }
    
    /**
     * 记录支付请求日志
     * <p>
     * 记录支付请求的详细信息，用于请求追踪和问题排查
     * 包含订单、用户、金额等核心信息
     * 
     * @param orderId 订单ID
     * @param userId 用户ID
     * @param amount 支付金额
     * @param paymentMethod 支付方式
     * @param requestTime 请求时间戳
     */
    @Override
    public void logPaymentRequest(final String orderId, final String userId, 
                                 final String amount, final String paymentMethod, 
                                 final long requestTime) {
        // 构建结构化的请求日志
        final StringBuilder logBuilder = new StringBuilder()
                .append(PAYMENT_REQUEST_LOG_PREFIX)
                .append(formatCurrentTimestamp())
                .append(formatField("订单ID", safeValue(orderId)))
                .append(formatField("用户ID", safeValue(userId)))
                .append(formatField("金额", safeValue(amount, ZERO_AMOUNT_PLACEHOLDER)))
                .append(formatField("支付方式", safeValue(paymentMethod)))
                .append(formatField("请求时间戳", String.valueOf(requestTime)));
        
        // 输出日志
        System.out.println(logBuilder.toString());
    }
    
    /**
     * 验证支付结果对象的有效性
     * 
     * @param result 待验证的支付结果对象
     * @throws IllegalArgumentException 当支付结果为空时抛出
     */
    private void validatePaymentResult(final PaymentResult result) {
        if (result == null) {
            throw new IllegalArgumentException("支付结果对象不能为空");
        }
    }
    
    /**
     * 格式化字段为日志输出格式
     * 
     * @param fieldName 字段名称
     * @param fieldValue 字段值
     * @return 格式化后的字段字符串
     */
    private String formatField(final String fieldName, final String fieldValue) {
        return String.format(" [%s: %s]", fieldName, fieldValue);
    }
    
    /**
     * 获取当前时间戳的格式化字符串
     * 
     * @return 格式化的时间戳字符串
     */
    private String formatCurrentTimestamp() {
        return formatField("时间", DATE_TIME_FORMATTER.format(LocalDateTime.now()));
    }
    
    /**
     * 安全获取字符串值，避免null
     * 
     * @param value 原始值
     * @return 安全的值，如果原始值为null则返回NA_PLACEHOLDER
     */
    private String safeValue(final String value) {
        return value != null ? value : NA_PLACEHOLDER;
    }
    
    /**
     * 安全获取字符串值，允许自定义默认值
     * 
     * @param value 原始值
     * @param defaultValue 默认值
     * @return 安全的值，如果原始值为null则返回默认值
     */
    private String safeValue(final String value, final String defaultValue) {
        return value != null ? value : defaultValue;
    }
}