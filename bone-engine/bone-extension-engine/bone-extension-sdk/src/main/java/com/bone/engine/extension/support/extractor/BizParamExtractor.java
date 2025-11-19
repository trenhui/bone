package com.bone.engine.extension.support.extractor;

public interface BizParamExtractor<T> {
    String getTenantCode(T data);
    String getBizCode(T data);
    String getUseCase(T data);
    String getScenario(T data);
}