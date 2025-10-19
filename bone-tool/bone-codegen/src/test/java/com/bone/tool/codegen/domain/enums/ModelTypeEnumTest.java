package com.bone.tool.codegen.domain.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ModelTypeEnum单元测试
 */
class ModelTypeEnumTest {

    @Test
    void testValueOfByType() {
        // 测试有效值
        assertEquals(ModelTypeEnum.SAAS, ModelTypeEnum.valueOf(1));
        assertEquals(ModelTypeEnum.DDD, ModelTypeEnum.valueOf(2));
        
        // 测试无效值
        assertThrows(RuntimeException.class, () -> ModelTypeEnum.valueOf(999));
    }
    
    @Test
    void testFromName() {
        // 测试有效值（不区分大小写）
        assertEquals(ModelTypeEnum.SAAS, ModelTypeEnum.fromName("saas"));
        assertEquals(ModelTypeEnum.SAAS, ModelTypeEnum.fromName("SAAS"));
        assertEquals(ModelTypeEnum.DDD, ModelTypeEnum.fromName("ddd"));
        
        // 测试无效值
        assertNull(ModelTypeEnum.fromName("unknown"));
        assertNull(ModelTypeEnum.fromName(""));
        
        // 测试null值
        assertNull(ModelTypeEnum.fromName(null));
    }
    
    @Test
    void testGetType() {
        assertEquals(Integer.valueOf(1), ModelTypeEnum.SAAS.getType());
        assertEquals(Integer.valueOf(2), ModelTypeEnum.DDD.getType());
    }
    
    @Test
    void testGetName() {
        assertEquals("saas", ModelTypeEnum.SAAS.getName());
        assertEquals("ddd", ModelTypeEnum.DDD.getName());
    }
    
    @Test
    void testGetJavaTemplates() {
        // 测试占位符方法返回空HashMap
        assertNotNull(ModelTypeEnum.SAAS.getJavaTemplates("test"));
        assertTrue(ModelTypeEnum.SAAS.getJavaTemplates("test").isEmpty());
        
        assertNotNull(ModelTypeEnum.DDD.getJavaTemplates("test"));
        assertTrue(ModelTypeEnum.DDD.getJavaTemplates("test").isEmpty());
    }
    
    @Test
    void testGetConfigTemplates() {
        // 测试占位符方法返回空HashMap
        assertNotNull(ModelTypeEnum.SAAS.getConfigTemplates("test"));
        assertTrue(ModelTypeEnum.SAAS.getConfigTemplates("test").isEmpty());
        
        assertNotNull(ModelTypeEnum.DDD.getConfigTemplates("test"));
        assertTrue(ModelTypeEnum.DDD.getConfigTemplates("test").isEmpty());
    }
}