package com.bone.tpa.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 自定义 Long 类型序列化器（示例：超过 1e15 的数字转字符串，其他保持数字）
 */
public class BigLongSerializer extends JsonSerializer<Long> {

    private static final long THRESHOLD = 1_000_000_000_000_000L; // 1e15

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull(); // 处理 null 值
            return;
        }

        // 自定义逻辑：超过阈值转为字符串，否则保持数字
        if (value > THRESHOLD) {
            gen.writeString(value.toString());
        } else {
            gen.writeNumber(value);
        }
    }
}
