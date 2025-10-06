package com.bone.engine.extension.invoker;

import com.bone.core.exception.BizException;
import com.bone.core.util.ReflectionUtil;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * ExtPointInvocationHandler
 *
 * @author renhui.trh 2023-10-2
 */
@Slf4j
public class ExtPointInvocationHandler {

    public static Object invoke(Object target, Method method, Object[] args) {
        try {
            Object result = ReflectionUtil.invokeMethod(target, method.getName(), args);
            log.info("ExtPoint {}.{} method invoke",
                    target.getClass().getCanonicalName(), method.getName());
            return result;
        } catch (ReflectionUtil.ReflectionException ex) {
            // 处理反射工具类抛出的异常
            Throwable cause = ex.getCause();
            if (cause instanceof BizException) {
                throw (BizException) cause;
            } else {
                log.error("Extension point method invocation failed: {}.{}",
                        target.getClass().getSimpleName(), method.getName(), ex);
                throw BizException.of(500, "Extension point execution failed: " +
                        (cause != null ? cause.getMessage() : ex.getMessage()));
            }
        } catch (Exception e) {
            // 处理其他异常
            log.error("Unexpected error during extension point invocation: {}.{}",
                    target.getClass().getSimpleName(), method.getName(), e);
            throw BizException.of(500, "Unexpected error: " + e.getMessage());
        }
    }
}