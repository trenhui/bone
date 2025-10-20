package com.bone.engine.extension.metadata;

import java.io.Serializable;
import java.util.List;

/**
 * 扩展点方法元数据模型
 * <p>
 * 用于存储扩展点接口中方法的详细信息
 * </p>
 * 
 * @since 1.0.0
 */
public class ExtPointMethodMetadata implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // 方法基本信息
    private String methodName;              // 方法名
    private String description;             // 方法描述
    private String returnType;              // 返回类型
    private List<String> paramTypes;        // 参数类型列表
    private List<String> paramNames;        // 参数名称列表
    private List<String> paramDescriptions; // 参数描述列表
    
    // 异常信息
    private List<String> exceptions;        // 抛出的异常列表
    
    // 方法特性
    private boolean isDeprecated;           // 是否废弃
    private String deprecatedMessage;       // 废弃说明
    private boolean isAsync;                // 是否异步方法
    
    // 调用统计
    private long invokeCount;               // 调用次数
    private double avgInvokeTime;           // 平均调用时间(ms)
    
    // 构造函数和getter/setter方法
    public ExtPointMethodMetadata() {
    }
    
    public String getMethodName() {
        return methodName;
    }
    
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getReturnType() {
        return returnType;
    }
    
    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }
    
    public List<String> getParamTypes() {
        return paramTypes;
    }
    
    public void setParamTypes(List<String> paramTypes) {
        this.paramTypes = paramTypes;
    }
    
    public List<String> getParamNames() {
        return paramNames;
    }
    
    public void setParamNames(List<String> paramNames) {
        this.paramNames = paramNames;
    }
    
    public List<String> getParamDescriptions() {
        return paramDescriptions;
    }
    
    public void setParamDescriptions(List<String> paramDescriptions) {
        this.paramDescriptions = paramDescriptions;
    }
    
    public List<String> getExceptions() {
        return exceptions;
    }
    
    public void setExceptions(List<String> exceptions) {
        this.exceptions = exceptions;
    }
    
    public boolean isDeprecated() {
        return isDeprecated;
    }
    
    public void setDeprecated(boolean isDeprecated) {
        this.isDeprecated = isDeprecated;
    }
    
    public String getDeprecatedMessage() {
        return deprecatedMessage;
    }
    
    public void setDeprecatedMessage(String deprecatedMessage) {
        this.deprecatedMessage = deprecatedMessage;
    }
    
    public boolean isAsync() {
        return isAsync;
    }
    
    public void setAsync(boolean isAsync) {
        this.isAsync = isAsync;
    }
    
    public long getInvokeCount() {
        return invokeCount;
    }
    
    public void setInvokeCount(long invokeCount) {
        this.invokeCount = invokeCount;
    }
    
    public double getAvgInvokeTime() {
        return avgInvokeTime;
    }
    
    public void setAvgInvokeTime(double avgInvokeTime) {
        this.avgInvokeTime = avgInvokeTime;
    }
}