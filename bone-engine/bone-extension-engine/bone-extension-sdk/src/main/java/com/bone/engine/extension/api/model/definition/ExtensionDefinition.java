package com.bone.engine.extension.api.model.definition;

import com.bone.engine.extension.support.config.ExtPointConstants;
import lombok.Builder;
import lombok.Data;
import java.io.Serializable;

/**
 * 扩展实现核心定义 - 路由匹配对象
 */
@Data
@Builder
public class ExtensionDefinition implements Comparable<ExtensionDefinition>, Serializable {
    private static final long serialVersionUID = 1L;

    private String code;
    private ExtensionPointDefinition point;
    private Class<?> implClass;
    private Object instance;

    // 路由维度
    private String tenant = "*";
    private String bizCode = "*";
    private String useCase = "*";
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

    public String getBizIdentity() {
        return tenant + ExtPointConstants.SEPARATOR
                + bizCode + ExtPointConstants.SEPARATOR
                + useCase + ExtPointConstants.SEPARATOR
                + scenario;
    }


    @Override
    public int compareTo(ExtensionDefinition o) {
        return Integer.compare(this.order, o.order);
    }
}