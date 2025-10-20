package com.bone.engine.extension.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExtensionConfigProperties的单元测试类
 */
@SpringBootTest(classes = {ExtensionConfigProperties.class})
public class ExtensionConfigPropertiesTest {

    @Autowired
    private ExtensionConfigProperties configProperties;

    @Test
    public void testDefaultValues() {
        // 验证默认值设置正确
        assertTrue(configProperties.isEnabled(), "Extension framework should be enabled by default");
        assertTrue(configProperties.isVersioningEnabled(), "Versioning should be enabled by default");
        assertTrue(configProperties.isCacheEnabled(), "Cache should be enabled by default");
        assertTrue(configProperties.isAsyncEventEnabled(), "Async events should be enabled by default");
        assertTrue(configProperties.isLoggingEnabled(), "Logging should be enabled by default");
        
        assertEquals(1000, configProperties.getAnnotationCacheMaxSize(), "Default annotation cache max size should be 1000");
        assertEquals(5000, configProperties.getRouteCacheMaxSize(), "Default route cache max size should be 5000");
        assertEquals(3600, configProperties.getCacheExpireAfterWrite(), "Default cache expire time should be 3600 seconds");
        
        assertEquals("DEFAULT", configProperties.getDefaultTenantId(), "Default tenant ID should be 'DEFAULT'");
        assertEquals("GENERAL", configProperties.getDefaultBusinessType(), "Default business type should be 'GENERAL'");
    }

    @Test
    public void testSettersAndGetters() {
        // 测试setter和getter方法
        configProperties.setEnabled(false);
        assertFalse(configProperties.isEnabled(), "Enabled flag should be false after setting");
        
        configProperties.setVersioningEnabled(false);
        assertFalse(configProperties.isVersioningEnabled(), "Versioning enabled flag should be false after setting");
        
        configProperties.setCacheEnabled(false);
        assertFalse(configProperties.isCacheEnabled(), "Cache enabled flag should be false after setting");
        
        configProperties.setAnnotationCacheMaxSize(2000);
        assertEquals(2000, configProperties.getAnnotationCacheMaxSize(), "Annotation cache max size should be updated");
        
        configProperties.setRouteCacheMaxSize(10000);
        assertEquals(10000, configProperties.getRouteCacheMaxSize(), "Route cache max size should be updated");
        
        configProperties.setCacheExpireAfterWrite(7200);
        assertEquals(7200, configProperties.getCacheExpireAfterWrite(), "Cache expire time should be updated");
        
        configProperties.setDefaultTenantId("TEST_TENANT");
        assertEquals("TEST_TENANT", configProperties.getDefaultTenantId(), "Default tenant ID should be updated");
    }

    @Test
    public void testComponentProperties() {
        // 测试组件属性相关方法
        Map<String, String> componentProps = new HashMap<>();
        componentProps.put("timeout", "5000");
        componentProps.put("retryCount", "3");
        
        Map<String, Map<String, String>> allProps = new HashMap<>();
        allProps.put("testComponent", componentProps);
        
        configProperties.setComponentProperties(allProps);
        
        // 测试获取组件属性
        Map<String, String> retrievedProps = configProperties.getComponentProperty("testComponent");
        assertNotNull(retrievedProps, "Component properties should not be null");
        assertEquals(2, retrievedProps.size(), "Component properties size should match");
        assertEquals("5000", retrievedProps.get("timeout"), "Component property value should match");
        
        // 测试获取不存在的组件属性
        Map<String, String> nonExistentProps = configProperties.getComponentProperty("nonExistentComponent");
        assertNotNull(nonExistentProps, "Should return empty map for non-existent component");
        assertTrue(nonExistentProps.isEmpty(), "Map should be empty for non-existent component");
        
        // 测试获取特定属性值
        String timeoutValue = configProperties.getComponentProperty("testComponent", "timeout", "default");
        assertEquals("5000", timeoutValue, "Property value should match");
        
        // 测试获取不存在的属性，应返回默认值
        String unknownValue = configProperties.getComponentProperty("testComponent", "unknownProp", "defaultValue");
        assertEquals("defaultValue", unknownValue, "Should return default value for unknown property");
    }

    @Test
    public void testDefaultVersions() {
        // 测试默认版本配置
        Map<String, String> defaultVersions = new HashMap<>();
        defaultVersions.put("com.example.ExtPoint1", "1.2.0");
        defaultVersions.put("com.example.ExtPoint2", "2.0.0");
        
        configProperties.setDefaultVersions(defaultVersions);
        
        Map<String, String> retrievedVersions = configProperties.getDefaultVersions();
        assertNotNull(retrievedVersions, "Default versions should not be null");
        assertEquals(2, retrievedVersions.size(), "Default versions size should match");
        assertEquals("1.2.0", retrievedVersions.get("com.example.ExtPoint1"), "Default version should match");
    }

    @Test
    public void testScanPackages() {
        // 测试扫描包配置
        String[] packages = {"com.example.extension", "com.another.extension"};
        configProperties.setScanPackages(packages);
        
        String[] retrievedPackages = configProperties.getScanPackages();
        assertNotNull(retrievedPackages, "Scan packages should not be null");
        assertEquals(2, retrievedPackages.length, "Scan packages length should match");
        assertEquals("com.example.extension", retrievedPackages[0], "First scan package should match");
    }
}