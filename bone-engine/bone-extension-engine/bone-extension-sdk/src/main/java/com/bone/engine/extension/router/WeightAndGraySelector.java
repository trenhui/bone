package com.bone.engine.extension.router;

import com.bone.engine.extension.Extension;
import com.bone.engine.extension.context.BizContext;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 权重和灰度发布选择器
 * <p>
 * 负责基于权重和灰度发布策略选择扩展点实现
 * </p>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
public class WeightAndGraySelector extends AbstractRouterComponent implements RouterComponent.WeightGraySelectorComponent {
    
    private static final Logger logger = LoggerFactory.getLogger(WeightAndGraySelector.class);
    private static final int DEFAULT_WEIGHT = 100;
    private Map<String, Integer> weightConfig = new ConcurrentHashMap<>();

    /**
     * 应用权重和灰度发布策略选择扩展点实现
     * 
     * @param candidates 候选扩展点实现列表
     * @param extensionMap 扩展点实现与注解的映射
     * @param context 业务上下文
     * @return 选中的扩展点实现，或null表示没有选中
     */
    @Override
    public <T> T applyWeightAndGrayRelease(List<T> candidates, Map<T, Extension> extensionMap, 
                                          BizContext<?> context) {
        ensureInitialized();
        
        if (candidates == null || candidates.isEmpty() || extensionMap == null) {
            return null;
        }

        // 如果只有一个候选，直接返回
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // 处理灰度发布逻辑
        List<T> grayReleaseCandidates = new ArrayList<>();
        List<T> normalCandidates = new ArrayList<>();

        for (T candidate : candidates) {
            Extension extension = extensionMap.get(candidate);
            if (matchGrayReleaseCondition(extension, context)) {
                grayReleaseCandidates.add(candidate);
            } else {
                normalCandidates.add(candidate);
            }
        }

        // 如果有灰度候选且需要应用灰度流量
        if (!grayReleaseCandidates.isEmpty()) {
            if (shouldApplyGrayRelease(grayReleaseCandidates.get(0), extensionMap, context)) {
                // 从灰度候选中基于权重选择
                return selectByWeight(grayReleaseCandidates, extensionMap);
            }
        }

        // 从正常候选中基于权重选择
        return selectByWeight(normalCandidates, extensionMap);
    }
    
    /**
     * 实现WeightGraySelectorComponent接口的方法
     */
    @Override
    public boolean applyWeightAndGrayRelease(Class<?> extPointClass, Object implementation, BizContext context) {
        ensureInitialized();
        
        if (extPointClass == null || context == null) {
            return false;
        }
        
        // 检查灰度发布
        String extPointName = extPointClass.getName();
        
        // 检查是否在灰度名单中
        String userId = getUserIdFromContext(context);
        if (userId != null && isInGrayList(extPointName, userId)) {
            logger.debug("User {} is in gray list for {}", userId, extPointName);
            return true;
        }
        
        // 基于权重的决策
        int weight = getWeightForImplementation(implementation);
        if (weight < DEFAULT_WEIGHT) {
            Random random = new Random();
            int randomValue = random.nextInt(DEFAULT_WEIGHT);
            return randomValue < weight;
        }
        
        return false;
    }
    
    /**
     * 从上下文获取用户ID
     */
    private String getUserIdFromContext(BizContext context) {
        try {
            // 使用BizContext的通用方法获取用户ID
            return context.getStringValue("userId");
        } catch (Exception e) {
            logger.warn("Failed to get userId from context", e);
            return null;
        }
    }
    
    /**
     * 检查用户是否在灰度名单中
     */
    private boolean isInGrayList(String extPointName, String userId) {
        // 从配置中获取灰度用户
        return false;
    }
    
    /**
     * 获取实现类的权重
     */
    private int getWeightForImplementation(Object implementation) {
        if (implementation == null) {
            return DEFAULT_WEIGHT;
        }
        
        String implName = implementation.getClass().getName();
        return weightConfig.getOrDefault(implName, DEFAULT_WEIGHT);
    }
    
    /**
     * 设置权重配置
     */
    public void setWeightConfig(Map<String, Integer> weightConfig) {
        if (weightConfig != null) {
            this.weightConfig.putAll(weightConfig);
        }
    }

    /**
     * 基于权重随机选择扩展点实现
     * 
     * @param candidates 候选扩展点实现列表
     * @param extensionMap 扩展点实现与注解的映射
     * @return 选中的扩展点实现
     */
    @Override
    public <T> T selectByWeight(List<T> candidates, Map<T, Extension> extensionMap) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        // 计算总权重
        int totalWeight = 0;
        for (T candidate : candidates) {
            Extension extension = extensionMap.get(candidate);
            totalWeight += getWeight(extension);
        }

        // 如果总权重为0，平均分配
        if (totalWeight <= 0) {
            return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        }

