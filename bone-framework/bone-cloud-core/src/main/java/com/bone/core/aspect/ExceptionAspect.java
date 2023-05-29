package com.bone.core.aspect;

import com.bone.core.result.Result;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintViolationException;

/**
 * @author renhui.trh
 */
@Aspect
@Component
public class ExceptionAspect {
    @Around("@annotation(ExceptionHandler)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        Object proceed = null;
        try {
            proceed = joinPoint.proceed();
        } catch (ConstraintViolationException cve) {
            return Result.error(cve.getMessage());
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
        return proceed;
    }
}