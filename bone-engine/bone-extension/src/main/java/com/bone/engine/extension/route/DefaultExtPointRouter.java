package com.bone.engine.extension.route;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.ExtProvider;
import com.bone.engine.extension.expression.ExpressionEvaluator;
import com.bone.engine.extension.repository.ExtPointRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * DefaultExtPointRouter
 *
 * @author renhui.trh 2023-11-1
 */
@Slf4j
public class DefaultExtPointRouter implements ExtPointRouter {
    private final ExtPointRepository extProviderRepo;
    private static final ConcurrentMap<String, ExtProvider> extAnnotationCache = new ConcurrentHashMap<>();

    public DefaultExtPointRouter(ExtPointRepository extProviderRepo) {
        this.extProviderRepo = extProviderRepo;
    }

    public <C> C locateExtProvider(Class<C> targetClz, BizContext bizContext) {
        if (bizContext == null) {
            throw new IllegalArgumentException("bizContext can not be null for extPoint");
        }

        //1、根据tenantCode， bizCode， useCase， scenario这4个维度精确查找扩展点实现
        String extProviderKey = targetClz.getCanonicalName() + "." + bizContext.getBizIdentity();
        log.debug("extProviderKey is : " + extProviderKey);
        C extProvider = locate(extProviderKey);
        if (extProvider != null) {
            return extProvider;
        }

        //2、根据规则表达式，计算出匹配的扩展点实现
        extProvider = locateByExpression(targetClz, bizContext);
        if (extProvider != null) {
            return extProvider;
        }

        //3、返回默认扩展点实现
        extProviderKey = targetClz.getCanonicalName() + "." + bizContext.getDefaultBizIdentity();
        log.debug("defaultExtProviderKey is : " + extProviderKey);
        extProvider = locate(extProviderKey);
        if (extProvider != null) {
            return extProvider;
        }

        throw new RuntimeException("Can not find  ExtProvider by " + targetClz.getCanonicalName() + "." + bizContext.getBizIdentity());
    }

    private <ExtProvider> ExtProvider locate(String extProviderKey) {
        return (ExtProvider) extProviderRepo.get(extProviderKey);
    }

    private <C> C locateByExpression(Class<C> targetClz, BizContext bizContext) {

        List<Object> extProviderList = (List<Object>) extProviderRepo.get(targetClz.getCanonicalName() + ".expression.extProviderList");
        List<C> matchedExtProviderList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(extProviderList)) {
            for (Object extProvider : extProviderList) {
                ExtProvider extAnnotation = extAnnotationCache.computeIfAbsent(extProvider.getClass().getCanonicalName(),
                        k -> AnnotationUtils.findAnnotation(extProvider.getClass(), ExtProvider.class));
                if (StringUtils.hasText(extAnnotation.expression())) {
                    if (ExpressionEvaluator.evaluateExpression(extAnnotation.expression(), bizContext)) {
                        log.info(targetClz.getCanonicalName() + " expression " + extAnnotation.expression() + " matched " + extProvider.getClass().getCanonicalName());
                        matchedExtProviderList.add((C) extProvider);
                    }
                }
            }
        }

        if (matchedExtProviderList.size() == 1) {
            return matchedExtProviderList.get(0);
        }

        if (matchedExtProviderList.size() > 1) {
            throw new RuntimeException(targetClz.getCanonicalName() + " expression matched extProvider more than one :" + bizContext.getBizIdentity());
        }

        return null;
    }
}
