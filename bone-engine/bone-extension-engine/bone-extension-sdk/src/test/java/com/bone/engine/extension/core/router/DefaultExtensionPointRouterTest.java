package com.bone.engine.extension.core.router;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.support.expression.SpELExpressionEvaluator;
import com.bone.engine.extension.support.repository.InMemoryExtensionRepository;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultExtensionPointRouterTest {

  private static final String EXT_POINT = SampleExtPoint.class.getName();

  private InMemoryExtensionRepository repository;
  private DefaultExtensionPointRouter router;

  private final SampleExtPoint outpatientImpl = () -> "OUTPATIENT";
  private final SampleExtPoint inpatientImpl = () -> "INPATIENT";
  private final SampleExtPoint broadImpl = () -> "BROAD";

  @BeforeEach
  void setUp() {
    repository = new InMemoryExtensionRepository();
    router =
        new DefaultExtensionPointRouter(
            repository, new SpELExpressionEvaluator(), 1000, Duration.ofMinutes(10), true, null);

    repository.registerExtension(
        EXT_POINT,
        ExtensionDefinition.builder()
            .code("OUTPATIENT")
            .extensionPoint(EXT_POINT)
            .instance(outpatientImpl)
            .tenant("default")
            .bizCode("MEDICAL")
            .condition("#data.type == 'OUTPATIENT'")
            .priority(100)
            .build());

    repository.registerExtension(
        EXT_POINT,
        ExtensionDefinition.builder()
            .code("INPATIENT")
            .extensionPoint(EXT_POINT)
            .instance(inpatientImpl)
            .tenant("default")
            .bizCode("MEDICAL")
            .scenario("INPATIENT")
            .condition("#data.type == 'INPATIENT'")
            .priority(110)
            .build());

    repository.registerExtension(
        EXT_POINT,
        ExtensionDefinition.builder()
            .code("BROAD")
            .extensionPoint(EXT_POINT)
            .instance(broadImpl)
            .tenant("default")
            .bizCode("MEDICAL")
            .priority(200)
            .build());
  }

  @Test
  void routesInpatientBySpelNotBroadDimensionMatch() {
    BizContext<ClaimPayload> ctx =
        BizContext.<ClaimPayload>builder()
            .tenant("default")
            .bizCode("MEDICAL")
            .data(new ClaimPayload("INPATIENT"))
            .build();

    Object routed = router.route(SampleExtPoint.class, ctx);
    assertSame(inpatientImpl, routed);
  }

  @Test
  void routesOutpatientBySpel() {
    BizContext<ClaimPayload> ctx =
        BizContext.<ClaimPayload>builder()
            .tenant("default")
            .bizCode("MEDICAL")
            .data(new ClaimPayload("OUTPATIENT"))
            .build();

    Object routed = router.route(SampleExtPoint.class, ctx);
    assertSame(outpatientImpl, routed);
  }

  @Test
  void routeResultCacheRespectsDifferentDimensionKeys() {
    InMemoryExtensionRepository cacheRepo = new InMemoryExtensionRepository();
    cacheRepo.registerExtension(
        EXT_POINT,
        ExtensionDefinition.builder()
            .code("INPATIENT")
            .extensionPoint(EXT_POINT)
            .instance(inpatientImpl)
            .tenant("default")
            .bizCode("MEDICAL")
            .build());
    cacheRepo.registerExtension(
        EXT_POINT,
        ExtensionDefinition.builder()
            .code("OUTPATIENT")
            .extensionPoint(EXT_POINT)
            .instance(outpatientImpl)
            .tenant("default")
            .bizCode("RETAIL")
            .build());

    DefaultExtensionPointRouter cachingRouter =
        new DefaultExtensionPointRouter(
            cacheRepo,
            new SpELExpressionEvaluator(),
            1000,
            Duration.ofMinutes(10),
            true,
            new com.bone.engine.extension.core.cache.CacheManager(cacheProperties(), null));

    BizContext<ClaimPayload> medical =
        BizContext.<ClaimPayload>builder()
            .tenant("default")
            .bizCode("MEDICAL")
            .data(new ClaimPayload("ANY"))
            .build();
    BizContext<ClaimPayload> retail =
        BizContext.<ClaimPayload>builder()
            .tenant("default")
            .bizCode("RETAIL")
            .data(new ClaimPayload("ANY"))
            .build();

    assertSame(inpatientImpl, cachingRouter.route(SampleExtPoint.class, medical));
    assertSame(outpatientImpl, cachingRouter.route(SampleExtPoint.class, retail));
  }

  @Test
  void fallsBackToBroadDimensionWhenNoSpelMatch() {
    BizContext<ClaimPayload> ctx =
        BizContext.<ClaimPayload>builder()
            .tenant("default")
            .bizCode("MEDICAL")
            .data(new ClaimPayload("UNKNOWN"))
            .build();

    assertSame(broadImpl, router.route(SampleExtPoint.class, ctx));
  }

  @Test
  void throwsWhenNoExtensionMatches() {
    InMemoryExtensionRepository strictRepo = new InMemoryExtensionRepository();
    strictRepo.registerExtension(
        EXT_POINT,
        ExtensionDefinition.builder()
            .code("INPATIENT")
            .extensionPoint(EXT_POINT)
            .instance(inpatientImpl)
            .tenant("isolated")
            .bizCode("ONLY")
            .condition("#data.type == 'INPATIENT'")
            .build());

    DefaultExtensionPointRouter isolatedRouter =
        new DefaultExtensionPointRouter(
            strictRepo, new SpELExpressionEvaluator(), 1000, Duration.ofMinutes(10), true, null);

    BizContext<ClaimPayload> ctx =
        BizContext.<ClaimPayload>builder()
            .tenant("default")
            .bizCode("MEDICAL")
            .data(new ClaimPayload("UNKNOWN"))
            .build();

    assertThrows(
        DefaultExtensionPointRouter.RouterException.class,
        () -> isolatedRouter.route(SampleExtPoint.class, ctx));
  }

  private static com.bone.engine.extension.support.config.ExtensionProperties cacheProperties() {
    com.bone.engine.extension.support.config.ExtensionProperties properties =
        new com.bone.engine.extension.support.config.ExtensionProperties();
    properties.getCache().setEnabled(true);
    properties.getCache().setMaxSize(500);
    properties.getCache().setExpireAfterWrite(Duration.ofMinutes(5).toMillis());
    properties.getRouter().setCacheEnabled(true);
    return properties;
  }

  @FunctionalInterface
  interface SampleExtPoint {
    String tag();
  }

  static final class ClaimPayload {
    private final String type;

    ClaimPayload(String type) {
      this.type = type;
    }

    public String getType() {
      return type;
    }
  }
}
