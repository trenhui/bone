package com.bone.engine.extension.api.model.definition;

import lombok.Data;
import java.io.Serializable;

/**
 * 扩展点核心定义 - 参与路由决策
 */
@Data
public class ExtensionPointDefinition implements Serializable {
    private static final long serialVersionUID = 1L;

    private String code;
    private String name;
    private String description = "";
    private String version = "1.0.0";
    private boolean transactional = false;
    private int timeoutSeconds = 30;
    private boolean singleton = true;
    private Class<?> interfaceType;
}