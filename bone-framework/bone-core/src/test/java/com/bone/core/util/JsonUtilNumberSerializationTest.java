package com.bone.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonUtilNumberSerializationTest {

  static class NumberContainer {
    private Long longValue;
    private long primitiveLongValue;
    private BigInteger bigIntegerValue;
    private Integer integerValue;
    private int primitiveIntValue;
    private BigDecimal bigDecimalValue;
    private List<BigInteger> bigIntegerList;
    private Map<String, BigInteger> bigIntegerMap;

    public NumberContainer() {}

    public NumberContainer(
        Long longValue,
        long primitiveLongValue,
        BigInteger bigIntegerValue,
        Integer integerValue,
        int primitiveIntValue,
        BigDecimal bigDecimalValue,
        List<BigInteger> bigIntegerList,
        Map<String, BigInteger> bigIntegerMap) {
      this.longValue = longValue;
      this.primitiveLongValue = primitiveLongValue;
      this.bigIntegerValue = bigIntegerValue;
      this.integerValue = integerValue;
      this.primitiveIntValue = primitiveIntValue;
      this.bigDecimalValue = bigDecimalValue;
      this.bigIntegerList = bigIntegerList;
      this.bigIntegerMap = bigIntegerMap;
    }

    public Long getLongValue() {
      return longValue;
    }

    public long getPrimitiveLongValue() {
      return primitiveLongValue;
    }

    public BigInteger getBigIntegerValue() {
      return bigIntegerValue;
    }

    public Integer getIntegerValue() {
      return integerValue;
    }

    public int getPrimitiveIntValue() {
      return primitiveIntValue;
    }

    public BigDecimal getBigDecimalValue() {
      return bigDecimalValue;
    }

    public List<BigInteger> getBigIntegerList() {
      return bigIntegerList;
    }

    public Map<String, BigInteger> getBigIntegerMap() {
      return bigIntegerMap;
    }
  }

  @Test
  void testNumberSerializationToString() {
    BigInteger largeBigInt = new BigInteger("9007199254740993"); // 2^53 + 1
    Long largeLong = 9007199254740995L;
    BigDecimal decimal = new BigDecimal("123.456");

    NumberContainer container =
        new NumberContainer(
            largeLong,
            12345678901234L,
            largeBigInt,
            42,
            100,
            decimal,
            List.of(largeBigInt),
            Map.of("key1", largeBigInt));

    String json = JsonUtil.toJson(container);
    assertThat(json).isNotNull();

    // Long, primitive long, BigInteger should be serialized as JSON strings
    assertThat(json).contains("\"longValue\" : \"9007199254740995\"");
    assertThat(json).contains("\"primitiveLongValue\" : \"12345678901234\"");
    assertThat(json).contains("\"bigIntegerValue\" : \"9007199254740993\"");
    assertThat(json).contains("\"key1\" : \"9007199254740993\"");

    // Integer and primitive int should remain as numbers
    assertThat(json).contains("\"integerValue\" : 42");
    assertThat(json).contains("\"primitiveIntValue\" : 100");

    // BigDecimal should remain as number
    assertThat(json).contains("\"bigDecimalValue\" : 123.456");

    // Test round-trip deserialization from JSON string
    NumberContainer deserialized = JsonUtil.fromJson(json, NumberContainer.class);
    assertThat(deserialized).isNotNull();
    assertThat(deserialized.getLongValue()).isEqualTo(largeLong);
    assertThat(deserialized.getPrimitiveLongValue()).isEqualTo(12345678901234L);
    assertThat(deserialized.getBigIntegerValue()).isEqualTo(largeBigInt);
    assertThat(deserialized.getIntegerValue()).isEqualTo(42);
    assertThat(deserialized.getPrimitiveIntValue()).isEqualTo(100);
    assertThat(deserialized.getBigDecimalValue()).isEqualTo(decimal);
  }

  /**
   * BigInteger 作为 Map Key 的序列化与 value 是两回事：value 走 serializerByType，key 走 key serializer。 本用例实证 key
   * 以字符串输出且超 2^53 精度不丢，反序列化能无损还原为 BigInteger Key（评审明确要求单独验证，不可假设被覆盖）。
   */
  @Test
  void testBigIntegerAsMapKeyKeepsPrecision() {
    BigInteger hugeKey = new BigInteger("9007199254740993");
    Map<BigInteger, String> map = Map.of(hugeKey, "v");

    String json = JsonUtil.toJson(map);
    assertThat(json).isNotNull();
    assertThat(json).contains("\"9007199254740993\"");

    Map<BigInteger, String> restored =
        JsonUtil.fromJson(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    assertThat(restored).containsKey(hugeKey);
    assertThat(restored.get(hugeKey)).isEqualTo("v");
  }

  /**
   * 兼容性回归：JsonUtil 本次改动把 Long 从数字改为字符串输出，但生产用途（ext_data_json 落库）中已有大量旧数据以 JSON number 格式存储。Jackson
   * 反序列化对 number 与 numeric-string 一视同仁，本用例证明历史数字格式旧数据仍可无损读回。
   */
  @Test
  void testDeserializeLegacyNumericLongStillWorks() {
    String legacyJson =
        "{\"longValue\":9007199254740995,\"primitiveLongValue\":12345678901234,"
            + "\"bigIntegerValue\":9007199254740993,\"integerValue\":42,"
            + "\"primitiveIntValue\":100,\"bigDecimalValue\":123.456}";

    NumberContainer restored = JsonUtil.fromJson(legacyJson, NumberContainer.class);
    assertThat(restored).isNotNull();
    assertThat(restored.getLongValue()).isEqualTo(9007199254740995L);
    assertThat(restored.getPrimitiveLongValue()).isEqualTo(12345678901234L);
    assertThat(restored.getBigIntegerValue()).isEqualTo(new BigInteger("9007199254740993"));
    assertThat(restored.getIntegerValue()).isEqualTo(42);
    assertThat(restored.getBigDecimalValue()).isEqualTo(new BigDecimal("123.456"));
  }
}
