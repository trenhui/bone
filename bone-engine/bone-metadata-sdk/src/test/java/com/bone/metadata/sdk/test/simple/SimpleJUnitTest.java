package com.bone.metadata.sdk.test.simple;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 极简的JUnit测试类，用于验证测试环境配置
 */
public class SimpleJUnitTest {
    
    /**
     * 最基本的测试方法，不依赖任何外部类
     */
    @Test
    public void testBasic() {
        assertTrue(true, "基本测试应该通过");
    }
}