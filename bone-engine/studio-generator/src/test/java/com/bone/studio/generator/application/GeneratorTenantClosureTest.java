package com.bone.studio.generator.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.core.exception.DomainException;
import com.bone.studio.generator.application.command.cmd.CreateDataSourceCommand;
import com.bone.studio.generator.common.GeneratorErrorCodes;
import com.bone.studio.generator.common.GeneratorErrors;
import com.bone.studio.generator.domain.gateway.TenantProvider;
import com.bone.studio.generator.domain.model.data.DataSource;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import com.bone.studio.generator.infrastructure.gateway.CatalogMetadataGatewayAdapter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * 租户闭环（G-1）与错误码（G-3）的验收测试。
 *
 * <p><b>为什么用纯单测而不是 {@code @SpringBootTest}</b>：租户缺失在 HTTP 链路上无法复现——测试态 {@code
 * TestTenantContextConfiguration} 的过滤器会为无上下文的请求补上平台租户，生产则由 JWT Filter 写入。 要验证「缺失即失败关闭」只能在用例层把
 * {@link TenantProvider} 换成返回 {@code null} 的桩，这正是端口抽象带来的可测性。
 */
class GeneratorTenantClosureTest {

  private static CreateDataSourceCommand command() {
    return CreateDataSourceCommand.builder()
        .name("订单库")
        .type("mysql")
        .host("localhost")
        .port("3306")
        .database("bone")
        .username("root")
        .password("enc-pwd")
        .build();
  }

  /** 领域侧不再回落平台租户 0：显式传 null 即拒绝。 */
  @Test
  void dataSourceRejectsNullTenant() {
    assertThrows(
        DomainException.class,
        () -> DataSource.create(1L, null, "订单库", "mysql", "localhost", 3306, "bone", "root", "p"));
  }

  /**
   * 租户上下文缺失时创建数据源必须失败关闭，且带稳定业务码。
   *
   * <p>修复前此处硬编码 {@code 0L}，写操作静默落到平台租户，创建者随后读不到自己建的数据源。
   */
  @Test
  void createDataSourceFailsClosedWithoutTenantContext() {
    DataSourceRepository repository = Mockito.mock(DataSourceRepository.class);
    TenantProvider provider = Mockito.mock(TenantProvider.class);
    when(provider.currentTenantIdOrNull()).thenReturn(null);

    CreateDataSourceApplicationService service =
        new CreateDataSourceApplicationService(repository, provider);

    BizException ex = assertThrows(BizException.class, () -> service.handle(command()));

    assertEquals(GeneratorErrorCodes.TENANT_CONTEXT_MISSING, ex.getErrorCode());
    assertEquals(400, GeneratorErrors.httpStatusOf(GeneratorErrorCodes.TENANT_CONTEXT_MISSING));
    Mockito.verify(repository, Mockito.never()).insert(any());
  }

  /** 有租户时写入的必须是上下文租户，而不是任何魔法默认值。 */
  @Test
  void createDataSourceWritesContextTenant() {
    DataSourceRepository repository = Mockito.mock(DataSourceRepository.class);
    when(repository.insert(any())).thenReturn(1L);
    TenantProvider provider = Mockito.mock(TenantProvider.class);
    when(provider.currentTenantIdOrNull()).thenReturn(1001L);

    new CreateDataSourceApplicationService(repository, provider).handle(command());

    ArgumentCaptor<DataSource> captor = ArgumentCaptor.forClass(DataSource.class);
    verify(repository).insert(captor.capture());
    assertEquals(1001L, captor.getValue().getTenantId());
    assertNotEquals(0L, captor.getValue().getTenantId(), "不得再回落到平台租户 0");
  }

  /** 目录快照查询侧同样失败关闭（修复前回落 1L，恒按租户 1 过滤）。 */
  @Test
  void loadPublishedSnapshotsFailsClosedWithoutTenant() {
    TenantProvider provider = Mockito.mock(TenantProvider.class);
    when(provider.currentTenantIdOrNull()).thenReturn(null);

    CatalogMetadataGatewayAdapter adapter = new CatalogMetadataGatewayAdapter(null, null, provider);

    BizException ex =
        assertThrows(
            BizException.class, () -> adapter.loadPublishedSnapshots(null, List.of("ic_order")));
    assertEquals(GeneratorErrorCodes.TENANT_CONTEXT_MISSING, ex.getErrorCode());
    assertEquals(400, ex.getCode());
  }

  /**
   * 模板不存在的错误码必须是独立字段。
   *
   * <p>修复前写作 {@code new BizException(404, "GEN_TEMPLATE_NOT_FOUND: " + id)}（三参构造）， {@code
   * errorCode} 恒为 {@code null}，前端 {@code i18n.t('errors.' + errorCode)} 的分支永不命中。
   */
  @Test
  void templateNotFoundCarriesIndependentErrorCode() {
    BizException ex = GeneratorErrors.of(GeneratorErrorCodes.TEMPLATE_NOT_FOUND, 9001L);

    assertEquals(GeneratorErrorCodes.TEMPLATE_NOT_FOUND, ex.getErrorCode());
    assertEquals(404, ex.getCode());
    assertEquals(GeneratorErrorCodes.TEMPLATE_NOT_FOUND + ": 9001", ex.getMessage());
  }

  /** 码表不变量：漏登记的码在 httpStatusOf 处立即炸，不会被兜底成 400/500。 */
  @Test
  void unregisteredCodeFailsFast() {
    assertThrows(IllegalStateException.class, () -> GeneratorErrors.httpStatusOf("GEN_NOT_A_CODE"));
  }
}
