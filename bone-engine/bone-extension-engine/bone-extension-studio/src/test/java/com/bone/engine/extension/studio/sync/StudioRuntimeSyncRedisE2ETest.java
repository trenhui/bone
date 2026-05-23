package com.bone.engine.extension.studio.sync;

import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import com.bone.engine.extension.api.model.sync.ExtensionRoutingMetadata;
import com.bone.engine.extension.core.router.DefaultExtensionPointRouter;
import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.infrastructure.persistence.InMemoryStudioExtPointRepository;
import com.bone.engine.extension.studio.infrastructure.persistence.InMemoryStudioExtensionRepository;
import com.bone.engine.extension.support.config.ExtensionMetadataRedisConfiguration;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.support.expression.SpELExpressionEvaluator;
import com.bone.engine.extension.support.repository.InMemoryExtensionRepository;
import com.bone.engine.extension.support.sync.MetadataOverlayExtensionRepository;
import com.bone.engine.extension.support.sync.RedisExtensionMetadataStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Studio {@link RuntimeExtensionSyncService} → Redis → {@link MetadataOverlayExtensionRepository} → 路由器 端到端。
 */
@Testcontainers(disabledWithoutDocker = true)
class StudioRuntimeSyncRedisE2ETest {

    private static final String EXT_POINT = SampleExtPoint.class.getName();
    private static final String REFRESH_CHANNEL = "bone:ext:metadata:test";

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private RedisExtensionMetadataStore metadataStore;
    private RuntimeExtensionSyncService syncService;
    private InMemoryExtensionRepository localRepo;
    private MetadataOverlayExtensionRepository overlayRepo;
    private DefaultExtensionPointRouter router;

    private final SampleExtPoint highPriority = () -> "HIGH";
    private final SampleExtPoint lowPriority = () -> "LOW";

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getFirstMappedPort());
        connectionFactory.afterPropertiesSet();

        ExtensionMetadataRedisConfiguration redisConfig = new ExtensionMetadataRedisConfiguration();
        ObjectMapper objectMapper = new ObjectMapper();
        RedisTemplate<String, ExtensionRoutingMetadata> valueTemplate =
                redisConfig.extensionMetadataRedisTemplate(connectionFactory, objectMapper);
        StringRedisTemplate indexTemplate =
                redisConfig.extensionMetadataIndexRedisTemplate(connectionFactory);
        metadataStore = new RedisExtensionMetadataStore(valueTemplate, indexTemplate, REFRESH_CHANNEL);

        InMemoryStudioExtPointRepository extPointRepository = new InMemoryStudioExtPointRepository();
        InMemoryStudioExtensionRepository extensionRepository = new InMemoryStudioExtensionRepository();
        syncService = new RuntimeExtensionSyncService(metadataStore, extPointRepository);

        ExtPoint point = new ExtPoint();
        point.setName("Redis E2E");
        point.setInterfaceName(EXT_POINT);
        point.setEnabled(true);
        extPointRepository.save(point);

        Extension extension =
                Extension.create(
                        point.getId(),
                        "HIGH_IMPL",
                        "disable via studio",
                        "com.bone.test.HighImpl");
        extension.setBizCode("BIZ");
        extension.setConfig("{\"code\":\"HIGH\",\"traffic\":100}");
        extensionRepository.save(extension);

        localRepo = new InMemoryExtensionRepository();
        localRepo.registerExtension(
                EXT_POINT,
                ExtensionDefinition.builder()
                        .code("HIGH")
                        .extensionPoint(EXT_POINT)
                        .instance(highPriority)
                        .tenant("default")
                        .bizCode("BIZ")
                        .priority(10)
                        .build());
        localRepo.registerExtension(
                EXT_POINT,
                ExtensionDefinition.builder()
                        .code("LOW")
                        .extensionPoint(EXT_POINT)
                        .instance(lowPriority)
                        .tenant("default")
                        .bizCode("BIZ")
                        .priority(200)
                        .build());

        SpELExpressionEvaluator spel = new SpELExpressionEvaluator();
        overlayRepo = new MetadataOverlayExtensionRepository(localRepo, metadataStore, spel);
        router =
                new DefaultExtensionPointRouter(
                        overlayRepo, spel, 1000, Duration.ofMinutes(5), true, null);

        assertTrue(syncService.publish(extension));
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void studioPublishToRedis_disablesHighPriorityInRouter() {
        ExtensionRoutingMetadata stored = metadataStore.get(EXT_POINT, "HIGH");
        assertNotNull(stored);
        assertFalse(stored.isEnabled());

        router.clearCache(SampleExtPoint.class);
        BizContext<String> ctx =
                BizContext.<String>builder().tenant("default").bizCode("BIZ").data("x").build();
        assertSame(lowPriority, router.route(SampleExtPoint.class, ctx));
    }

    @Test
    void redisIndexContainsPublishedCode() {
        assertTrue(metadataStore.getByExtensionPoint(EXT_POINT).containsKey("HIGH"));
    }

    @FunctionalInterface
    interface SampleExtPoint {
        String tag();
    }
}
