package com.bone.core.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** EntityIdSerializer 实体 ID 序列化测试：大整数转字符串避免 JS 精度丢失 */
class EntityIdSerializerTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
  }

  @Test
  void longId_serializedAsString() throws Exception {
    assertThat(objectMapper.writeValueAsString(new IdHolder(1234567890123456789L)))
        .isEqualTo("{\"id\":\"1234567890123456789\"}");
  }

  @Test
  void integerId_serializedAsNumber() throws Exception {
    assertThat(objectMapper.writeValueAsString(new IdHolder(42))).isEqualTo("{\"id\":42}");
  }

  @Test
  void bigIntegerId_serializedAsString() throws Exception {
    assertThat(
            objectMapper.writeValueAsString(new IdHolder(new BigInteger("999999999999999999999"))))
        .isEqualTo("{\"id\":\"999999999999999999999\"}");
  }

  @Test
  void bigDecimalId_serializedAsString() throws Exception {
    assertThat(objectMapper.writeValueAsString(new IdHolder(new BigDecimal("123.45"))))
        .isEqualTo("{\"id\":\"123.45\"}");
  }

  @Test
  void stringId_serializedAsString() throws Exception {
    assertThat(objectMapper.writeValueAsString(new IdHolder("user-001")))
        .isEqualTo("{\"id\":\"user-001\"}");
  }

  @Test
  void nullId_serializedAsNull() throws Exception {
    assertThat(objectMapper.writeValueAsString(new IdHolder(null))).isEqualTo("{\"id\":null}");
  }

  /** 持有任意类型 id 的测试载体：与 {@link Entity#getId()} 相同的注解方式 */
  static class IdHolder {
    @JsonSerialize(using = EntityIdSerializer.class)
    private final Object id;

    IdHolder(Object id) {
      this.id = id;
    }

    @SuppressWarnings("unused")
    public Object getId() {
      return id;
    }
  }
}
