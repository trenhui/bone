package com.bone.engine.extension.repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ExtPointRepositoryFactory
 *
 * @author renhui.trh 2023-10-30
 */
public class ExtPointRepositoryFactory {
    private static Map<String, ExtPointRepository> extensionRepo = new ConcurrentHashMap<>();

    public static ExtPointRepository createExtPointRepository(Class<?> extProviderRepository) {
        return extensionRepo.computeIfAbsent(extProviderRepository.getSimpleName(),
                key -> {
                    ExtPointRepository extPointRepo = null;
                    switch (key) {
                        case "MemExtPointRepository":
                            extPointRepo = new MemExtPointRepository();
                            break;
                        case "RedisExtPointRepository":
                            extPointRepo = new RedisExtPointRepository();
                            break;
                        case "NacosExtPointRepository":
                            extPointRepo = new NacosExtPointRepository();
                            break;
                    }
                    return extPointRepo;
                }
        );
    }
}
