import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 最简单的测试类，位于默认包中，不依赖任何项目代码或Spring配置
 */
public class SimpleTest {
    
    @Test
    public void testBasicAssertion() {
        assertTrue(true, "This test should pass");
    }
}