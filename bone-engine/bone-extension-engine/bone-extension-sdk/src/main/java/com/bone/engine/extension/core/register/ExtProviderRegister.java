//package com.bone.engine.extension.core.register;
//
//import com.bone.core.util.ReflectionUtil;
//import com.bone.engine.extension.api.annotation.Extension;
//import com.bone.engine.extension.api.annotation.ExtensionPoint;
//import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
//import com.bone.engine.extension.support.repository.ExtPointRepositoryFactory;
//import com.bone.engine.extension.api.spi.ExtensionRepository;
//import com.bone.engine.extension.support.repository.InMemoryExtensionRepository;
//import jakarta.annotation.PostConstruct;
//import org.springframework.aop.support.AopUtils;
//import org.springframework.beans.BeansException;
//import org.springframework.context.ApplicationContext;
//import org.springframework.context.ApplicationContextAware;
//import org.springframework.core.annotation.AnnotationUtils;
//import org.springframework.stereotype.Component;
//import org.springframework.util.ClassUtils;
//import org.springframework.util.StringUtils;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//
///**
// * ExtProviderRegister
// *
// * @author renhui.trh 2023-10-30
// */
//@Component
//public class ExtProviderRegister implements ApplicationContextAware {
//
//    private ApplicationContext applicationContext;
//
//    @PostConstruct
//    public void init() {
//        Map<String, Object> extensionBeans = applicationContext.getBeansWithAnnotation(Extension.class);
//        extensionBeans.values().forEach(
//                extProvider -> registerExtProvider(extProvider)
//        );
//    }
//
//    @Override
//    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
//        this.applicationContext = applicationContext;
//    }
//
//    public void registerExtProvider(Object extProvider) {
//        Class<?> extProviderClass = extProvider.getClass();
//        if (AopUtils.isAopProxy(extProvider)) {
//            extProviderClass = ClassUtils.getUserClass(extProvider);
//        }
//
//        Extension extAnnotation = AnnotationUtils.findAnnotation(extProviderClass, Extension.class);
//        ExtensionDefinition extProviderSpec = ExtensionDefinition.builder()
//                .tenant(extAnnotation.tenant())
//                .bizCode(extAnnotation.bizCode())
//                .useCase(extAnnotation.useCase())
//                .scenario(extAnnotation.scenario())
//                .condition(extAnnotation.condition())
//                .build();
//
//        ExtensionRepository extProviderRepo = ExtPointRepositoryFactory.createExtPointRepository(InMemoryExtensionRepository.class);
//        Class<?> extPointClass = ReflectionUtil.getInterfaceByAnnotation(extProviderClass, ExtensionPoint.class);
//
//        if (!StringUtils.hasText(extProviderSpec.getCondition())) {
//            Object preVal = extProviderRepo.register(extPointClass.getCanonicalName() + "." + extProviderSpec.getBizIdentity(), extProvider);
//            if (preVal != null) {
//                throw new RuntimeException("Duplicate registration is not allowed for :" + extProviderSpec.getBizIdentity());
//            }
//        } else {
//            List<Object> extProviderList = (List<Object>) extProviderRepo.getExtensionByCode(extPointClass.getCanonicalName() + ".expression.extProviderList");
//            if (extProviderList == null) {
//                extProviderList = new ArrayList<>();
//            }
//            extProviderList.add(extProvider);
//            extProviderRepo.register(extPointClass.getCanonicalName() + ".expression.extProviderList", extProviderList);
//        }
//    }
//}
