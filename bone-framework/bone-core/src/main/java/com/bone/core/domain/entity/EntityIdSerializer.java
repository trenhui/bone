package com.bone.core.domain.entity;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 基于业界最佳实践的自定义序列化器 - 数字类型序列化为数字（Long/BigInteger/BigDecimal 除外，序列化为字符串避免 JS 精度丢失） - 其他类型序列化为字符串 - 处理
 * null 值
 */
public class EntityIdSerializer extends JsonSerializer<Object> {

  @Override
  public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    if (value == null) {
      gen.writeNull();
      return;
    }

    // 检查是否为数字类型
    if (isNumberType(value)) {
      writeNumber(value, gen);
    } else {
      // 非数字类型序列化为字符串
      gen.writeString(value.toString());
    }
  }

  /** 检查值是否为数字类型 */
  private boolean isNumberType(Object value) {
    return value instanceof Number || value instanceof BigInteger || value instanceof BigDecimal;
  }

  /**
   * 根据具体数字类型进行序列化 Long/BigInteger/BigDecimal 序列化为字符串，避免 JavaScript Number 精度丢失 （雪花ID等大整数超过 JS
   * Number.MAX_SAFE_INTEGER 时末位会偏移）
   */
  private void writeNumber(Object value, JsonGenerator gen) throws IOException {
    if (value instanceof Long) {
      gen.writeString(value.toString());
    } else if (value instanceof Integer) {
      gen.writeNumber((Integer) value);
    } else if (value instanceof Short) {
      gen.writeNumber((Short) value);
    } else if (value instanceof Byte) {
      gen.writeNumber((Byte) value);
    } else if (value instanceof Float) {
      gen.writeNumber((Float) value);
    } else if (value instanceof Double) {
      gen.writeNumber((Double) value);
    } else if (value instanceof BigInteger) {
      gen.writeString(value.toString());
    } else if (value instanceof BigDecimal) {
      gen.writeString(value.toString());
    } else {
      // 对于其他数字类型，使用字符串表示以确保兼容性
      gen.writeString(value.toString());
    }
  }

  @Override
  public boolean isEmpty(SerializerProvider provider, Object value) {
    return value == null;
  }
}
