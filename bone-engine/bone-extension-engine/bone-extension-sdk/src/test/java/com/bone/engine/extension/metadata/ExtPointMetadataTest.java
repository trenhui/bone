package com.bone.engine.extension.metadata;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.Extension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.bone.engine.extension.metadata.example.ValidationResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 扩展点元数据测试类
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = TestConfig.class)
@TestPropertySource(properties = {
    "bone.extension.cache.enabled=true",
    "bone.extension.metadata.enabled=true"
})
public class ExtPointMetadataTest {
    
    @Autowired
    private ExtPointMetadataCollector metadataCollector;
    
    @Autowired
    private ExtPointMetadataExporter metadataExporter;
    
    @Autowired
    private ExtPointRoutingConfigManager routingConfigManager;
    
    @Autowired
    private ExtConfigPropertyManager configPropertyManager;
    
    @BeforeEach
    public void setUp() {
        // 刷新元数据
        metadataCollector.refresh();
        
        // 清除测试前的缓存
        routingConfigManager.clearAll();
        configPropertyManager.clearAllCache();
    }
    
    /**
     * 测试扩展点元数据收集
     */
    @Test
    public void testExtPointMetadataCollection() {
        // 获取测试扩展点的元数据
        ExtPointMetadata metadata = metadataCollector.getExtPointMetadata(TestExtPoint.class.getName());
        assertNotNull(metadata);// 验证元数据基本信息
        assertEquals("This is a test extension point", metadata.getDescription());
        assertEquals("metadata test", metadata.getOwner());
        assertFalse(metadata.isDeprecated());
    }
    
    /**
     * 测试扩展实现元数据收集
     */
    @Test
    public void testExtImplMetadataCollection() {
        ExtPointMetadata metadata = metadataCollector.getExtPointMetadata(TestExtPoint.class.getName());
        assertNotNull(metadata);
        assertEquals(2, metadata.getImplementations().size());
        
        // 简化实现检查，只验证存在性
        assertNotNull(metadata.getImplementations().get(0));
        assertNotNull(metadata.getImplementations().get(1));
    }
    
    /**
     * 测试元数据查询
     */
    @Test
    public void testMetadataQuery() {
        // 测试获取所有扩展点接口
        Map<String, ExtPointMetadata> allMetadata = metadataCollector.getAllExtPointMetadata();
        assertNotNull(allMetadata);
        assertTrue(allMetadata.containsKey(TestExtPoint.class.getName()));
        
        // 获取扩展点元数据
        ExtPointMetadata metadata = metadataCollector.getExtPointMetadata(TestExtPoint.class.getName());
        assertNotNull(metadata, "扩展点元数据不应为null");
        // 检查实现列表
        assertNotNull(metadata.getImplementations(), "实现列表不应为null");
        assertFalse(metadata.getImplementations().isEmpty(), "应包含实现");
    }
    
    /**
     * 测试元数据刷新
     */
    @Test
    public void testMetadataRefresh() {
        int initialCount = metadataCollector.getAllExtPointMetadata().size();
        metadataCollector.refresh();
        assertEquals(initialCount, metadataCollector.getAllExtPointMetadata().size());
    }
    
    /**
     * 测试元数据导出
     */
    @Test
    public void testMetadataExport() {
        // 测试摘要导出
        String summaryJson = metadataExporter.exportAsSummaryJson();
        assertNotNull(summaryJson);
        assertTrue(summaryJson.length() > 0);
    }
    
    /**
     * 测试路由配置管理
     */
    @Test
    public void testRoutingConfigManager() {
        String interfaceName = TestExtPoint.class.getName();
        String implClassName = TestImplementation1.class.getName();
        
        // 创建测试配置
        Map<String, String> config = new HashMap<>();
        config.put("priority", "50");
        config.put("isDefault", "true");
        
        // 更新配置
        boolean updated = routingConfigManager.updateRoutingConfig(interfaceName, implClassName, config);
        assertTrue(updated);
        
        // 验证配置
        Map<String, String> retrievedConfig = routingConfigManager.getRoutingConfig(interfaceName, implClassName);
        assertNotNull(retrievedConfig);
        assertEquals("50", retrievedConfig.get("priority"));
        assertEquals("true", retrievedConfig.get("isDefault"));
    }
    
    /**
     * 测试配置属性管理
     */
    @Test
    public void testConfigPropertyManager() {
        String interfaceName = TestExtPoint.class.getName();
        String implClassName = TestImplementation1.class.getName();
        
        // 注册属性
        Map<String, String> properties = new HashMap<>();
        properties.put("timeout", "1000");
        properties.put("maxRetries", "3");
        configPropertyManager.registerProperties(interfaceName, implClassName, properties);
        
        // 验证属性
        Map<String, String> retrievedProperties = configPropertyManager.getProperties(interfaceName, implClassName);
        assertEquals("1000", retrievedProperties.get("timeout"));
        
        // 更新属性
        ValidationResult result = configPropertyManager.updateProperty(interfaceName, implClassName, "timeout", "2000");
        assertTrue(result.isValid());
        assertEquals("2000", configPropertyManager.getProperty(interfaceName, implClassName, "timeout"));
    }
    
    // 测试扩展点接口
    @ExtPoint(description = "This is a test extension point", 
              owner = "metadata test", 
              category = "test",
              tags = {"test", "metadata"})
    public interface TestExtPoint {
        String execute(String input);
    }
    
    // 测试实现类1
    @Extension(bizCode = "test", 
               useCase = "default",
               priority = 10)
    public static class TestImplementation1 implements TestExtPoint {
        @Override
        public String execute(String input) {
            return "Impl1: " + input;
        }
    }
    
    // 测试实现类2
    @Extension(bizCode = "test", 
               useCase = "alternative",
               priority = 20, 
               isDefault = true)
    public static class TestImplementation2 implements TestExtPoint {
        @Override
        public String execute(String input) {
            return "Impl2: " + input;
        }
    }
}