//package com.bone.lowcode.integration.transformation;
//
//import com.bone.lowcode.integration.config.InterfaceConfig;
//import com.yomahub.liteflow.annotation.LiteflowComponent;
//import com.yomahub.liteflow.core.NodeComponent;
//import com.github.benmanes.caffeine.cache.Cache;
//import com.github.benmanes.caffeine.cache.Caffeine;
//import io.atlasmap.api.AtlasContext;
//import io.atlasmap.api.AtlasContextFactory;
//import io.atlasmap.api.AtlasSession;
//import io.atlasmap.core.DefaultAtlasContextFactory;
//import io.atlasmap.java.v2.JavaField;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.net.URL;
//import java.util.concurrent.TimeUnit;
//
//@LiteflowComponent("parseInputData")
//public class ParseInputDataComponent extends NodeComponent {
//
//    private static final Logger logger = LoggerFactory.getLogger(ParseInputDataComponent.class);
//    private static final String DEFAULT_ADM_PATH = "partner/atlasmap/cpl/cpl-claimRequest-input.adm";
//
//    private final AtlasContextFactory atlasContextFactory;
//    private final Cache<String, AtlasContext> contextCache;
//
//    // 构造函数：初始化工厂和缓存
//    public ParseInputDataComponent() {
//        this.atlasContextFactory = initAtlasContextFactory();
//        this.contextCache = initContextCache();
//    }
//
//    // 初始化 AtlasContextFactory，捕获初始化异常
//    private AtlasContextFactory initAtlasContextFactory() {
//        try {
//            return DefaultAtlasContextFactory.getInstance();
//        } catch (Exception e) {
//            logger.error("Failed to initialize AtlasContextFactory", e);
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
//                        logger.info("Context for ADM {} removed due to {}", key, cause))
//                .build();
//    }
//
//    @Override
//    public void process() throws Exception {
//        InterfaceConfig interfaceConfig = this.getContextBean(InterfaceConfig.class);
//        Object inputData = this.getSlot().getRequestData();
//
//        AtlasContext atlasContext = contextCache.get(interfaceConfig.getInputAdmFilePath(), admResourcePath -> {
//            try {
//                URL url= Thread.currentThread().getContextClassLoader().getResource(admResourcePath);
//                assert url != null;
//                return atlasContextFactory.createContext(url.toURI());
//            } catch (Exception e) {
//                logger.error("Loading ADM resource: {}", admResourcePath, e);
//                throw new RuntimeException(e);
//            }
//        });
//
//        AtlasSession atlasSession = atlasContext.createSession();
//        switch (interfaceConfig.getInputFormat()) {
//            case JSON -> {
//                logger.info("Processing input as JSON...");
//                atlasSession.setSourceDocument("JSON", inputData);
//            }
//            case XML -> {
//                logger.info("Processing input as XML...");
//                atlasSession.setSourceDocument("XML", inputData);
//            }
//            case JAVA -> {
//                logger.info("Processing input as Java object...");
//                JavaField javaField = new JavaField();
//                javaField.setValue(inputData);
//                atlasSession.setSourceDocument("JAVA", javaField);
//            }
//            default ->
//                    throw new IllegalArgumentException("Unsupported input format: " + interfaceConfig.getInputFormat());
//        }
//
//        atlasContext.process(atlasSession);
//        Object parsedOutput = atlasSession.getDefaultTargetDocument();
//        this.getSlot().setOutput("parsedInput", parsedOutput);
//        logger.info("Successfully processed input data and set output.");
//    }
//}
