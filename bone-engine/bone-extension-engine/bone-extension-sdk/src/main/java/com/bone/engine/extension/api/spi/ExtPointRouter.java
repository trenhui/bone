package com.bone.engine.extension.api.spi;

import com.bone.engine.extension.support.context.BizContext;

import java.util.Map;

// com.bone.extension.api.spi.ExtPointRouter
public interface ExtPointRouter {
    <T> T route(Class<T> extPointClass, BizContext<?> context);
    void clearCache(Class<?> extPointClass);
    <T> void registerImplementation(Class<T> extPointClass, T implementation);
    <T> void unregisterImplementation(Class<T> extPointClass, T implementation);
    <T> T getDefaultImplementation(Class<T> extPointClass);
    Map<String, Map<String, Long>> getRouteStats();
}