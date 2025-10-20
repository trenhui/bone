package com.bone.engine.extension.exception;

/**
 * 扩展点未找到异常，表示无法找到匹配的扩展实现
 * <p>
 * 当根据业务上下文无法定位到任何合适的扩展实现时抛出此异常
 * 
 * @author bone team
 */
public class ExtensionNotFoundException extends ExtensionException {

    /**
     * 异常错误码常量
     */
    public static final String ERROR_CODE = "EXTENSION_NOT_FOUND";

    /**
     * 目标扩展点接口类名
     */
    private final String targetInterface;

    /**
     * 业务上下文标识
     */
    private final String businessIdentity;

    /**
     * 创建扩展点未找到异常
     * 
     * @param targetInterface 目标扩展点接口类名
     * @param businessIdentity 业务上下文标识
     */
    public ExtensionNotFoundException(String targetInterface, String businessIdentity) {
        super(ERROR_CODE, String.format(
                "No extension provider found for interface: %s with business identity: %s. " +
                "Ensure extensions are properly registered and @Extension annotations are correctly configured. " +
                "Check if there is a default implementation with bizCode='DEFAULT'.",
                targetInterface, businessIdentity
        ));
        this.targetInterface = targetInterface;
        this.businessIdentity = businessIdentity;
    }

    /**
     * 创建扩展点未找到异常
     * 
     * @param targetInterface 目标扩展点接口类名
     * @param businessIdentity 业务上下文标识
     * @param cause 根本原因异常
     */
    public ExtensionNotFoundException(String targetInterface, String businessIdentity, Throwable cause) {
        super(ERROR_CODE, String.format(
                "Failed to find extension provider for interface: %s with business identity: %s",
                targetInterface, businessIdentity
        ), cause);
        this.targetInterface = targetInterface;
        this.businessIdentity = businessIdentity;
    }

    /**
     * 获取目标扩展点接口类名
     * 
     * @return 接口类名
     */
    public String getTargetInterface() {
        return targetInterface;
    }

    /**
     * 获取业务上下文标识
     * 
     * @return 业务标识
     */
    public String getBusinessIdentity() {
        return businessIdentity;
    }

    @Override
    public String toString() {
        return "ExtensionNotFoundException{" +
                "errorCode='" + ERROR_CODE + "'" +
                ", targetInterface='" + targetInterface + "'" +
                ", businessIdentity='" + businessIdentity + "'" +
                '}' + 
                ", message='" + getMessage() + "'" +
                '}';
    }
}