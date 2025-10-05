package com.bone.core.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class JsonUtil {
    private static final ObjectMapper objectMapper = JacksonUtils.getObjectMapper();

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (IOException e) {
            log.error("fromJson:" + json, e);
            return null;
        }
    }

    // 新增方法，支持通过 TypeReference<T> 进行反序列化
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        if (json == null) return null;

        try {
            return objectMapper.readValue(json, typeReference);
        } catch (IOException e) {
            log.error("fromJson:" + json, e);
            return null;
        }
    }

    public static String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (IOException e) {
            // 处理异常
            log.error("toJson:" + obj, e);
            return null;
        } catch (Exception e) {
            log.error("toJson failed. Object: {}", obj, e);
            return null;
        }
    }

    private static class JacksonUtils {
        private static final ObjectMapper objectMapper = new ObjectMapper();

        static {
            // 配置 ObjectMapper
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        }

         static ObjectMapper getObjectMapper() {
            return objectMapper;
        }
    }
}
