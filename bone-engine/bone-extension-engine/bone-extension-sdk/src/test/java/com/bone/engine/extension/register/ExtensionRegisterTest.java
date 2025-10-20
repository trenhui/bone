package com.bone.engine.extension.register;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.config.ExtensionConfigProperties;
import com.bone.engine.extension.event.ExtensionEventPublisher;
import com.bone.engine.extension.repository.ExtPointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

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
    private ExtensionConfigProperties configProperties;

    @Mock
    private ApplicationContext applicationContext;

    @InjectMocks
    private ExtensionRegister extensionRegister;

    @BeforeEach
    public void setup() {
        // 设置默认配置
        when(configProperties.isVersioningEnabled()).thenReturn(true);
        when(configProperties.isStrictModeEnabled()).thenReturn(false);
        when(configProperties.isVerboseLoggingEnabled()).thenReturn(true);
        when(configProperties.isEventPublishingEnabled()).thenReturn(true);
        when(configProperties.isParallelRegistrationEnabled()).thenReturn(false);
        when(configProperties.getScanPackages()).thenReturn(new String[]{"com.example"});

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
        Map<String, Object> beans = Collections.singletonMap("testProvider", provider);
        when(applicationContext.getBeansWithAnnotation(Extension.class)).thenReturn(beans);

        // 执行注册
        extensionRegister.registerExtension(provider);

        // 验证结果
        verify(extPointRepository, atLeastOnce()).put(anyString(), eq(provider));
        verify(eventPublisher, atLeastOnce()).publishBeforeRegister(any(), anyString(), anyString());
        verify(eventPublisher, atLeastOnce()).publishAfterRegister(any(), anyString(), anyString());
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
        verify(extPointRepository, never()).put(anyString(), any());
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
        verify(extPointRepository, never()).put(anyString(), any());
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
        verify(extPointRepository, never()).put(anyString(), any());
    }

    /**
     * 测试在严格模式下注册无效的扩展提供者
     */
    @Test
    public void testRegisterInvalidExtensionInStrictMode() {
        // 设置为严格模式
        when(configProperties.isStrictModeEnabled()).thenReturn(true);

        // 准备测试数据 - 没有@Extension注解
        NoAnnotationProvider provider = new NoAnnotationProvider();

        // 执行注册并验证异常
        assertThrows(IllegalArgumentException.class, () -> {
            extensionRegister.registerExtension(provider);
        });
    }

    /**
     * 测试扩展点接口
     */
    @ExtPoint
    public interface TestExtPoint {
        String test();
    }

    /**
     * 有效的扩展提供者实现
     */
    @Extension(version = "1.0.0")
    public class TestExtensionProvider implements TestExtPoint {
        @Override
        public String test() {
            return "test";
        }
    }

    /**
     * 没有@Extension注解的提供者
     */
    public class NoAnnotationProvider implements TestExtPoint {
        @Override
        public String test() {
            return "no-annotation";
        }
    }

    /**
     * 有@Extension注解但没有实现@ExtPoint接口的提供者
     */
    @Extension(version = "1.0.0")
    public class NoExtPointProvider {
        public String test() {
            return "no-ext-point";
        }
    }

    /**
     * 实现多个@ExtPoint接口的提供者
     */
    @ExtPoint
    public interface SecondTestExtPoint {
        String secondTest();
    }

    @Extension(version = "2.0.0")
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