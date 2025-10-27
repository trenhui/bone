package com.bone.engine.extension.router;

import com.bone.engine.extension.Extension;
import com.bone.engine.extension.context.BizContext;
import org.springframework.expression.Expression;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 路由组件接口，定义路由相关组件的标准行为
 * 用于解耦和标准化路由系统中的各个组件
 *
 * @author Trae AI
 */
public interface RouterComponent {
    
    /**
     * 初始化组件
     */
    void initialize();
    
    /**
     * 清理资源
     */
    void shutdown();
    
    /**
     * 获取组件名称
     */
    String getComponentName();
    
    /**
     * 检查组件是否可用
     */
    boolean isAvailable();
    
    // 缓存管理相关
    interface CacheManagerComponent extends RouterComponent {
        /**
         * 初始化缓存
         */
        void initializeCache(int expireTime, int maxSize);
        
        /**
         * 从缓存获取值，如果不存在则计算并缓存
         */
        <V> V getFromCache(String key, Function<String, V> loader);
        
        /**
         * 检查缓存键是否命中
         */
        boolean isCacheHit(String key);
        
        /**
         * 获取或创建表达式缓存
         */
        Expression getExpression(String expressionString, Function<String, Expression> parser);
        
        /**
         * 获取或创建路由规则缓存
         */
        List<Object> getOrCreateRouteRuleCache(Class<?> extPointClass, Function<Class<?>, List<Object>> loader);
        
        /**
         * 刷新路由规则缓存
         */
        void refreshRouteRuleCache(Class<?> extPointClass);
        
        /**
         * 清理指定扩展点的缓存
         */
        void clearCache(Class<?> extPointClass);
        
        /**
         * 清理所有缓存
         */
        void clearAllCache();
        
        /**
         * 预热缓存
         */
        void warmupCache(Class<?> extPointClass, BizContext<?> context);
        
        /**
         * 处理配置变更
         */
        void onConfigChanged(String key, String value);
        
        /**
         * 重新初始化配置
         */
        void reinitializeConfig(int expireTime, int maxSize);
    }
    
    // 评分计算器相关
    interface ScoreCalculatorComponent extends RouterComponent {
        /**
         * 计算扩展实现的匹配得分
         */
        int calculateMatchScore(Extension extension, BizContext<?> context);
        
        /**
         * 获取嵌套属性值
         */
        Object getNestedProperty(Object obj, String propertyPath);
    }
    
    // 权重和灰度选择器相关
    interface WeightGraySelectorComponent extends RouterComponent {
        /**
         * 应用权重路由和灰度发布策略
         */
        Object applyWeightAndGrayRelease(List<Object> implementations, Class<?> extPointClass, 
                                        BizContext<?> context, boolean weightedEnabled, boolean grayEnabled);
    }
    
    // 统计收集器相关
    interface StatsCollectorComponent extends RouterComponent {
        /**
         * 记录路由统计信息
         * 
         * @param extPointClass 扩展点类型
         * @param method 调用方法
         * @param implementationType 实现类型
         * @param executionTimeMs 执行时间（毫秒）
         * @param success 是否成功
         */
        void recordRouteStats(Class<?> extPointClass, java.lang.reflect.Method method, 
                             Class<?> implementationType, long executionTimeMs, boolean success);
        
        /**
         * 记录路由失败
         * 
         * @param extPointClass 扩展点类型
         * @param method 调用方法
         * @param implementationType 实现类型
         */
        void recordRouteFailure(Class<?> extPointClass, java.lang.reflect.Method method, 
                               Class<?> implementationType);
        
        /**
         * 获取路由统计
         * 
         * @return 路由统计信息映射
         */
        Map<String, Map<String, Long>> getRouteStats();
        
        /**
         * 获取实现统计
         * 
         * @param implementationName 实现名称
         * @return 实现统计信息映射
         */
        Map<String, Long> getImplementationStats(String implementationName);
        
        /**
         * 重置所有统计信息
         */
        void resetAllStats();
        
        /**
         * 重置特定扩展点的统计
         * 
         * @param extPointClass 扩展点类型
         */
        void resetStatsForExtPoint(Class<?> extPointClass);
    }
}