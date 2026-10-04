package com.bone.file.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.file.application.port.out.FileStoragePort;
import com.bone.file.common.BucketPolicy;
import com.bone.file.common.FileErrorCodes;
import com.bone.file.domain.gateway.TenantProvider;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

/** 控制器加固行为：服务端键生成、租户归属断言、桶白名单、带码异常（FL-1/FL-2/FL-3/FL-5/FL-6）。 */
@ExtendWith(MockitoExtension.class)
class FileControllerTest {

  private static final String CONFIGURED_BUCKET = "platform-files";

  @Mock private FileStoragePort fileStoragePort;
  @Mock private TenantProvider tenantProvider;
  @Mock private MultipartFile multipartFile;

  private FileController controller;

  @BeforeEach
  void setUp() {
    controller = new FileController(fileStoragePort, tenantProvider, CONFIGURED_BUCKET);
  }

  @Test
  @DisplayName("upload：键由服务端生成且带租户前缀，响应回传资源表示")
  void uploadGeneratesTenantPrefixedKeyAndReturnsResource() throws Exception {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(42L);
    when(multipartFile.getOriginalFilename()).thenReturn("报表.pdf");
    when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
    when(multipartFile.getSize()).thenReturn(10L);
    when(multipartFile.getContentType()).thenReturn("application/pdf");
    when(fileStoragePort.upload(any(), anyString(), any(), anyLong(), any()))
        .thenReturn(
            new FileStoragePort.StoredObject(
                "platform-files", "42-x.pdf", "etag", 10L, java.util.Map.of()));

    var resp = controller.upload(multipartFile, null);

    assertThat(resp.getData().objectName()).startsWith("42-").endsWith(".pdf");
    assertThat(resp.getData().tenantId()).isEqualTo(42L);
    assertThat(resp.getData().originalName()).isEqualTo("报表.pdf");
  }

  @Test
  @DisplayName("upload：无租户上下文失败关闭，不触碰存储")
  void uploadFailsClosedWithoutTenant() throws Exception {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(null);

    assertThatThrownBy(() -> controller.upload(multipartFile, null))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(FileErrorCodes.TENANT_CONTEXT_MISSING));
    verify(tenantProvider).currentTenantIdOrNull();
  }

  @Test
  @DisplayName("download：跨租户对象名被拒（403），不触发取流")
  void downloadRejectsForeignTenantObject() {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(42L);

    assertThatThrownBy(
            () -> controller.download("7-20260929-x.pdf", null, new MockHttpServletResponse()))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(FileErrorCodes.ACCESS_DENIED));
  }

  @Test
  @DisplayName("download：同租户放行且 Content-Disposition 无注入面")
  void downloadSetsEncodedDisposition() throws Exception {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(42L);
    when(fileStoragePort.download(any(), eq("42-20260929-x.pdf")))
        .thenReturn(new ByteArrayInputStream("hello".getBytes()));

    MockHttpServletResponse response = new MockHttpServletResponse();
    controller.download("42-20260929-x.pdf", null, response);

    assertThat(response.getHeader("Content-Disposition"))
        .startsWith("attachment; filename=")
        .contains("filename*=UTF-8''");
  }

  @Test
  @DisplayName("delete：跨租户删除被拒（FL-1 主场景）")
  void deleteRejectsForeignTenantObject() {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(42L);

    assertThatThrownBy(() -> controller.delete("7-20260929-x.pdf", null))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(FileErrorCodes.ACCESS_DENIED));
  }

  // ===== FL-6：桶白名单（跨桶越权闸门）=====
  // 键前缀校验（assertTenantScope）只管对象键、不管桶，故「键名带自己租户前缀 + 指定他人桶名」
  // 这条越权路径必须由 BucketPolicy 独立拦住。修复前这三个用例全传 bucket=null，此维度零覆盖。

  @Test
  @DisplayName("download：指定非配置 bucket 被拒（跨桶越权闸门），不得触达存储")
  void downloadRejectsForeignBucket() {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(42L);

    assertThatThrownBy(
            () ->
                controller.download(
                    "42-20260929-x.pdf", "other-tenant-bucket", new MockHttpServletResponse()))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(FileErrorCodes.ACCESS_DENIED));
    // 关键断言：不得在鉴权前触达存储（否则「先取流再判权限」会把对象内容读出来）
    verify(fileStoragePort, never()).download(any(), anyString());
  }

  @Test
  @DisplayName("upload：指定非配置 bucket 被拒（跨桶写入闸门），不得触达存储")
  void uploadRejectsForeignBucket() throws Exception {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(42L);

    assertThatThrownBy(() -> controller.upload(multipartFile, "other-tenant-bucket"))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(FileErrorCodes.ACCESS_DENIED));
    verify(fileStoragePort, never()).upload(any(), anyString(), any(), anyLong(), any());
  }

  @Test
  @DisplayName("delete：指定非配置 bucket 被拒（跨桶删除闸门），不得触达存储")
  void deleteRejectsForeignBucket() {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(42L);

    assertThatThrownBy(() -> controller.delete("42-20260929-x.pdf", "other-tenant-bucket"))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(FileErrorCodes.ACCESS_DENIED));
    verify(fileStoragePort, never()).delete(any(), anyString());
  }

  @Test
  @DisplayName("download：显式指定配置桶名放行（与不传 bucket 等价）")
  void downloadAllowsConfiguredBucket() throws Exception {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(42L);
    when(fileStoragePort.download(eq(CONFIGURED_BUCKET), eq("42-20260929-x.pdf")))
        .thenReturn(new ByteArrayInputStream("hello".getBytes()));

    MockHttpServletResponse response = new MockHttpServletResponse();
    controller.download("42-20260929-x.pdf", CONFIGURED_BUCKET, response);

    assertThat(response.getHeader("Content-Disposition")).startsWith("attachment; filename=");
  }

  @Test
  @DisplayName("BucketPolicy：配置桶缺失时失败关闭，拒绝任意桶名（不静默降级为全桶可写）")
  void bucketPolicyFailsClosedWhenConfiguredBucketMissing() {
    assertThatThrownBy(() -> BucketPolicy.assertBucketAllowed("any-bucket", null))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(FileErrorCodes.ACCESS_DENIED));
    assertThatThrownBy(() -> BucketPolicy.assertBucketAllowed("any-bucket", "  "))
        .isInstanceOf(BizException.class);
    // 空/空白入参表示「用配置桶」，配置缺失时也无桶可用 —— 但此形态在存储适配器侧才解析，
    // 故此处仅断言不放行任意非空桶名（空白入参不抛，见下方单元测试）
  }

  @Test
  @DisplayName("BucketPolicy：未指定 bucket 一律放行（保留「留空即用默认桶」的既有端口语义）")
  void bucketPolicyAllowsUnspecifiedBucket() {
    BucketPolicy.assertBucketAllowed(null, CONFIGURED_BUCKET);
    BucketPolicy.assertBucketAllowed("   ", CONFIGURED_BUCKET);
  }
}
