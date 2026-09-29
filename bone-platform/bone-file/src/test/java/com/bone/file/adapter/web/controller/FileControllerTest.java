package com.bone.file.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.file.application.port.out.FileStoragePort;
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

/** 控制器加固行为：服务端键生成、租户归属断言、带码异常（FL-1/FL-2/FL-3/FL-5）。 */
@ExtendWith(MockitoExtension.class)
class FileControllerTest {

  @Mock private FileStoragePort fileStoragePort;
  @Mock private TenantProvider tenantProvider;
  @Mock private MultipartFile multipartFile;

  private FileController controller;

  @BeforeEach
  void setUp() {
    controller = new FileController(fileStoragePort, tenantProvider);
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
}
