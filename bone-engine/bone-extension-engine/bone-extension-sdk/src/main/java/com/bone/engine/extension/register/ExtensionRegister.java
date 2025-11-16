package com.bone.engine.extension.register;

//import com.bone.core.extension.repository.ExtPointRepository;
//import com.bone.core.extension.repository.ExtPointRepositoryFactory;
//import com.bone.core.extension.repository.MemExtPointRepository;
//import com.bone.core.extension.spec.ExtProviderSpec;
import com.bone.core.util.ReflectionUtil;
import com.bone.engine.extension.annotation.ExtPoint;
import com.bone.engine.extension.annotation.Extension;
import com.bone.engine.extension.repository.ExtPointRepository;
import com.bone.engine.extension.repository.ExtPointRepositoryFactory;
import com.bone.engine.extension.repository.MemExtPointRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ExtensionRegister
 *
 * @author renhui.trh 2023-10-30
 */
@Component
public class ExtensionRegister implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    @PostConstruct
    public void init() {
        Map<String, Object> extensionBeans = applicationContext.getBeansWithAnnotation(ExtProvider.class);
        extensionBeans.values().forEach(
                extProvider -> registerExtProvider(extProvider)
        );
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public void registerExtProvider(Object extProvider) {
        Class<?> extProviderClass = extProvider.getClass();
        if (AopUtils.isAopProxy(extProvider)) {
            extProviderClass = ClassUtils.getUserClass(extProvider);
        }

        Extension extAnnotation = AnnotationUtils.findAnnotation(extProviderClass, Extension.class);
        ExtProviderSpec extProviderSpec = ExtProviderSpec.builder()
                .tenantCode(extAnnotation.tenantCode())
                .bizCode(extAnnotation.bizCode())
                .useCase(extAnnotation.useCase())
                .scenario(extAnnotation.scenario())
                .expression(extAnnotation.expression())
                .build();

        ExtPointRepository extProviderRepo = ExtPointRepositoryFactory.createExtPointRepository(MemExtPointRepository.class);
        Class<?> extPointClass = ReflectionUtil.getInterfaceByAnnotation(extProviderClass, ExtPoint.class);

        if (!StringUtils.hasText(extProviderSpec.getExpression())) {
            Object preVal = extProviderRepo.put(extPointClass.getCanonicalName() + "." + extProviderSpec.getBizIdentity(), extProvider);
            if (preVal != null) {
                throw new RuntimeException("Duplicate registration is not allowed for :" + extProviderSpec.getBizIdentity());
            }
        } else {
            List<Object> extProviderList = (List<Object>) extProviderRepo.get(extPointClass.getCanonicalName() + ".expression.extProviderList");
            if (extProviderList == null) {
                extProviderList = new ArrayList<>();
            }
            extProviderList.add(extProvider);
            extProviderRepo.put(extPointClass.getCanonicalName() + ".expression.extProviderList", extProviderList);
        }
    }
}
