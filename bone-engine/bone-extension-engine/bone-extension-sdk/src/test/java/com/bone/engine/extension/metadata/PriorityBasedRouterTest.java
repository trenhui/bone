package com.bone.engine.extension.metadata;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.Extension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 优先级路由器测试
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
public class PriorityBasedRouterTest {
    
    @Autowired
    private PriorityBasedRouter priorityBasedRouter;
    
    @Autowired
    private ExtPointMetadataCollector metadataCollector;
    
    @Autowired
    private ExtPointRoutingConfigManager configManager;
    
    @BeforeEach
    public void setup() {
        // 清除测试前的缓存
        configManager.clearAll();
    }
    
    /**
     * 测试空候选者列表
     */
    @Test
    public void testEmptyCandidates() {
        Map<String, String> context = new HashMap<>();
        TestExtPoint result = priorityBasedRouter.route(Arrays.asList(), TestExtPoint.class, context);
        assertNull(result);
    }
    
    /**
     * 测试单个候选者
     */
    @Test
    public void testSingleCandidate() {
        TestImplementation1 impl1 = new TestImplementation1();
        Map<String, String> context = new HashMap<>();
        TestExtPoint result = priorityBasedRouter.route(Arrays.asList(impl1), TestExtPoint.class, context);
        assertEquals(impl1, result);
    }
    
    /**
     * 测试基于优先级的路由
     */
    @Test
    public void testPriorityBasedRouting() {
        TestImplementation1 impl1 = new TestImplementation1(); // priority=10
        TestImplementation2 impl2 = new TestImplementation2(); // priority=20
        TestImplementation3 impl3 = new TestImplementation3(); // priority=0
        
        List<TestExtPoint> candidates = Arrays.asList(impl1, impl2, impl3);
        Map<String, String> context = new HashMap<>();
        
        TestExtPoint result = priorityBasedRouter.route(candidates, TestExtPoint.class, context);
        
        // 应该选择优先级最高的实现
        assertEquals(impl2.getClass(), result.getClass());
    }
    
    /**
     * 测试运行时配置覆盖
     */
    @Test
    public void testRuntimeConfigOverride() {
        TestImplementation1 impl1 = new TestImplementation1(); // default priority=10
        TestImplementation2 impl2 = new TestImplementation2(); // default priority=20
        
        // 在运行时配置中降低impl2的优先级
        Map<String, String> runtimeConfig = new HashMap<>();
        runtimeConfig.put("priority", "5");
        configManager.updateRoutingConfig(TestExtPoint.class.getName(), 
                TestImplementation2.class.getName(), runtimeConfig);
        
        List<TestExtPoint> candidates = Arrays.asList(impl1, impl2);
        Map<String, String> context = new HashMap<>();
        
        TestExtPoint result = priorityBasedRouter.route(candidates, TestExtPoint.class, context);
        
        // 现在impl1应该优先级更高
        assertEquals(impl1.getClass(), result.getClass());
    }
    
    /**
     * 测试默认实现
     */
    @Test
    public void testDefaultImplementation() {
        TestImplementation1 impl1 = new TestImplementation1(); 
        TestImplementation2 impl2 = new TestImplementation2();
        TestImplementation4 impl4 = new TestImplementation4(); // isDefault=true
        
        List<TestExtPoint> candidates = Arrays.asList(impl1, impl2, impl4);
        Map<String, String> context = new HashMap<>();
        
        TestExtPoint result = priorityBasedRouter.route(candidates, TestExtPoint.class, context);
        
        // 应该选择默认实现，即使优先级不是最高的
        assertEquals(impl4.getClass(), result.getClass());
    }
    
    // 测试接口定义
    @ExtPoint
    public interface TestExtPoint {
        String getName();
    }
    
    // 测试实现类 - 优先级10
    @Extension(bizCode = "test", priority = 10)
    public static class TestImplementation1 implements TestExtPoint {
        @Override
        public String getName() {
            return "impl1"; 
        }
    }
    
    // 测试实现类 - 优先级20
    @Extension(bizCode = "test", priority = 20)
    public static class TestImplementation2 implements TestExtPoint {
        @Override
        public String getName() {
            return "impl2"; 
        }
    }
    
    // 测试实现类 - 默认优先级0
    @Extension(bizCode = "test")
    public static class TestImplementation3 implements TestExtPoint {
        @Override
        public String getName() {
            return "impl3"; 
        }
    }
    
    // 测试实现类 - 设为默认实现
    @Extension(bizCode = "test", isDefault = true)
    public static class TestImplementation4 implements TestExtPoint {
        @Override
        public String getName() {
            return "impl4-default"; 
        }
    }
}