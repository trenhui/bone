//package com.bone.lowcode.integration.transformation.output;
//
//import com.bone.lowcode.integration.config.InterfaceConfig;
//import com.bone.lowcode.integration.transformation.Converter;
//import com.github.benmanes.caffeine.cache.Cache;
//import com.github.benmanes.caffeine.cache.Caffeine;
//import io.atlasmap.api.AtlasContext;
//import io.atlasmap.api.AtlasContextFactory;
//import io.atlasmap.api.AtlasSession;
//import io.atlasmap.core.DefaultAtlasContextFactory;
//import io.atlasmap.java.v2.JavaField;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//
//import java.net.URL;
//import java.util.concurrent.TimeUnit;
//
//@Component
//@Slf4j
//public class OutputConverter implements Converter {
//
//    private final AtlasContextFactory atlasContextFactory;
//    private final Cache<String, AtlasContext> contextCache;
//
//    // 构造函数：初始化工厂和缓存
//    public OutputConverter() {
//        this.atlasContextFactory = initAtlasContextFactory();
//        this.contextCache = initContextCache();
//    }
//
//    // 初始化 AtlasContextFactory，捕获初始化异常
//    private AtlasContextFactory initAtlasContextFactory() {
//        try {
//            return DefaultAtlasContextFactory.getInstance();
//        } catch (Exception e) {
//            log.error("Failed to initialize AtlasContextFactory", e);
//            throw new RuntimeException("AtlasContextFactory 初始化失败", e);
//        }
//    }
//
//    // 初始化 Caffeine 缓存
//    private Cache<String, AtlasContext> initContextCache() {
//        return Caffeine.newBuilder()
//                .maximumSize(100)
//                .expireAfterAccess(30, TimeUnit.MINUTES)
//                .removalListener((key, value, cause) ->
//                        log.info("Context for ADM {} removed due to {}", key, cause))
//                .build();
//    }
//
//    @Override
//    public Object parse(InterfaceConfig interfaceConfig, Object inputData) throws Exception {
//
//        AtlasContext atlasContext = contextCache.get(interfaceConfig.getOutputAdmFilePath(), admResourcePath -> {
//            try {
//                URL url = Thread.currentThread().getContextClassLoader().getResource(admResourcePath);
//                assert url != null;
//                return atlasContextFactory.createContext(url.toURI());
//            } catch (Exception e) {
//                log.error("Loading ADM resource: {}", admResourcePath, e);
//                throw new RuntimeException(e);
//            }
//        });
//
//        AtlasSession atlasSession = atlasContext.createSession();
//        switch (interfaceConfig.getInputFormat()) {
//            case JSON -> {
//                log.info("Processing input as JSON...");
//                atlasSession.setSourceDocument("JSON", inputData);
//            }
//            case XML -> {
//                log.info("Processing input as XML...");
//                atlasSession.setSourceDocument("XML", inputData);
//            }
//            case JAVA -> {
//                log.info("Processing input as Java object...");
//                JavaField javaField = new JavaField();
//                javaField.setValue(inputData);
//                atlasSession.setSourceDocument("JAVA", javaField);
//            }
//            default ->
//                    throw new IllegalArgumentException("Unsupported input format: " + interfaceConfig.getInputFormat());
//        }
//
//        atlasContext.process(atlasSession);
//        return atlasSession.getDefaultTargetDocument();
//    }
//}
