package com.bone.lowcode.infra.infrastructure.common.aop;

import com.bone.core.exception.ServiceException;
import com.bone.lowcode.infra.infrastructure.common.annotation.HandleRepeatedReq;
import com.bone.lowcode.infra.infrastructure.common.annotation.KeyPart;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
public class HandleRepeatedReqAspect {

    @Autowired
    private RedissonClient redissonClient;


    @Around("@annotation(com.bone.lowcode.infra.infrastructure.common.annotation.HandleRepeatedReq)")
    public Object aroundMethod(ProceedingJoinPoint jp) throws Throwable {
        MethodSignature signature = (MethodSignature) jp.getSignature();
        Method method = signature.getMethod();
        HandleRepeatedReq annotation = method.getAnnotation(HandleRepeatedReq.class);
        boolean flag = annotation.value();

        HttpServletRequest request = ((ServletRequestAttributes) (RequestContextHolder.currentRequestAttributes())).getRequest();
        String suffix = null;
        if (flag) {
            String reqMethod = request.getMethod();
            if ("GET".equalsIgnoreCase(reqMethod)) {
                Object[] args = jp.getArgs();
                Parameter[] parameters = method.getParameters();

                List<Entry> list = new ArrayList<>();
                for (int i = 0; i < parameters.length; i++) {
                    Parameter parameter = parameters[i];
                    if (parameter.isAnnotationPresent(KeyPart.class) && args[i] != null) {
                        list.add(new Entry(parameter.getAnnotation(KeyPart.class).value(), args[i].toString()));
                    }
                }

                suffix = getSuffix(list);
            } else if ("POST".equalsIgnoreCase(reqMethod)) {
                Object[] args = jp.getArgs();
                if (args.length == 1) {
                    Object obj = args[0];
                    Class<?> objClass = obj.getClass();
                    Field[] fields = objClass.getDeclaredFields();

                    List<Entry> list = new ArrayList<>();
                    for (Field field : fields) {
                        if (field.isAnnotationPresent(KeyPart.class)) {
                            field.setAccessible(true);
                            list.add(new Entry(field.getAnnotation(KeyPart.class).value(), field.get(obj)));
                        }
                    }

                    suffix = getSuffix(list);
                }
            }
        }

        String prefix = "handleRepeatedReq" + ":" + request.getRequestURI();
        String key = suffix == null ? prefix : prefix + ":" + suffix;
        RLock lock = redissonClient.getLock(key);
        boolean isLock = lock.tryLock(1, 60, TimeUnit.SECONDS);
        if (!isLock) {
            log.info("阻止重复请求,key:{}", key);
            throw new ServiceException(500, "接口繁忙,请勿重复点击");
        }

        Object result = null;
        try {
            result = jp.proceed(); //执行目标方法
        } finally {
            if (lock.isHeldByCurrentThread())
                lock.unlock();
        }
        return result;
    }

    private String getSuffix(List<Entry> list) {
        if (CollectionUtils.isEmpty(list)) return null;

        list.sort(Comparator.comparing(Entry::getKey));
        StringBuilder builder = new StringBuilder();
        for (Entry entry : list) {
            builder.append(entry.getValue().toString()).append(":");
        }
        if (builder.charAt(builder.length() - 1) == ':') {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder.toString();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Entry {
        private Integer key;

        private Object value;
    }
}
