package com.bone.metadata.sdk.domain.enums;

/**
 * 元数据 SDK 的部署模式枚举
 */
public enum DeploymentMode {

    /**
     * 内嵌部署模式：
     * 元数据 SDK 以 Jar 包形式与宿主应用一起部署，
     * 元数据表直接放在宿主应用数据库中，由 SDK 本地访问。
     */
    EMBEDDED("EMBEDDED", "内嵌部署，SDK 与宿主应用一起部署，访问本地数据库中的元数据表"),

    /**
     * 远程服务部署模式：
     * 元数据 SDK 以远程 API 服务形式部署，宿主应用通过远程调用访问元数据服务。
     */
    REMOTE("REMOTE", "远程部署，宿主应用通过 API 远程调用元数据服务");

    private final String code;
    private final String description;

    DeploymentMode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
