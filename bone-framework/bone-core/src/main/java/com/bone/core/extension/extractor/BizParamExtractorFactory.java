package com.bone.core.extension.extractor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BizParamExtractorFactory {
    private static final Map<Class<?>, BizParamExtractor<?>> extractors = new ConcurrentHashMap<>();


    @SuppressWarnings("unchecked")
    public static <T> BizParamExtractor<T> getExtractor(T data) {
        if (data == null) return null;

        BizParamExtractor<?> extractor = extractors.get(data.getClass());
        if (extractor == null) {
            // 尝试查找父类或接口的提取器
            for (Class<?> type : extractors.keySet()) {
                if (type.isInstance(data)) {
                    extractor = extractors.get(type);
                    break;
                }
            }
        }
        return (BizParamExtractor<T>) extractor;
    }
}