package com.bone.engine.extension.router;

import com.bone.engine.extension.Extension;
import com.bone.engine.extension.context.BizContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 路由匹配得分计算器
 * <p>
 * 负责计算扩展点实现与业务上下文之间的匹配得分，基于多维度的匹配规则
 * </p>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
public class RouteScoreCalculator extends AbstractRouterComponent implements RouterComponent.ScoreCalculatorComponent {
    private static final Logger logger = LoggerFactory.getLogger(RouteScoreCalculator.class);
    
    // 匹配维度权重常量
    private static final int TAG_MATCH_SCORE = 10;
    private static final int TENANT_MATCH_SCORE = 100;
    private static final int BIZ_DOMAIN_MATCH_SCORE = 80;
    private static final int USE_CASE_MATCH_SCORE = 60;

    // 匹配维度权重配置
    private static final Map<String, Integer> DIMENSION_WEIGHTS = new HashMap<String, Integer>() {
        private static final long serialVersionUID = 1L;
        {
            put("tenant", 1000);       // 租户匹配权重
            put("bizCode", 800);       // 业务域匹配权重
            put("useCase", 600);       // 用例匹配权重
            put("scenario", 400);      // 场景匹配权重
            put("env", 300);           // 环境匹配权重
            put("userGroup", 200);     // 用户组匹配权重
            put("tag", 20);            // 标签匹配基础权重
        }
    };

    /**
     * 计算扩展点实现与业务上下文的匹配得分
     * 
     * @param extension 扩展点实现的注解信息
     * @param context 业务上下文
     * @return 匹配得分，值越高表示匹配度越好
     */
    @Override
    public int calculateMatchScore(Extension extension, BizContext<?> context) {
        int score = 0;

        // 1. 租户匹配
        String tenantCode = context.getTenantCode();
        if (StringUtils.hasText(tenantCode) && StringUtils.hasText(extension.tenant())) {
            if (extension.tenant().equals(tenantCode) || "*.".equals(extension.tenant())) {
                score += DIMENSION_WEIGHTS.get("tenant");
            }
        }

        // 2. 业务域匹配
        String bizCode = context.getBizCode();
        if (StringUtils.hasText(bizCode)) {
            if (StringUtils.hasText(extension.bizCode()) && extension.bizCode().equals(bizCode)) {
                score += DIMENSION_WEIGHTS.get("bizCode");
            } else if (!CollectionUtils.isEmpty(extension.multiBizCodes())) {
                for (String code : extension.multiBizCodes()) {
                    if (code.equals(bizCode)) {
                        score += DIMENSION_WEIGHTS.get("bizCode");
                        break;
                    }
                }
            }
        }

        // 3. 用例匹配
        String useCase = context.getUseCase();
        if (StringUtils.hasText(useCase) && StringUtils.hasText(extension.useCase())) {
            if (extension.useCase().equals(useCase)) {
                score += DIMENSION_WEIGHTS.get("useCase");
            }
        }

        // 4. 场景匹配
        String scenario = context.getScenario();
        if (StringUtils.hasText(scenario) && StringUtils.hasText(extension.scenario())) {
            if (extension.scenario().equals(scenario)) {
                score += DIMENSION_WEIGHTS.get("scenario");
            }
        }

        // 5. 环境匹配
        String env = context.getEnv();
        if (StringUtils.hasText(env) && StringUtils.hasText(extension.env())) {
            if (extension.env().equals(env) || "*".equals(extension.env())) {
                score += DIMENSION_WEIGHTS.get("env");
            }
        }

        // 6. 用户组匹配
        String userGroup = context.getUserGroup();
        if (StringUtils.hasText(userGroup) && StringUtils.hasText(extension.userGroup())) {
            if (extension.userGroup().equals(userGroup) || "*".equals(extension.userGroup())) {
                score += DIMENSION_WEIGHTS.get("userGroup");
            }
        }

        // 7. 标签匹配（支持通配符）
        Map<String, Object> contextTags = context.getAllTags();
        Map<String, String> extensionTags = parseExtensionTags(extension.tags());
        
        if (!CollectionUtils.isEmpty(extensionTags) && !CollectionUtils.isEmpty(contextTags)) {
            int matchedTags = 0;
            int totalTags = extensionTags.size();
            
            for (Map.Entry<String, String> tagEntry : extensionTags.entrySet()) {
                String tagName = tagEntry.getKey();
                String tagValue = tagEntry.getValue();
                
                if (contextTags.containsKey(tagName)) {
                    Object ctxTagValue = contextTags.get(tagName);
                    if (ctxTagValue != null) {
                        // 支持标签值通配符
                        if ("*".equals(tagValue) || tagValue.equals(ctxTagValue.toString())) {
                            matchedTags++;
                        }
                    }
                }
            }
            
            // 基于匹配率的奖励
            if (matchedTags > 0) {
                int baseTagScore = DIMENSION_WEIGHTS.get("tag");
                score += baseTagScore * matchedTags;
                
                // 完全匹配奖励
                if (matchedTags == totalTags) {
                    score += baseTagScore * 2;
                }
            }
        }

        return score;
    }

