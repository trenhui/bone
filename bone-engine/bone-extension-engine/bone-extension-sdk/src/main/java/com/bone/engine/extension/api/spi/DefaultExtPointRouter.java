package com.bone.engine.extension.api.spi;

import com.bone.engine.extension.api.annotation.Extension;
import com.bone.engine.extension.core.router.*;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.support.expression.AviatorExpressionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DefaultExtPointRouter extends AbstractRouterComponent
        implements ExtPointRouter, SmartInitializingSingleton, InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DefaultExtPointRouter.class);
    private final ApplicationContext ctx;
    private final CacheManager cacheManager;
    private final RouteScoreCalculator scoreCalculator;
    private final WeightAndGraySelector weightGraySelector;
    private final ExpressionEvaluator exprEvaluator = new AviatorExpressionEvaluator();

    @Autowired
    public DefaultExtPointRouter(ApplicationContext ctx, CacheManager cacheManager) {
        this.ctx = ctx;
        this.cacheManager = cacheManager;
        this.scoreCalculator = new RouteScoreCalculator();
        this.weightGraySelector = new WeightAndGraySelector();
    }

    @Override
    public <T> T route(Class<T> extPointClass, BizContext<?> context) {
        CacheManager.RouteCacheKey cacheKey = CacheKeyFactory.createRouteCacheKey(extPointClass, null, context);
        return cacheManager.getFromCache(cacheKey.toString(), key -> doRoute(extPointClass, context));
    }

    @SuppressWarnings("unchecked")
    private <T> T doRoute(Class<T> extPointClass, BizContext<?> context) {
        List<Object> candidates = cacheManager.getOrCreateRouteRuleCache(extPointClass,
                clazz -> new ArrayList<>(ctx.getBeansOfType(clazz).values()));

        if (candidates.isEmpty()) return null;

        // 1. 精确匹配 + 评分
        List<Map.Entry<Object, Integer>> scored = candidates.stream()
                .map(impl -> {
                    Extension ext = impl.getClass().getAnnotation(Extension.class);
                    int score = scoreCalculator.calculateMatchScore(ext, context);
                    return Map.entry(impl, score);
                })
                .filter(e -> e.getValue() > 0)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toList());

        List<Object> matched = scored.isEmpty() ? candidates :
                scored.stream().map(Map.Entry::getKey).collect(Collectors.toList());

        // 2. 权重 + 灰度
        Object selected = weightGraySelector.selectByWeight(matched,
                matched.stream().collect(Collectors.toMap(Function.identity(),
                        o -> o.getClass().getAnnotation(Extension.class))));

        selected = weightGraySelector.selectByGrayRelease(List.of(selected),
                matched.stream().collect(Collectors.toMap(Function.identity(),
                        o -> o.getClass().getAnnotation(Extension.class))), context);

        return (T) selected;
    }

    @Override public void clearCache(Class<?> extPointClass) { cacheManager.clearCache(extPointClass); }
    @Override public <T> void registerImplementation(Class<T> c, T impl) { cacheManager.clearCache(c); }
    @Override public <T> void unregisterImplementation(Class<T> c, T impl) { cacheManager.clearCache(c); }
    @Override public <T> T getDefaultImplementation(Class<T> c) { return ctx.getBean(c); }
    @Override public Map<String, Map<String, Long>> getRouteStats() {
        return null;//todo more
        //return cacheManager.getCacheStats();
    }

    @Override public void afterSingletonsInstantiated() { initialize(); }
    @Override public void afterPropertiesSet() { initialize(); }
    @Override protected void doInitialize() throws Exception { log.info("Bone Extension Router v2.0 初始化完成"); }
    @Override protected void doShutdown() { cacheManager.clearAllCache(); }
    @Override public String getComponentName() { return "DefaultExtPointRouter"; }
}