        // 随机权重选择
        int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight) + 1;
        int currentWeight = 0;

        for (T candidate : candidates) {
            Extension extension = extensionMap.get(candidate);
            currentWeight += getWeight(extension);
            if (randomWeight <= currentWeight) {
                return candidate;
            }
        }

        // 默认返回第一个
        return candidates.get(0);
    }

    /**
     * 根据灰度发布配置选择实现
     * 
     * @param candidates 候选扩展点实现列表
     * @param extensionMap 扩展点实现与注解的映射
     * @param context 业务上下文
     * @return 选中的扩展点实现
     */
    @Override
    public <T> T selectByGrayRelease(List<T> candidates, Map<T, Extension> extensionMap, 
                                    BizContext<?> context) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        List<T> eligibleCandidates = new ArrayList<>();
        for (T candidate : candidates) {
            Extension extension = extensionMap.get(candidate);
            if (matchGrayReleaseCondition(extension, context)) {
                eligibleCandidates.add(candidate);
            }
        }

        if (eligibleCandidates.isEmpty()) {
            return null;
        }

        // 检查是否应该应用灰度发布
        if (eligibleCandidates.size() > 0) {
            T firstCandidate = eligibleCandidates.get(0);
            if (shouldApplyGrayRelease(firstCandidate, extensionMap, context)) {
                // 从符合条件的灰度候选中随机选择
                return eligibleCandidates.get(ThreadLocalRandom.current().nextInt(eligibleCandidates.size()));
            }
        }

        return null;
    }

    /**
     * 获取扩展点实现的权重
     * 
     * @param extension 扩展点注解
     * @return 权重值，默认为100
     */
    @Override
    public int getWeight(Extension extension) {
        if (extension == null) {
            return 100;
        }
        int weight = extension.weight();
        return weight > 0 ? weight : 100;
    }

    /**
     * 检查是否匹配灰度发布条件
     * 
     * @param extension 扩展点注解
     * @param context 业务上下文
     * @return 是否匹配灰度条件
     */
    @Override
    public boolean matchGrayReleaseCondition(Extension extension, BizContext<?> context) {
        if (extension == null) {
            return false;
        }

        // 暂时返回false，避免调用不存在的方法
        // 实际应用中应该检查注解属性或配置
        return false;
        
        /* 以下是原本的实现，需要Extension类支持相应的方法
        // 检查灰度发布标志
        if (!extension.grayRelease()) {
            return false;
        }

        // 检查灰度用户组
        if (StringUtils.hasText(extension.grayUserGroup()) && 
            !extension.grayUserGroup().equals(context.getUserGroup())) {
            return false;
        }

        // 检查灰度业务域
        if (StringUtils.hasText(extension.grayBizCode()) && 
            !extension.grayBizCode().equals(context.getBizCode())) {
            return false;
        }

        // 检查灰度标签
        String[] grayTags = extension.grayTags();
        if (grayTags != null && grayTags.length > 0) {
            boolean tagMatched = false;
            for (String tag : grayTags) {
                if (StringUtils.hasText(tag) && tag.contains("=")) {
                    int eqIndex = tag.indexOf('=');
                    String key = tag.substring(0, eqIndex).trim();
                    String value = tag.substring(eqIndex + 1).trim();
                    
                    Object ctxTagValue = context.getTag(key);
                    if (ctxTagValue != null && value.equals(ctxTagValue.toString())) {
                        tagMatched = true;
                        break;
                    }
                }
            }
            if (!tagMatched) {
                return false;
            }
        }
        */

        return true;
    }

    /**
     * 检查是否应该应用灰度发布流量
     * 
     * @param candidate 候选扩展点实现
     * @param extensionMap 扩展点实现与注解的映射
     * @param context 业务上下文
     * @return 是否应用灰度流量
     */
    @Override
    public String getComponentName() {
        return "WeightAndGraySelector";
    }
    
    @Override
    protected void doInitialize() throws Exception {
        // 初始化权重配置
        logger.info("WeightAndGraySelector initialized");
    }
    
    @Override
    protected void doShutdown() {
        // 清理资源
        weightConfig.clear();
        logger.info("WeightAndGraySelector shut down");
    }

    /**
     * 检查是否应该应用灰度发布流量
     * 
     * @param candidate 候选扩展点实现
     * @param extensionMap 扩展点实现与注解的映射
     * @param context 业务上下文
     * @return 是否应用灰度流量
     */
    public <T> boolean shouldApplyGrayRelease(T candidate, Map<T, Extension> extensionMap, 
                                            BizContext<?> context) {
        Extension extension = extensionMap.get(candidate);
        if (extension == null) {
            return false;
        }

        int trafficRate = extension.trafficRate();
        // 流量比例为100%，直接返回true
        if (trafficRate >= 100) {
            return true;
        }

        // 流量比例为0%，直接返回false
        if (trafficRate <= 0) {
            return false;
        }

        // 基于流量比例随机决定
        return ThreadLocalRandom.current().nextInt(100) < trafficRate;
    }
}