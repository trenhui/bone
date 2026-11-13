package com.bone.masterdata.testsupport.changerequest;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 终局出站端口的<b>仅测试可见</b>装配。Bean 方法返回具体夹具类型，Spring 按端口接口注入时可唯一匹配； 生产制品中这些 bean 不存在，所以 {@code
 * change-request.enabled=true} 不会误装配出「能调用但落不了库」的端点。
 */
@TestConfiguration
public class ChangeRequestHttpTestConfiguration {

  @Bean
  public InMemoryChangeRequestAdapter changeRequestPort() {
    return new InMemoryChangeRequestAdapter();
  }

  @Bean
  public InMemoryOutboxRelayStore outboxRelayStore() {
    return new InMemoryOutboxRelayStore();
  }

  @Bean
  public InMemoryMasterDataProjection masterDataProjectionPort() {
    return new InMemoryMasterDataProjection();
  }

  @Bean
  public RecordingChangeRequestActivationAdapter changeRequestActivationPort(
      InMemoryOutboxRelayStore store) {
    return new RecordingChangeRequestActivationAdapter(store);
  }

  @Bean
  public RecordingChangeRequestWorkflowAdapter changeRequestWorkflowPort() {
    return new RecordingChangeRequestWorkflowAdapter();
  }
}
