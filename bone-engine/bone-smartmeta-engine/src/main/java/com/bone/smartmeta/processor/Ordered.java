package com.bone.smartmeta.processor;

/**
 * 排序接口，定义组件的执行顺序
 * <p>
 * 该接口用于元数据处理器的优先级排序，值越小优先级越高
 * </p>
 *
 * @author SmartMeta Team
 */
public interface Ordered {

    /**
     * 最高优先级
     */
    int HIGHEST_PRECEDENCE = Integer.MIN_VALUE;

    /**
     * 高优先级
     */
    int HIGH_PRECEDENCE = 1000;

    /**
     * 中优先级
     */
    int MEDIUM_PRECEDENCE = 0;

    /**
     * 低优先级
     */
    int LOW_PRECEDENCE = -1000;

    /**
     * 最低优先级
     */
    int LOWEST_PRECEDENCE = Integer.MAX_VALUE;
}