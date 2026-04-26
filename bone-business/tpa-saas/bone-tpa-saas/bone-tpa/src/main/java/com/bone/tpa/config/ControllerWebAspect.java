package com.bone.tpa.config;

import com.bone.core.result.Result;
import com.bone.core.util.BizContext;
import com.bone.core.util.BizContextUtils;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.pkh.cloud.auth.sdk.util.UserUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.UUID;

@Aspect
@Component
@Slf4j
public class ControllerWebAspect {
    public static final String MDC_TRACE_ID = "traceId";

    @Resource
    UserUtil userUtil;
    @Pointcut("execution(public * com.bone.tpa.claim.adapter..*.*(..))")//切入点描述 这个是controller包的切入点
    public void claimPath(){}//签名，可以理解成这个切入点的一个名称

    @Pointcut("execution(public * com.bone.tpa.adjustment.adapter..*.*(..))")//切入点描述 这个是controller包的切入点
    public void adjust(){}//签名，可以理解成这个切入点的一个名称


    @Pointcut("execution(public * com.bone.tpa.soa.adapter..*.*(..))")//切入点描述 这个是controller包的切入点
    public void soa(){}

    @Around("soa()")
    public Object aroundSoa(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        Object result = null;
        MethodSignature methodSignature = (MethodSignature)proceedingJoinPoint.getSignature();
        Method method = methodSignature.getMethod();
        boolean hasError = false;
        initMdcTraceId();
        Long start = System.currentTimeMillis();
        try {

            result = proceedingJoinPoint.proceed();
        } catch (Throwable ex) {
            //log.error(method.getName()+" error : ",ex);

            hasError = true;
            throw ex;
        } finally {
            long costMs = System.currentTimeMillis() - start;
            if(result instanceof Result){
                ( (Result)result ).setTaceId(MDC.get(MDC_TRACE_ID));
            }
            BizContextUtils.clear();
            log(method, costMs, hasError, result);
            clearTraceId();

        }
        return result;
    }

    @Around("claimPath() || adjust()")
    public Object around(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {

        Object result = null;
        MethodSignature methodSignature = (MethodSignature)proceedingJoinPoint.getSignature();
        Method method = methodSignature.getMethod();
       boolean hasError = false;
        try {
//            String userName = "测试用户";
//            LoginUser user = new LoginUser();
//            user.setUserName(userName);
//            LoginUserContext.set(user);
            //用这个annot 来标识，用来标识不需要登录的接口，比如登录
            BizContext context = new BizContext();
            NoLoginUri noLoginUri=    method.getAnnotation(NoLoginUri.class);
            if( noLoginUri == null){
                String userName =  userUtil.getUserCode();
                context.setUserName(userName);
            }
            BizContextUtils.set(context);
        } catch (Exception e) {
            log.error("获取用户登录信息失败:",e);
            throw new TpaBizException(BizErrorCode.UNAUTHORIZED);
        }
        initMdcTraceId();
        Long start = System.currentTimeMillis();
        try {

            result = proceedingJoinPoint.proceed();
        } catch (Exception ex) {
            //log.error(method.getName()+" error : ",ex);

            hasError = true;
            throw ex;
        } finally {
            long costMs = System.currentTimeMillis() - start;
            if(result instanceof Result){
                ( (Result)result ).setTaceId(MDC.get(MDC_TRACE_ID));
            }
            BizContextUtils.clear();
           log(method, costMs, hasError, result);
            clearTraceId();

        }
        return result;

    }

    private void log(Method method, long cost, boolean hasError, Object result) {
        log.info("controllerLog:{}|{}|{} ",
                method.getDeclaringClass().getName() + "." + method.getName(),
                cost + "ms",
                hasError
        );
    }

    static public void initMdcTraceId() {
        if(StringUtils.isBlank(MDC.get(MDC_TRACE_ID))){
            MDC.put(MDC_TRACE_ID, UUID.randomUUID().toString() );
        }
    }

    static public void clearTraceId() {
        MDC.remove(MDC_TRACE_ID);
    }

}
