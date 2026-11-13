package com.bone.masterdata.testsupport.goldenrecord;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 真实 HTTP 联调的端口装配：<b>仅测试可见</b>地把三个终局出站端口接到内存夹具。
 *
 * <p>三个 {@code @Bean} 方法返回具体夹具类型（同时实现对应出站端口），Spring 按端口接口注入时可唯一匹配； 不再额外注册接口别名，避免同实例两个 bean 定义造成
 * {@code NoUniqueBeanDefinitionException}。
 *
 * <p>生产制品里没有这三个 bean，因此 {@code golden-record.enabled=true} 也不会误装配出一个「能调用但落不了库」
 * 的端点；这正是能力开关要守的边界。测试夹具实现的是端口契约，不代表生产持久化已交付。
 */
@TestConfiguration
public class GoldenRecordHttpTestConfiguration {

  @Bean
  public InMemoryGoldenRecordAdapter goldenRecordPort() {
    return new InMemoryGoldenRecordAdapter();
  }

  @Bean
  public InMemoryEntityKeyMappingAdapter entityKeyMappingPort() {
    return new InMemoryEntityKeyMappingAdapter();
  }

  @Bean
  public InMemoryMasterDataOutboxAdapter masterDataOutboxPort() {
    return new InMemoryMasterDataOutboxAdapter();
  }
}
