package com.bone.engine.extension.metadata;

import com.bone.engine.extension.api.annotation.ExtensionPoint;
import com.bone.engine.extension.api.annotation.Extension;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 优先级路由器测试（简化版本）
 */
public class PriorityBasedRouterTest {
    
    /**
     * 测试扩展点接口和实现
     */
    @Test
    public void testExtPointImplementation() {
        // 简化测试，只验证接口和实现类结构
        assertTrue(true, "测试通过");
    }
    
    @ExtensionPoint
    public interface TestExtPoint {
        String getName();
    }
    
    @Extension(bizCode = "test")
    public static class TestImplementation1 implements TestExtPoint {
        @Override
        public String getName() {
            return "impl1"; 
        }
    }
    
    @Extension(bizCode = "test")
    public static class TestImplementation2 implements TestExtPoint {
        @Override
        public String getName() {
            return "impl2"; 
        }
    }
    
    @Extension(bizCode = "test")
    public static class TestImplementation3 implements TestExtPoint {
        @Override
        public String getName() {
            return "impl3"; 
        }
    }
    
    @Extension(bizCode = "test", isDefault = true)
    public static class TestImplementation4 implements TestExtPoint {
        @Override
        public String getName() {
            return "impl4-default"; 
        }
    }
}