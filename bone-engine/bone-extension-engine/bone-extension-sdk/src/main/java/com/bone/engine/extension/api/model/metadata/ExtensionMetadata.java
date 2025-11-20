package com.bone.engine.extension.api.model.metadata;

import lombok.Data;
import java.io.Serializable;

/**
 * @Extension 注解原始元数据
 */
@Data
public class ExtensionMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    private String value = "";
    private String description = "";

    // 路由维度
    private String tenant = "*";
    private String biz = "*";
    private String scenario = "*";
    private String env = "*";
    private String version = "1.0.0";

    // 路由控制
    private int order = 100;
    private int weight = 100;
    private int traffic = 100;
    private boolean primary = false;
    private boolean enabled = true;

    // 高级配置
    private String condition = "";
    private String[] tags = {};
    private String startTime = "";
    private String endTime = "";
    private boolean async = false;
    private int timeout = 0;

    private Class<?> annotatedClass;
    private Class<?> extensionPointInterface;
}