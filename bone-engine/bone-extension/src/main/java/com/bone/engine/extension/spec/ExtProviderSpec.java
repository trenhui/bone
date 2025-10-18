package com.bone.engine.extension.spec;

import com.bone.engine.extension.ExtPointConstants;
import lombok.Builder;
import lombok.Data;

/**
 * 扩展点接口规范
 *
 * @author renhui.trh 2023-10-30
 */
@Data
@Builder
public class ExtProviderSpec {
    private String tenantCode;
    private String bizCode;
    private String useCase;
    private String scenario;
    private String expression;

    /**
     * 生成业务标识字符串，用于扩展点路由匹配
     * 
     * @return 业务标识字符串
     */
    public String getBusinessIdentity() {
        return tenantCode + ExtPointConstants.SEPARATOR
                + bizCode + ExtPointConstants.SEPARATOR
                + useCase + ExtPointConstants.SEPARATOR
                + scenario;
    }

    /**
     * 生成默认业务标识字符串（所有维度使用默认值）
     * 
     * @return 默认业务标识字符串
     */
    public String getDefaultBusinessIdentity() {
        return ExtPointConstants.DEFAULT_VALUE + ExtPointConstants.SEPARATOR
                + ExtPointConstants.DEFAULT_VALUE + ExtPointConstants.SEPARATOR
                + ExtPointConstants.DEFAULT_VALUE + ExtPointConstants.SEPARATOR
                + ExtPointConstants.DEFAULT_VALUE;
    }
}
