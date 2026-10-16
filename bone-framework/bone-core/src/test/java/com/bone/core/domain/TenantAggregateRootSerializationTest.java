package com.bone.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * {@link TenantAggregateRoot#getTenantId()} 已加 {@code @JsonIgnore}：租户隔离由网关 / 拦截器在请求链路完成， {@code
 * tenantId} 不应随实体响应泄漏到前端（契约测试与前端均不消费该字段）。一处注解覆盖所有子类 （QualityIssue / MasterDataRecord /
 * MasterDataEntity 等）。
 */
class TenantAggregateRootSerializationTest {

  static class Sample extends TenantAggregateRoot<Long> {}

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void tenantId_is_excluded_but_id_remains() throws Exception {
    Sample sample = new Sample();
    sample.setId(1L);
    sample.setTenantId(42L);

    String json = mapper.writeValueAsString(sample);

    assertThat(json).doesNotContain("tenantId");
    assertThat(json).contains("id");
  }
}
