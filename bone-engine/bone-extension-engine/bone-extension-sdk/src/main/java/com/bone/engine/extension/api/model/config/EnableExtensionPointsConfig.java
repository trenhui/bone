package com.bone.engine.extension.api.model.config;

import lombok.Data;

import java.io.Serializable;

/**
 * @EnableExtensionPoints 注解原始元数据
 */
@Data
public class EnableExtensionPointsConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private String[] basePackages = {};
    private String customRouter = "";
    private String customExecutor = "";
    private boolean cacheEnabled = true;
    private boolean metricsEnabled = true;
    private String routingStrategy = "default";
    private boolean strictMode = false;
    private int timeout = 30;
}