    /**
     * 解析扩展点标签配置
     * 
     * @param tagArray 标签数组，格式为"key=value"
     * @return 解析后的标签映射
     */
    private Map<String, String> parseExtensionTags(String[] tagArray) {
        Map<String, String> tagMap = new HashMap<>();
        if (tagArray == null || tagArray.length == 0) {
            return tagMap;
        }
        
        for (String tag : tagArray) {
            if (StringUtils.hasText(tag) && tag.contains("=")) {
                int eqIndex = tag.indexOf('=');
                String key = tag.substring(0, eqIndex).trim();
                String value = tag.substring(eqIndex + 1).trim();
                if (StringUtils.hasText(key)) {
                    tagMap.put(key, value);
                }
            }
        }
        
        return tagMap;
    }

    /**
     * 访问嵌套属性值
     * 
     * @param obj 目标对象
     * @param propertyPath 属性路径，支持点号分隔的嵌套属性
     * @return 属性值
     */
    @Override
    public Object getNestedProperty(Object obj, String propertyPath) {
        if (obj == null || !StringUtils.hasText(propertyPath)) {
            return null;
        }
        
        try {
            String[] properties = propertyPath.split("\\.");
            Object current = obj;
            
            for (String property : properties) {
                if (current == null) {
                    return null;
                }
                
                // 简化实现：使用getter方法获取属性值
                String methodName = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
                try {
                    Method method = current.getClass().getMethod(methodName);
                    current = method.invoke(current);
                } catch (Exception e) {
                    // 如果没有找到getter方法，尝试直接访问字段
                    try {
                        Field field = current.getClass().getDeclaredField(property);
                        field.setAccessible(true);
                        current = field.get(current);
                    } catch (Exception ex) {
                        // 找不到属性，返回null
                        return null;
                    }
                }
            }
            
            return current;
        } catch (Exception e) {
            logger.error("Error getting nested property: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 计算标签匹配得分
     */
    @Override
    public int getTagMatchScore(Map<String, String> tags, BizContext<?> context) {
        if (CollectionUtils.isEmpty(tags) || context == null) {
            return 0;
        }
        
        int score = 0;
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String tagName = entry.getKey();
            String tagValue = entry.getValue();
            
            // 从上下文获取对应的值
            Object contextValue = getNestedProperty(context, tagName);
            if (contextValue != null) {
                String strValue = contextValue.toString();
                if (isMatchOrWildcard(strValue, tagValue)) {
                    score += TAG_MATCH_SCORE;
                }
            }
        }
        
        return score;
    }
    
    /**
     * 计算租户匹配得分
     */
    @Override
    public int getTenantMatchScore(String tenantId, BizContext<?> context) {
        if (!StringUtils.hasText(tenantId) || context == null) {
            return 0;
        }
        
        // 尝试获取租户ID
        Object tenantIdObj = getNestedProperty(context, "tenantId");
        String contextTenantId = tenantIdObj != null ? tenantIdObj.toString() : null;
        
        return isMatchOrWildcard(tenantId, contextTenantId) ? TENANT_MATCH_SCORE : 0;
    }
    
    /**
     * 计算业务域匹配得分
     */
    @Override
    public int getBizDomainMatchScore(String bizDomain, BizContext<?> context) {
        if (!StringUtils.hasText(bizDomain) || context == null) {
            return 0;
        }
        
        // 尝试获取业务域
        Object bizDomainObj = getNestedProperty(context, "bizDomain");
        String contextBizDomain = bizDomainObj != null ? bizDomainObj.toString() : null;
        
        return isMatchOrWildcard(bizDomain, contextBizDomain) ? BIZ_DOMAIN_MATCH_SCORE : 0;
    }
    
    /**
     * 计算用例匹配得分
     */
    @Override
    public int getUseCaseMatchScore(String useCase, BizContext<?> context) {
        if (!StringUtils.hasText(useCase) || context == null) {
            return 0;
        }
        
        // 从上下文获取用例信息
        Object contextUseCase = getNestedProperty(context, "useCase");
        if (contextUseCase != null) {
            String strUseCase = contextUseCase.toString();
            if (isMatchOrWildcard(useCase, strUseCase)) {
                return USE_CASE_MATCH_SCORE;
            }
        }
        
        return 0;
    }
    
    public boolean isMatchOrWildcard(String pattern, String target) {
        return "*".equals(pattern) || Objects.equals(pattern, target);
    }
    
    @Override
    public String getComponentName() {
        return "RouteScoreCalculator";
    }
    
    @Override
    protected void doInitialize() throws Exception {
        logger.info("RouteScoreCalculator initialized");
    }
    
    @Override
    protected void doShutdown() {
        logger.info("RouteScoreCalculator shut down");
    }
}