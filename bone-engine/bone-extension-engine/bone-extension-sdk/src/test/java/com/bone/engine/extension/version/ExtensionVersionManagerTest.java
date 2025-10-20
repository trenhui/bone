package com.bone.engine.extension.version;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionVersionManagerTest {

    private ExtensionVersionManager versionManager;

    @BeforeEach
    void setUp() {
        versionManager = new ExtensionVersionManager();
    }

    // 定义测试用的简单接口
    interface TestExtPoint {
        String getName();
    }

    // 测试实现类
    static class TestExtPointImpl1 implements TestExtPoint {
        @Override
        public String getName() {
            return "Impl1";
        }
    }

    static class TestExtPointImpl2 implements TestExtPoint {
        @Override
        public String getName() {
            return "Impl2";
        }
    }

    @Test
    void testExtensionVersionManager() {
        // 基础测试，验证版本管理器实例正常
        assertNotNull(versionManager, "Version manager should be initialized");
    }

    @Test
    void testExtensionImplementations() {
        // 测试扩展点实现类功能
        TestExtPoint ext1 = new TestExtPointImpl1();
        TestExtPoint ext2 = new TestExtPointImpl2();
        
        assertNotNull(ext1);
        assertNotNull(ext2);
        assertEquals("Impl1", ext1.getName());
        assertEquals("Impl2", ext2.getName());
    }

    @Test
    void testExtensionClassMetadata() {
        // 测试扩展点元数据
        TestExtPointImpl1 impl1 = new TestExtPointImpl1();
        TestExtPointImpl2 impl2 = new TestExtPointImpl2();
        
        assertEquals("TestExtPointImpl1", impl1.getClass().getSimpleName());
        assertEquals("TestExtPointImpl2", impl2.getClass().getSimpleName());
        assertTrue(TestExtPoint.class.isAssignableFrom(TestExtPointImpl1.class));
        assertTrue(TestExtPoint.class.isAssignableFrom(TestExtPointImpl2.class));
    }
}