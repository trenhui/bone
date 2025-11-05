package com.bone.metadata.sdk.test.config;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import com.bone.metadata.sdk.support.config.InterceptorAutoConfiguration;
import com.bone.metadata.sdk.support.config.MetadataAutoConfiguration;
import com.bone.metadata.sdk.support.config.MetadataSdkProperties;
import com.bone.metadata.sdk.support.config.SqlRepositoryAutoConfiguration;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import feign.RequestInterceptor;
import org.mockito.Mockito;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
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
import java.util.HashMap;
import java.util.Map;
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
@ComponentScan("com.bone.metadata.sdk")
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
//    @Bean
//    @Primary
//    public H2ColumnAllocationDialect h2ColumnAllocationDialect() {
//        return new H2ColumnAllocationDialect();
//    }


    @Bean
    @Primary
    public DataSource dataSource() {
        // 修改点1：统一使用HikariDataSource
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(environment.getProperty("spring.datasource.url"));
        config.setUsername(environment.getProperty("spring.datasource.username"));
        config.setPassword(environment.getProperty("spring.datasource.password"));
        config.setPoolName("TestDBPool");

        // 设置数据库驱动
        String url = environment.getProperty("spring.datasource.url", "").toLowerCase();
        if (url.contains("mysql:")) {
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else {
            // H2或其他数据库
            config.setDriverClassName("org.h2.Driver");
        }

        // 修改点2：统一返回HikariDataSource实例
        return new HikariDataSource(config);
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


    public static class RequestContext {
        private ThreadLocal<Map<String, Object>> threadLocal = ThreadLocal.withInitial(() -> new HashMap<>());

        public void setAttribute(String key, Object value) {
            threadLocal.get().put(key, value);
        }

        public Object getAttribute(String key) {
            return threadLocal.get().get(key);
        }

        public void removeAttribute(String key) {
            threadLocal.get().remove(key);
        }

        public void clear() {
            threadLocal.remove();
        }
    }

    @Bean
    @Primary
    public RequestContext testRequestContext() {
        return new RequestContext();
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