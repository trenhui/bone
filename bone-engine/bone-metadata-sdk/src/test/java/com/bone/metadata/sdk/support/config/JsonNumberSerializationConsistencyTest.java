package com.bone.metadata.sdk.support.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.util.JsonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * 统一 JSON 数值序列化契约的一致性测试：Spring MVC(ObjectMapper) 与 JsonUtil 必须对同一数据产生一致的数值序列化行为。 两端 ObjectMapper
 * 是独立实例，若只改一端会导致不同入口行为不一致（评审核心关切）。
 */
class JsonNumberSerializationConsistencyTest {

  /** 用真实 customizer 构造 Spring 侧 ObjectMapper，避免复刻配置造成假绿。 */
  private ObjectMapper springObjectMapper() {
    Jackson2ObjectMapperBuilderCustomizer customizer =
        new MetadataAutoConfiguration(null, null).boneLongToStringCustomizer();
    Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
    customizer.customize(builder);
    return builder.build();
  }

  static class Numbers {
    public Long longValue = 9007199254740995L;
    public long primitiveLong = 12345678901234L;
    public BigInteger bigInteger = new BigInteger("9007199254740993");
    public Integer integer = 42;
    public BigDecimal decimal = new BigDecimal("123.456");
    public List<BigInteger> list = List.of(new BigInteger("9007199254740997"));
    public Map<String, BigInteger> map = Map.of("k", new BigInteger("9007199254740999"));
  }

  @Test
  void springAndJsonUtilAgreeOnNumberSerialization() throws Exception {
    ObjectMapper springMapper = springObjectMapper();
    Numbers numbers = new Numbers();

    // 用 tree model 核对，规避两端缩进/空格差异；isTextual/isNumber 断言保证类型契约而非仅字面值
    com.fasterxml.jackson.databind.JsonNode springNode =
        springMapper.readTree(springMapper.writeValueAsString(numbers));
    com.fasterxml.jackson.databind.JsonNode utilNode =
        springMapper.readTree(JsonUtil.toJson(numbers));

    for (com.fasterxml.jackson.databind.JsonNode node :
        new com.fasterxml.jackson.databind.JsonNode[] {springNode, utilNode}) {
      assertThat(node.get("longValue").isTextual()).isTrue();
      assertThat(node.get("longValue").asText()).isEqualTo("9007199254740995");
      assertThat(node.get("primitiveLong").isTextual()).isTrue();
      assertThat(node.get("bigInteger").asText()).isEqualTo("9007199254740993");
      assertThat(node.get("integer").isNumber()).isTrue();
      assertThat(node.get("integer").intValue()).isEqualTo(42);
      assertThat(node.get("decimal").isNumber()).isTrue();
      assertThat(node.get("decimal").decimalValue()).isEqualTo(new BigDecimal("123.456"));
      assertThat(node.get("list").get(0).isTextual()).isTrue();
      assertThat(node.get("map").get("k").asText()).isEqualTo("9007199254740999");
    }
  }

  /** 判别用例：若 customizer 未登记 BigInteger，Spring 侧会输出裸数字，本用例必须失败。 */
  @Test
  void springMapperSerializesBigIntegerAsString() throws Exception {
    String json = springObjectMapper().writeValueAsString(new BigInteger("9007199254740993"));
    assertThat(json).isEqualTo("\"9007199254740993\"");
  }
}
