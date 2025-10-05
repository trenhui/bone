package com.bone.engine.extension.invoker;

import com.bone.core.exception.BizException;
import com.bone.core.util.ReflectionUtil;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
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
            log.info("ExtPoint " + target.getClass().getCanonicalName() + "." + method.getName() + " method invoke");
            return result;
        } catch (InvocationTargetException ex) {
            // 捕获 InvocationTargetException 并转换为业务异常
            Throwable cause = ex.getCause();  // 获取封装的原始异常
            if (cause instanceof BizException) {
                // 将具体异常转换为业务异常
                throw BizException.of(500, "A specific error occurred: " + cause.getMessage());
            } else {
                // 对于其他异常，转换为通用的业务异常
                throw BizException.of(500, "An error occurred during method invocation: " + cause.getMessage());
            }
        } catch (IllegalAccessException | IllegalArgumentException e) {
            // 处理其他反射异常
            log.error("其他反射异常", e);
            throw BizException.of(500, "An error occurred during method invocation: " + e.getMessage());
        }

    }
}
