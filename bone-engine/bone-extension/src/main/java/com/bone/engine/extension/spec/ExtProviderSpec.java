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

    public String getBizIdentity() {
        return tenantCode + ExtPointConstants.SEPARATOR
                + bizCode + ExtPointConstants.SEPARATOR
                + useCase + ExtPointConstants.SEPARATOR
                + scenario;
    }

    public String getDefaultBizIdentity() {
        return ExtPointConstants.DEFAULT_VALUE + ExtPointConstants.SEPARATOR
                + ExtPointConstants.DEFAULT_VALUE + ExtPointConstants.SEPARATOR
                + ExtPointConstants.DEFAULT_VALUE + ExtPointConstants.SEPARATOR
                + ExtPointConstants.DEFAULT_VALUE;
    }
}
