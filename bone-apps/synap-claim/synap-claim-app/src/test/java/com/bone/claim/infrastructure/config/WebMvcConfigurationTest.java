package com.bone.claim.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/**
 * C1（P0）验证：{@link WebMvcConfiguration#extendMessageConverters} 必须把 {@code Long} 主键序列化为字符串， 防 JS
 * 安全整数 {@code 2^53} 截断导致的 ID 损坏。直接驱动生产代码路径（非假绿）。
 */
class WebMvcConfigurationTest {

  record Sample(Long id, String name) {}

  private ObjectMapper underTest() {
    MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
    List<HttpMessageConverter<?>> converters = new ArrayList<>();
    converters.add(converter);
    new WebMvcConfiguration().extendMessageConverters(converters);
    return converter.getObjectMapper();
  }

  @Test
  void longIdSerializedAsString() throws Exception {
    String json = underTest().writeValueAsString(new Sample(123456789012345678L, "x"));
    assertTrue(json.contains("\"id\":\"123456789012345678\""), "Long id 应为字符串: " + json);
    assertFalse(json.contains("\"id\":123456789012345678"), "Long id 不得为 number: " + json);
  }

  @Test
  void longAboveSafeIntegerStillString() throws Exception {
    // 9007199254740993 = 2^53 + 1，JS number 会就近舍入；必须保持字符串
    String json = underTest().writeValueAsString(new Sample(9007199254740993L, "y"));
    assertTrue(json.contains("\"id\":\"9007199254740993\""), "超过 2^53 的 Long 必须为字符串: " + json);
  }
}
