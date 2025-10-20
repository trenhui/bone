package com.bone.engine.extension.metadata;

import java.util.List;
import java.util.Map;

/**
 * 基于优先级的路由器实现
 */
public class PriorityBasedRouter {
    
    /**
     * 路由到合适的候选实现
     */
    public <T> T route(List<T> candidates, Class<T> extPointClass, Map<String, String> context) {
        // 简化实现，返回第一个候选者
        if (candidates != null && !candidates.isEmpty()) {
            return candidates.get(0);
        }
        return null;
    }
}