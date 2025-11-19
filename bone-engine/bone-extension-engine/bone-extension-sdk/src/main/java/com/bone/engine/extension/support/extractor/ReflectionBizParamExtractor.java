package com.bone.engine.extension.support.extractor;

import com.bone.engine.extension.ExtPointConstants;

import java.lang.reflect.Field;

public class ReflectionBizParamExtractor<T> implements BizParamExtractor<T> {


    @Override
    public String getTenantCode(T data) {
        return extractFieldValue(data, "tenantCode");
    }


    @Override
    public String getBizCode(T data) {
        return extractFieldValue(data, "bizCode");
    }


    @Override
    public String getUseCase(T data) {
        return extractFieldValue(data, "useCase");
    }


    @Override
    public String getScenario(T data) {
        return extractFieldValue(data, "scenario");
    }



    private String extractFieldValue(T data, String fieldName) {
        try {
            Field field = data.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(data);
            return value != null ? value.toString() : ExtPointConstants.DEFAULT_VALUE;
        } catch (Exception e) {
            return ExtPointConstants.DEFAULT_VALUE;
        }
    }

}