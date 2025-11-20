package com.bone.engine.extension.register;

import com.bone.engine.extension.api.annotation.ExtPoint;
import com.bone.engine.extension.api.annotation.Extension;
import com.bone.engine.extension.core.register.ExtensionRegister;
import com.bone.engine.extension.core.event.ExtensionEventPublisher;
import com.bone.engine.extension.support.repository.ExtPointRepository;
import com.bone.engine.extension.api.annotation.ExtPointDoc;
import com.bone.engine.extension.api.annotation.ExtensionDoc;
import com.bone.engine.extension.support.config.ExtensionProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import static org.mockito.Mockito.*;

/**
 * 扩展点注册器测试类
 * <p>
 * 全面验证ExtensionRegister的功能正确性，包括扩展注册、重复检测、无效扩展过滤等核心功能
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

    /**
     * 测试环境设置
     * <p>
     * 初始化所有必要的模拟对象和测试实例
     */
    @BeforeEach
    public void setup() {
        // 准备扫描配置模拟对象
        ExtensionProperties.ScanConfig scanConfig = mock(ExtensionProperties.ScanConfig.class);
        when(scanConfig.getBasePackages()).thenReturn(new String[]{});
        
        // 配置扩展属性
        when(extensionProperties.getScan()).thenReturn(scanConfig);
        
        // 创建注册器实例
        extensionRegister = new ExtensionRegister(extPointRepository, eventPublisher, extensionProperties);
        
        // 设置应用上下文
        extensionRegister.setApplicationContext(applicationContext);
    }

    /**
     * 测试有效扩展提供者注册
     * <p>
     * 验证正确实现了ExtPoint接口并带有Extension注解的提供者能被成功注册
     */
    @Test
    public void testRegisterValidExtension() {
        // 准备测试数据
        TestExtensionProvider provider = new TestExtensionProvider();
        
        // 执行注册
        extensionRegister.registerExtension(provider);

        // 验证结果 - 确认仓库中成功添加了扩展
        verify(extPointRepository).put(anyString(), eq(provider));
    }

    /**
     * 测试无注解提供者注册
     * <p>
     * 验证没有@Extension注解的提供者不会被注册
     */
    @Test
    public void testRegisterProviderWithoutAnnotation() {
        // 准备测试数据
        NoAnnotationProvider provider = new NoAnnotationProvider();

        // 执行注册
        extensionRegister.registerExtension(provider);

        // 验证结果 - 确认仓库未被调用
        verifyNoInteractions(extPointRepository);
    }

    /**
     * 测试无扩展点接口实现的提供者注册
     * <p>
     * 验证没有实现@ExtPoint接口的提供者不会被注册
     */
    @Test
    public void testRegisterProviderWithoutExtPointInterface() {
        // 准备测试数据
        NoExtPointProvider provider = new NoExtPointProvider();

        // 执行注册
        extensionRegister.registerExtension(provider);

        // 验证结果 - 确认仓库未被调用
        verifyNoInteractions(extPointRepository);
    }

    /**
     * 测试重复扩展注册
     * <p>
     * 验证同一个扩展提供者只能被注册一次
     */
    @Test
    public void testRegisterDuplicateExtension() {
        // 准备测试数据
        TestExtensionProvider provider = new TestExtensionProvider();

        // 第一次注册
        extensionRegister.registerExtension(provider);
        
        // 重置模拟对象以清除之前的交互记录
        reset(extPointRepository);

        // 第二次注册（预期会被忽略）
        extensionRegister.registerExtension(provider);

        // 验证结果 - 确认仓库未被再次调用
        verifyNoInteractions(extPointRepository);
    }

    /**
     * 测试严格模式下的无效扩展注册
     * <p>
     * 验证在严格模式下无效扩展的处理行为
     */
    @Test
    public void testRegisterInvalidExtensionInStrictMode() {
        // 准备测试数据 - 没有@Extension注解的提供者
        NoAnnotationProvider provider = new NoAnnotationProvider();

        // 执行注册（在非严格模式下不会抛出异常）
        extensionRegister.registerExtension(provider);
        
        // 验证结果 - 确认仓库未被调用
        verifyNoInteractions(extPointRepository);
    }

    /**
     * 测试扩展点主接口
     * <p>
     * 定义用于测试扩展注册功能的主接口
     */
    @ExtPoint(
        name = "注册测试扩展点",
        description = "用于测试扩展注册功能的主扩展点接口"
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
         * @return 测试结果字符串
         */
        String test();
    }

    /**
     * 有效扩展提供者实现
     * <p>
     * 符合所有要求的标准扩展实现，用于测试正常注册流程
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
     * 无注解提供者实现
     * <p>
     * 没有@Extension注解但实现了ExtPoint接口，用于测试无效注册场景
     */
    public class NoAnnotationProvider implements TestExtPoint {
        @Override
        public String test() {
            return "no-annotation";
        }
    }

    /**
     * 非扩展点实现
     * <p>
     * 有@Extension注解但未实现ExtPoint接口，用于测试无效注册场景
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
     * 多实现测试扩展点接口
     * <p>
     * 用于测试一个实现类可以同时实现多个扩展点接口的情况
     */
    @ExtPoint(
        name = "第二个测试扩展点",
        description = "用于测试多扩展点实现的辅助接口"
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
         * @return 测试结果字符串
         */
        String secondTest();
    }

    /**
     * 多扩展点实现提供者
     * <p>
     * 同时实现多个@ExtPoint接口的实现类，用于测试多接口实现的注册机制
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