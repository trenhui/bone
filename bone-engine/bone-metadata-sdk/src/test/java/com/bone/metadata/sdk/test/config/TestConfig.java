package com.bone.metadata.sdk.test.config;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import com.bone.metadata.sdk.support.config.*;
import com.bone.metadata.sdk.domain.exception.ExceptionHandler;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.mockito.invocation.InvocationOnMock;
import org.redisson.api.RAtomicLong;
import com.bone.metadata.sdk.sql.dialect.H2ColumnAllocationDialect;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import com.bone.metadata.sdk.support.dataSource.DataSourceManager;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.*;

/**
 * 测试配置类
 * <p>提供测试环境所需的核心组件配置，包括数据库、事务、缓存等基础设施</p>
 * <p>遵循Spring Boot测试配置最佳实践，确保测试环境的一致性和隔离性</p>
 */
@Configuration
@EnableAutoConfiguration
@ImportAutoConfiguration({
        MetadataAutoConfiguration.class,
        SqlRepositoryAutoConfiguration.class,
        InterceptorAutoConfiguration.class,
        FeignAutoConfiguration.class
})
@ComponentScan(
    // 精确指定需要扫描的核心包，避免不必要的组件扫描
    basePackages = {
        "com.bone.metadata.sdk.extension",
        "com.bone.metadata.sdk.support.config",
        "com.bone.metadata.sdk.test.repository",
        "com.bone.metadata.sdk.test.service"
    },
    // 排除所有TestConfig类，避免配置冲突
    excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*TestConfig$")
)
@EnableSqlRepositories(basePackages = "com.bone.metadata.sdk.test.repository.proxy")
@EnableFeignClients("com.bone.metadata.sdk.metadata.client")
@EnableConfigurationProperties(MetadataSdkProperties.class)
public class TestConfig {

    @Autowired
    private Environment environment;

    /**
     * 配置H2数据库方言，支持列分配和类型转换
     * @return H2方言实例
     */
    @Bean
    @Primary
    public H2ColumnAllocationDialect h2ColumnAllocationDialect() {
        return new H2ColumnAllocationDialect();
    }

    /**
     * 配置数据源，使用主源码中的DataSourceManager
     * @return 配置完成的数据源
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        return DataSourceBuilder.create()
                .url(environment.getProperty("spring.datasource.url"))
                .username(environment.getProperty("spring.datasource.username"))
                .password(environment.getProperty("spring.datasource.password"))
                .build();
    }

    /**
     * 配置命名参数JDBC操作模板，提供更方便的SQL执行方式
     * @param dataSource 数据源
     * @return 命名参数JDBC操作实例
     */
    @Bean
    @Primary
    public NamedParameterJdbcOperations jdbc(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    /**
     * 配置事务管理器，管理数据库事务
     * @param dataSource 数据源
     * @return 事务管理器实例
     */
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
    
    /**
     * 配置异常处理器，统一处理应用异常
     * @return 异常处理器模拟实例
     */
    @Bean
    public ExceptionHandler exceptionHandler() {
        ExceptionHandler mockHandler = Mockito.mock(ExceptionHandler.class);
        // 配置异常处理行为
        Mockito.when(mockHandler.handleException(any(Exception.class)))
                .thenAnswer(invocation -> {
                    Exception ex = invocation.getArgument(0);
                    return new RuntimeException("Mock exception handler: " + ex.getMessage(), ex);
                });
        return mockHandler;
    }

    /**
     * 配置Redis连接工厂，提供Redis连接支持
     * @return Redis连接工厂实例
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName("localhost");
        config.setPort(6379);
        return new LettuceConnectionFactory(config);
    }

    /**
     * 配置Redisson客户端模拟，避免测试依赖真实Redis
     * @return 模拟的Redisson客户端实例
     */
    @Bean
    public RedissonClient redissonClient() {
        // 创建RedissonClient模拟实例
        RedissonClient redissonMock = Mockito.mock(RedissonClient.class);
        
        // 模拟分布式锁功能
        RLock lockMock = Mockito.mock(RLock.class);
        // 使用显式的Answer实现来正确处理InterruptedException
        try {
            Mockito.when(lockMock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        } catch (InterruptedException e) {
            // 不会执行到这里，仅用于编译通过
        }
        Mockito.when(redissonMock.getLock(anyString())).thenReturn(lockMock);

        // 模拟原子长整型功能
        RAtomicLong atomicLongMock = createMockedRAtomicLong();
        Mockito.when(redissonMock.getAtomicLong(anyString())).thenReturn(atomicLongMock);

        return redissonMock;
    }
    
    /**
     * 创建模拟的RAtomicLong实例
     * @return 模拟的RAtomicLong
     */
    private RAtomicLong createMockedRAtomicLong() {
        RAtomicLong atomicLongMock = Mockito.mock(RAtomicLong.class);
        AtomicBoolean initialized = new AtomicBoolean(false);
        
        // 配置存在性检查
        Mockito.when(atomicLongMock.isExists()).thenAnswer(inv -> initialized.get());
        
        // 配置获取值的行为
        Mockito.when(atomicLongMock.get()).thenAnswer(inv -> initialized.get() ? 100L : 0L);
        
        // 配置CAS操作
        Mockito.when(atomicLongMock.compareAndSet(eq(0L), anyLong())).thenAnswer(inv -> {
            initialized.set(true);
            long newValue = inv.getArgument(1);
            Mockito.when(atomicLongMock.get()).thenReturn(newValue);
            return true;
        });
        
        // 配置自增操作
        Mockito.when(atomicLongMock.getAndAdd(anyInt())).thenAnswer(inv -> {
            int delta = inv.getArgument(0);
            long current = atomicLongMock.get();
            Mockito.when(atomicLongMock.get()).thenReturn(current + delta);
            return current;
        });
        
        return atomicLongMock;
    }

    // RedisTemplate配置被注释，因为缺少依赖
    // @Bean
    // @Primary
    // @SuppressWarnings("unchecked")
    // public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
    //     RedisTemplate<String, Object> template = new RedisTemplate<>();
    //     template.setConnectionFactory(connectionFactory);
    //     template.afterPropertiesSet();
    //     return template;
    // }


    // RequestContext配置被注释，因为缺少依赖
    // @Bean
    // @Primary
    // public RequestContext testRequestContext() {
    //     return new RequestContext() {
    //         @Override
    //         public String getRequestId() {
    //             return "test-request-id";
    //         }

    //         @Override
    //         public void init() {
    //         }

    //         @Override
    //         public void clear() {
    //         }
    //     };
    // }

    // RequestInterceptor配置被注释，因为缺少依赖
    // @Bean
    // @Primary
    // public RequestInterceptor testAuthInterceptor() {
    //     return template -> {
    //         template.header("Authorization", "Bearer test-token");
    //         template.header("X-Request-ID", "test-request-id");
    //     };
    // }

    // ReentrantLockUtil配置被注释，因为缺少依赖
    // @Bean
    // public ReentrantLockUtil reentrantLockUtil() {
    //     return new ReentrantLockUtil() {
    //         @Override
    //         public ReentrantLock getLock(String key) {
    //             return new ReentrantLock();
    //         }
    //         @Override
    //         public boolean tryLock(String key, long timeoutMillis) {
    //             try {
    //                 return new ReentrantLock().tryLock(timeoutMillis, TimeUnit.MILLISECONDS);
    //             } catch (InterruptedException e) {
    //                 Thread.currentThread().interrupt();
    //                 return false;
    //             }
    //         }
    //         @Override
    //         public void releaseLock(String key) {
    //         }
    //     };
    // }


}