package com.bone.engine.extension.register;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.event.ExtensionEventPublisher;
import com.bone.engine.extension.repository.ExtPointRepository;
import org.junit.jupiter.api.BeforeEach;
import com.bone.engine.extension.annotation.ExtPointDoc;
import com.bone.engine.extension.annotation.ExtensionDoc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import com.bone.engine.extension.config.ExtensionProperties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ExtensionRegister测试类，验证扩展点注册器的功能正确性
 */
@ExtendWith(MockitoExtension.class)
public class ExtensionRegisterTest {

    @Mock
    private ExtPointRepository extPointRepository;

    @Mock
    private ExtensionEventPublisher eventPublisher;

    @Mock
    private ApplicationContext applicationContext;
    
    @Mock
    private ExtensionProperties extensionProperties;

    private ExtensionRegister extensionRegister;

    @BeforeEach
    public void setup() {
        // 先创建ScanConfig mock并设置行为
        ExtensionProperties.ScanConfig scanConfig = mock(ExtensionProperties.ScanConfig.class);
        when(scanConfig.getBasePackages()).thenReturn(new String[]{});
        
        // 在创建extensionRegister之前先设置ExtensionProperties的mock行为
        when(extensionProperties.getScan()).thenReturn(scanConfig);
        
        // 现在创建extensionRegister实例
        extensionRegister = new ExtensionRegister(extPointRepository, eventPublisher, extensionProperties);
        
        // 设置ApplicationContext
        extensionRegister.setApplicationContext(applicationContext);
    }

    /**
     * 测试注册有效的扩展提供者
     */
    @Test
    public void testRegisterValidExtension() {
        // 准备测试数据
        TestExtensionProvider provider = new TestExtensionProvider();
        
        // 执行注册
        extensionRegister.registerExtension(provider);

        // 验证结果 - 只验证我们关心的repository调用
        verify(extPointRepository).put(anyString(), eq(provider));
    }

    /**
     * 测试注册没有@Extension注解的提供者（非严格模式）
     */
    @Test
    public void testRegisterProviderWithoutAnnotation() {
        // 准备测试数据
        NoAnnotationProvider provider = new NoAnnotationProvider();

        // 执行注册
        extensionRegister.registerExtension(provider);

        // 验证结果 - 不应调用repository
        verifyNoInteractions(extPointRepository);
    }

    /**
     * 测试注册没有实现@ExtPoint接口的提供者（非严格模式）
     */
    @Test
    public void testRegisterProviderWithoutExtPointInterface() {
        // 准备测试数据
        NoExtPointProvider provider = new NoExtPointProvider();

        // 执行注册
        extensionRegister.registerExtension(provider);

        // 验证结果 - 不应调用repository
        verifyNoInteractions(extPointRepository);
    }

    /**
     * 测试注册重复的扩展提供者
     */
    @Test
    public void testRegisterDuplicateExtension() {
        // 准备测试数据
        TestExtensionProvider provider = new TestExtensionProvider();

        // 第一次注册
        extensionRegister.registerExtension(provider);
        
        // 重置mock
        reset(extPointRepository);

        // 第二次注册（应该被忽略）
        extensionRegister.registerExtension(provider);

        // 验证结果 - 不应再次调用repository
        verifyNoInteractions(extPointRepository);
    }

    /**
     * 测试在严格模式下注册无效的扩展提供者
     */
    @Test
    public void testRegisterInvalidExtensionInStrictMode() {
        // 准备测试数据 - 没有@Extension注解
        NoAnnotationProvider provider = new NoAnnotationProvider();

        // 执行注册（在非严格模式下不会抛出异常）
        extensionRegister.registerExtension(provider);
        
        // 验证结果 - 不应调用repository
        verifyNoInteractions(extPointRepository);
    }

    /**
     * 测试扩展点接口 - 主接口
     */
    @ExtPoint(
        name = "注册测试扩展点",
        description = "用于测试扩展注册功能的主扩展点接口",
        version = "1.0.0",
        category = "测试",
        domain = "扩展注册"
    )
    @ExtPointDoc(
        title = "注册测试主扩展点接口",
        domain = "扩展引擎",
        category = "注册机制",
        description = "该接口用于验证扩展注册器对扩展点实现的正确识别和注册功能。",
        usage = "在扩展注册测试中使用，验证注册逻辑。",
        bestPractices = "1. 确保接口方法简单明确\n2. 配合多种实现类进行全面测试\n3. 验证各种边缘情况",
        notes = "用于测试注册逻辑"
    )
    public interface TestExtPoint {
        /**
         * 执行测试方法
         * @return 测试结果
         */
        String test();
    }

    /**
     * 有效的扩展提供者实现
     */
    @Extension(
        name = "有效测试扩展实现",
        description = "符合所有要求的有效扩展实现",
        version = "1.0.0",
        priority = 100
    )
    @ExtensionDoc(
        description = "这是一个完全符合规范的扩展实现，用于测试正常注册流程。",
        notes = "用于验证正常注册路径"
    )
    public class TestExtensionProvider implements TestExtPoint {
        @Override
        public String test() {
            return "test";
        }
    }

    /**
     * 没有@Extension注解的提供者 - 用于测试无效注册
     */
    public class NoAnnotationProvider implements TestExtPoint {
        @Override
        public String test() {
            return "no-annotation";
        }
    }

    /**
     * 有@Extension注解但没有实现@ExtPoint接口的提供者 - 用于测试无效注册
     */
    @Extension(
        name = "非扩展点实现",
        description = "虽然有Extension注解但未实现ExtPoint接口",
        version = "1.0.0"
    )
    @ExtensionDoc(
        description = "这个类用于测试没有实现ExtPoint接口但有Extension注解的情况。",
        notes = "用于验证注册器的过滤逻辑"
    )
    public class NoExtPointProvider {
        public String test() {
            return "no-ext-point";
        }
    }

    /**
     * 测试扩展点接口 - 第二个接口
     */
    @ExtPoint(
        name = "第二个测试扩展点",
        description = "用于测试多扩展点实现的辅助接口",
        version = "1.0.0",
        category = "测试",
        domain = "扩展注册"
    )
    @ExtPointDoc(
        title = "多实现测试扩展点接口",
        domain = "扩展引擎",
        category = "多实现测试",
        description = "该接口用于验证一个实现类可以同时实现多个扩展点接口。",
        usage = "与TestExtPoint一起测试多接口实现场景。",
        notes = "用于测试多接口实现"
    )
    public interface SecondTestExtPoint {
        /**
         * 执行第二个测试方法
         * @return 测试结果
         */
        String secondTest();
    }

    /**
     * 实现多个@ExtPoint接口的提供者 - 用于测试多接口实现
     */
    @Extension(
        name = "多扩展点实现",
        description = "同时实现多个扩展点接口的实现类",
        version = "2.0.0",
        priority = 50
    )
    @ExtensionDoc(
        description = "这个类同时实现了两个扩展点接口，用于测试多接口实现的注册机制。",
        notes = "用于验证注册器对多接口实现的处理逻辑"
    )
    public class MultiExtPointProvider implements TestExtPoint, SecondTestExtPoint {
        @Override
        public String test() {
            return "multi-test";
        }

        @Override
        public String secondTest() {
            return "second-test";
        }
    }
}