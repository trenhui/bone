package com.bone.file.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.file.application.port.out.FileStoragePort;
import com.bone.file.common.FileErrorCodes;
import com.bone.file.common.FileErrors;
import com.bone.file.common.FileObjectKeyGenerator;
import com.bone.file.domain.gateway.TenantProvider;
import com.bone.file.domain.model.FileObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务控制器（FL-1 / FL-2 / FL-3 / FL-5 加固）。
 *
 * <p><b>寻址与归属</b>：下载/删除的对象名先过 {@link FileObjectKeyGenerator#assertTenantScope(String, Long)}——
 * 键必须带当前租户前缀，否则 403。{@code file_object} 表（FL-4，L3 待审批）落地前，键前缀是唯一可校验的归属载体。
 *
 * <p><b>键生成</b>：服务端生成（{@code {租户}-{日期}-{uuid}.{ext}}），客户端原始文件名只取扩展名， 不再直接拼进对象键——杜绝路径遍历与对象键注入（FL-3）。
 */
@Slf4j
@Tag(name = "文件服务", description = "文件上传/下载/删除接口")
@RestController
@RequestMapping(PlatformApiPaths.FILE_V1 + "/files")
@RequiredArgsConstructor
public class FileController {

  private final FileStoragePort fileStorageService;
  private final TenantProvider tenantProvider;

  @Operation(summary = "上传文件")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<FileObjectResp> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "bucket", required = false) String bucket) {
    final Long tenantId = tenantProvider.currentTenantIdOrNull();
    // 租户缺失时 generate 内部失败关闭（FILE_TENANT_CONTEXT_MISSING），不会把对象写到无主空间。
    String objectName = FileObjectKeyGenerator.generate(tenantId, file.getOriginalFilename());
    try (InputStream in = file.getInputStream()) {
      var stored =
          fileStorageService.upload(bucket, objectName, in, file.getSize(), file.getContentType());
      FileObject fileObject =
          FileObject.of(
              UUID.randomUUID().toString(),
              tenantId,
              stored.bucket(),
              stored.objectName(),
              file.getOriginalFilename(),
              file.getContentType(),
              file.getSize());
      log.info("文件上传成功: {}", fileObject.summarize());
      return ApiResponse.success(FileObjectResp.of(fileObject));
    } catch (java.io.IOException e) {
      throw FileErrors.of(FileErrorCodes.UPLOAD_FAILED, objectName, e);
    }
  }

  @Operation(summary = "下载文件")
  @GetMapping("/{objectName}")
  public void download(
      @PathVariable String objectName,
      @RequestParam(value = "bucket", required = false) String bucket,
      HttpServletResponse response) {
    // FL-1：先验归属再取流；FL-3：校验键形，响应头走 RFC 5987 编码。
    FileObjectKeyGenerator.assertTenantScope(objectName, tenantProvider.currentTenantIdOrNull());
    try (InputStream in = fileStorageService.download(bucket, objectName);
        OutputStream out = response.getOutputStream()) {
      response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
      response.setHeader(
          HttpHeaders.CONTENT_DISPOSITION, FileObjectKeyGenerator.contentDisposition(objectName));
      in.transferTo(out);
    } catch (java.io.IOException e) {
      throw FileErrors.of(FileErrorCodes.DOWNLOAD_FAILED, objectName, e);
    }
  }

  @Operation(summary = "删除文件")
  @DeleteMapping("/{objectName}")
  public ApiResponse<Void> delete(
      @PathVariable String objectName,
      @RequestParam(value = "bucket", required = false) String bucket) {
    FileObjectKeyGenerator.assertTenantScope(objectName, tenantProvider.currentTenantIdOrNull());
    try {
      fileStorageService.delete(bucket, objectName);
      return ApiResponse.success();
    } catch (RuntimeException e) {
      throw FileErrors.of(FileErrorCodes.DELETE_FAILED, objectName, e);
    }
  }

  @Operation(summary = "存储连接测试")
  @GetMapping("/test")
  public ApiResponse<Boolean> test() {
    return ApiResponse.success(fileStorageService.testConnection());
  }
}
