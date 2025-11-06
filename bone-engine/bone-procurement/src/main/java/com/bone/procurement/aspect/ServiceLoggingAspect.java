package com.bone.procurement.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 服务层日志切面
 * 统一记录服务层方法的调用日志，包括参数、执行时间和结果
 * 
 * @author bone
 */
@Aspect
@Component
@Slf4j
public class ServiceLoggingAspect {

    /**
     * 定义切点：拦截所有service包下的方法
     */
    @Pointcut("execution(* com.bone.procurement.service.*.*(..))")
    public void serviceMethods() {}

    /**
     * 环绕通知，记录方法调用的详细日志
     * @param joinPoint 连接点
     * @return 方法执行结果
     * @throws Throwable 方法执行过程中的异常
     */
    @Around("serviceMethods()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        
        // 记录方法开始调用的日志，包括参数信息
        log.info("[{}] 开始执行，参数: {}", methodName, Arrays.toString(joinPoint.getArgs()));
        
        // 记录开始时间，用于计算执行时间
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        
        try {
            // 执行目标方法
            Object result = joinPoint.proceed();
            
            // 停止计时
            stopWatch.stop();
            
            // 记录方法执行成功的日志，包括执行时间和结果（只记录结果是否为空）
            log.info("[{}] 执行成功，耗时: {}ms，结果: {}", 
                    methodName, 
                    stopWatch.getTotalTimeMillis(), 
                    result != null ? "[非空]" : "[空]");
            
            return result;
        } catch (Exception e) {
            // 停止计时
            stopWatch.stop();
            
            // 记录方法执行异常的日志
            log.error("[{}] 执行异常，耗时: {}ms，异常: {}", 
                    methodName, 
                    stopWatch.getTotalTimeMillis(), 
                    e.getMessage(), 
                    e);
            
            // 重新抛出异常，不影响原有异常处理流程
            throw e;
        }
    }
}