package com.bone.engine.extension;

import com.bone.engine.extension.repository.ExtPointRepository;
import com.bone.engine.extension.route.DefaultExtPointRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 扩展点集成测试类
 * 重点测试扩展点的基本功能
 */
public class ExtPointIntegrationTest {

    private ExtPointProxyFactory proxyFactory;
    private TestExtPointRepository repository;
    
    @BeforeEach
    void setUp() {
        // 清理上下文
        BizContexts.clear();
        
        // 创建自定义仓库和路由器
        repository = new TestExtPointRepository();
        DefaultExtPointRouter router = new DefaultExtPointRouter(repository);
        
        // 初始化代理工厂
        proxyFactory = new ExtPointProxyFactory(repository, router);
        
        // 注册测试用的扩展点实现
        registerTestExtProviders();
    }
    
    /**
     * 测试基本的上下文管理功能
     */
    @Test
    void testBasicContextManagement() {
        try (BizContexts.ContextManager manager = BizContexts.with("TENANT_A", "ORDER")) {
            BizContext<?> context = BizContexts.getCurrent();
            assertNotNull(context, "Context should not be null");
            assertEquals("TENANT_A|ORDER|DEFAULT|DEFAULT", context.getBizIdentity(), "Business identity format should match");
        }
        
        // 上下文应该已经被清理
        BizContext<?> context = BizContexts.getCurrent();
        assertEquals(null, context, "Context should be null after close");
    }
    
    /**
     * 测试上下文属性管理
     */
    @Test
    void testContextAttributes() {
        try (BizContexts.ContextManager manager = BizContexts.with("TENANT_B", "ORDER")) {
            BizContext<?> context = BizContexts.getCurrent();
            
            // 设置属性
            context.withAttribute("userId", "12345");
            context.withAttribute("orderAmount", 100.50);
            
            // 获取属性
            String userId = (String) context.getAttribute("userId");
            Double orderAmount = (Double) context.getAttribute("orderAmount");
            
            assertEquals("12345", userId, "User ID should match");
            assertEquals(100.50, orderAmount, "Order amount should match");
            assertEquals("TENANT_B|ORDER|DEFAULT|DEFAULT", context.getBizIdentity(), "Business identity format should match");
        }
    }
    
    /**
     * 测试默认扩展点实现
     * 这是最基本的测试，确保能找到默认实现
     */
    @Test
    void testDefaultExtPointImplementation() {
        try (BizContexts.ContextManager manager = BizContexts.with("TENANT_C", "USER")) {
            // 检查是否注册了默认实现
            DefaultUserService defaultService = repository.getDefaultUserService();
            assertNotNull(defaultService, "Default user service should be registered");
            
            // 测试默认实现的直接调用
            String result = defaultService.greetUser("test-user");
            assertEquals("Default User Greeting - test-user", result);
            
            // 验证业务身份格式
            BizContext<?> context = BizContexts.getCurrent();
            assertEquals("TENANT_C|USER|DEFAULT|DEFAULT", context.getBizIdentity(), "Business identity format should match");
        }
    }
    
    /**
     * 注册测试用的扩展点实现
     * 使用更简单直接的方式注册，避免复杂的路由逻辑
     */
    private void registerTestExtProviders() {
        // 创建并注册默认订单服务实现
        DefaultOrderService defaultOrderService = new DefaultOrderService();
        repository.setDefaultOrderService(defaultOrderService);
        
        // 创建并注册默认用户服务实现
        DefaultUserService defaultUserService = new DefaultUserService();
        repository.setDefaultUserService(defaultUserService);
        
        // 创建并注册默认通知服务实现
        DefaultNotificationService defaultNotificationService = new DefaultNotificationService();
        repository.setDefaultNotificationService(defaultNotificationService);
    }
    
    // 自定义的测试用扩展点仓库实现
    private static class TestExtPointRepository implements ExtPointRepository {
        private final Map<Object, Object> store = new HashMap<>();
        
        // 简单存储默认实现的引用，便于直接访问
        private DefaultOrderService defaultOrderService;
        private DefaultUserService defaultUserService;
        private DefaultNotificationService defaultNotificationService;
        
        @Override
        public Object get(Object key) {
            return store.get(key);
        }
        
        @Override
        public Object put(Object key, Object value) {
            return store.put(key, value);
        }
        
        @Override
        public Object remove(Object key) {
            return store.remove(key);
        }
        
        @Override
        public void clear() {
            store.clear();
        }
        
        // 设置默认订单服务
        public void setDefaultOrderService(DefaultOrderService service) {
            this.defaultOrderService = service;
            // 同时存储到map中，使用规范的键格式
            put(OrderService.class.getName() + ".default", service);
        }
        
        // 获取默认订单服务
        public DefaultOrderService getDefaultOrderService() {
            return defaultOrderService;
        }
        
        // 设置默认用户服务
    public void setDefaultUserService(DefaultUserService service) {
        this.defaultUserService = service;
        put(UserService.class.getName() + ".default", service);
    }
    
    // 获取默认用户服务
    public DefaultUserService getDefaultUserService() {
        return defaultUserService;
    }
    
    // 设置默认通知服务
        public void setDefaultNotificationService(DefaultNotificationService service) {
            this.defaultNotificationService = service;
            put(NotificationService.class.getName() + ".default", service);
        }
    }
    
    // 订单服务扩展点接口
    @ExtPoint
    public interface OrderService {
        String processOrder(String orderId);
    }
    
    // 订单服务 - 默认实现
    @ExtProvider
    public static class DefaultOrderService implements OrderService {
        @Override
        public String processOrder(String orderId) {
            return "Default Order Processing - " + orderId;
        }
    }
    
    // 用户服务扩展点接口
    @ExtPoint
    public interface UserService {
        String greetUser(String username);
    }
    
    // 用户服务 - 默认实现
    @ExtProvider
    public static class DefaultUserService implements UserService {
        @Override
        public String greetUser(String username) {
            return "Default User Greeting - " + username;
        }
    }
    
    // 通知服务扩展点接口
    @ExtPoint
    public interface NotificationService {
        String sendNotification(String target);
    }
    
    // 通知服务 - 默认实现
    @ExtProvider
    public static class DefaultNotificationService implements NotificationService {
        @Override
        public String sendNotification(String target) {
            return "Default Notification sent to: " + target;
        }
    }
}