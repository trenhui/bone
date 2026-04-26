package com.bone.tpa.push.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class JsonValueUtils {

    private static final Logger log = LoggerFactory.getLogger(JsonValueUtils.class);

    /**
     * 从 JSON 字符串中获取 Map<String, BigDecimal> 的第一个非空 value。
     * 如果 JSON 无效或为空，则返回 BigDecimal.ZERO。
     *
     * @param jsonString JSON 格式字符串，例如 {"A":10,"B":20}
     * @return 第一个 value 或 BigDecimal.ZERO
     */
    public static BigDecimal getFirstValue(String jsonString) {
        if (StringUtils.isBlank(jsonString) || "{}".equals(jsonString.trim())) {
            return BigDecimal.ZERO;
        }

        try {
            // 使用 LinkedHashMap 保证顺序
            Map<String, BigDecimal> map = JSON.parseObject(
                    jsonString,
                    new TypeReference<LinkedHashMap<String, BigDecimal>>() {}
            );

            if (map != null && !map.isEmpty()) {
                return map.values().stream()
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(BigDecimal.ZERO);
            }
        } catch (Exception e) {
            log.warn("Failed to parse JSON to Map<String, BigDecimal>: {}", jsonString, e);
        }

        return BigDecimal.ZERO;
    }
}
