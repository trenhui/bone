package com.bone.metadata.sdk.test.config;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import com.bone.metadata.sdk.support.config.*;
import com.bone.metadata.sdk.support.context.RequestContext;
import com.bone.metadata.sdk.support.config.MetadataSdkProperties;
import feign.RequestInterceptor;
import org.mockito.Mockito;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.*;

@Configuration
@EnableAutoConfiguration
@ImportAutoConfiguration({
        MetadataAutoConfiguration.class,
        SqlRepositoryAutoConfiguration.class,
        InterceptorAutoConfiguration.class,
        FeignAutoConfiguration.class
})
// 简化ComponentScan配置，确保排除所有可能的TestConfig冲突
@ComponentScan(
    // 精确指定需要扫描的核心包
    basePackages = {
        "com.bone.metadata.sdk.extension",
        "com.bone.metadata.sdk.support.config",
        "com.bone.metadata.sdk.test.repository",
        "com.bone.metadata.sdk.test.service"
    },
    // 使用正则表达式过滤器排除特定的TestConfig类
    excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*TestConfig$")
)
@EnableSqlRepositories(basePackages = "com.bone.metadata.sdk.test.repository.proxy")
@EnableFeignClients("com.bone.metadata.sdk.metadata.client")
@EnableConfigurationProperties(MetadataSdkProperties.class)
public class TestConfig {

    @Autowired
    private Environment environment;

    //    @Bean
//    public MetaPermissionService metaPermissionService() {
//        return new DefaultMetaPermissionService();
//    }
    @Bean
    @Primary
    public H2ColumnAllocationDialect h2ColumnAllocationDialect() {
        return new H2ColumnAllocationDialect();
    }


    @Bean
    @Primary
    public DataSource dataSource() {
        // 让 Spring Boot 自动配置 HikariCP
        return DataSourceBuilder.create()
                .url(environment.getProperty("spring.datasource.url"))
                .username(environment.getProperty("spring.datasource.username"))
                .password(environment.getProperty("spring.datasource.password"))
                .build();
    }

    @Bean
    @Primary
    public NamedParameterJdbcOperations jdbc(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
    
    /**
     * 配置ExceptionHandler
     */
    @Bean
    public ExceptionHandler exceptionHandler() {
        return new ExceptionHandler() {
            @Override
            public RuntimeException handleException(Exception e) {
                return new RuntimeException(e);
            }
            
            @Override
            public RuntimeException handleException(Exception e, String message) {
                return new RuntimeException(message, e);
            }
            
            @Override
            public void logException(Exception e) {
                System.out.println("Exception logged: " + e.getMessage());
            }
        };
    }
    }


    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName("localhost");
        config.setPort(6379);
        return new LettuceConnectionFactory(config);
    }

    // Mock Redis 相关配置保持不变
    @Bean
    public RedissonClient redissonClient() throws InterruptedException {
        // 1. Mock RedissonClient
        RedissonClient redissonMock = Mockito.mock(RedissonClient.class);
        RLock lockMock = Mockito.mock(RLock.class);
        Mockito.when(lockMock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        Mockito.when(redissonMock.getLock(anyString())).thenReturn(lockMock);

        // 2. Mock RAtomicLong
        RAtomicLong atomicLongMock = Mockito.mock(RAtomicLong.class);
        AtomicBoolean initialized = new AtomicBoolean(false);
        Mockito.when(atomicLongMock.isExists()).thenAnswer(inv -> initialized.get());
        Mockito.when(atomicLongMock.get()).thenAnswer(inv -> initialized.get() ? 100L : 0L);
        Mockito.when(atomicLongMock.compareAndSet(eq(0L), anyLong())).thenAnswer(inv -> {
            initialized.set(true);
            Mockito.when(atomicLongMock.get()).thenReturn(inv.getArgument(1));
            return true;
        });
        Mockito.when(atomicLongMock.getAndAdd(anyInt())).thenAnswer(inv -> {
            int delta = inv.getArgument(0);
            long current = atomicLongMock.get();
            Mockito.when(atomicLongMock.get()).thenReturn(current + delta);
            return current;
        });

        Mockito.when(redissonMock.getAtomicLong(anyString())).thenReturn(atomicLongMock);

        return redissonMock;
    }

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.afterPropertiesSet();
        return template;
    }


    @Bean
    @Primary
    public RequestContext testRequestContext() {
        return new RequestContext() {
            @Override
            public String getRequestId() {
                return "test-request-id";
            }

            @Override
            public void init() {
            }

            @Override
            public void clear() {
            }
        };
    }

    @Bean
    @Primary
    public RequestInterceptor testAuthInterceptor() {
        return template -> {
            template.header("Authorization", "Bearer test-token");
            template.header("X-Request-ID", "test-request-id");
        };
    }


}