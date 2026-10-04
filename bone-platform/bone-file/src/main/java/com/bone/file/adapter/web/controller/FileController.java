package com.bone.file.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.file.application.port.out.FileStoragePort;
import com.bone.file.common.BucketPolicy;
import com.bone.file.common.FileErrorCodes;
import com.bone.file.common.FileErrors;
import com.bone.file.common.FileObjectKeyGenerator;
import com.bone.file.domain.gateway.TenantProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务控制器（FL-1/FL-2/FL-3/FL-5 加固，FL-6 桶白名单）。
 *
 * <p><b>租户归属（FL-1）</b>：上传键由服务端经 {@link FileObjectKeyGenerator#generate(Long, String)} 生成，带租户前缀；
 * 下载/删除经 {@link FileObjectKeyGenerator#assertTenantScope(String, Long)} 校验键名前缀是否归属当前租户， 不符即 403，在无
 * {@code file_object} 表（FL-4，L3 待审批）的情况下也堵住跨租户越权。
 *
 * <p><b>桶归属（FL-6）</b>：键前缀校验只管住对象键，管不住「桶」这个正交寻址维度，故三个端点都额外经 {@link
 * BucketPolicy#assertBucketAllowed(String, String)} 校验：{@code bucket} 入参非空且不等于配置桶即 403。
 * 否则客户端可用自己租户前缀的键名指定任意桶名，把「键前缀隔离」这一个维度绕开。
 *
 * <p><b>失败关闭（FL-2）</b>：无租户上下文即拒绝写入/读取，对象不落到无主空间。
 *
 * <p><b>授权</b>：上传/删除挂 {@code file:objects:write}。该码已默认授予所有活跃角色 ——
 * 头像、附件、导入模板等功能普遍依赖上传端点，若缺失会导致这些功能对所有人 403。 下载不挂码：读端点仅需认证，且键的租户前缀已强制校验。
 *
 * <p><b>响应头注入防护（FL-3）</b>：下载的 {@code Content-Disposition} 走 {@link
 * FileObjectKeyGenerator#contentDisposition(String)}（RFC 5987 filename*），剥离引号与控制字符，杜绝换行注入新头。
 */
@Tag(name = "文件服务", description = "文件上传/下载/删除接口")
@RestController
@RequestMapping(PlatformApiPaths.FILE_V1 + "/files")
public class FileController {

  private final FileStoragePort fileStoragePort;
  private final TenantProvider tenantProvider;

  /** 本平台实际使用的存储桶；{@code bucket} 入参非空时必须等于它（FL-6 桶白名单）。 */
  private final String configuredBucket;

  public FileController(
      FileStoragePort fileStoragePort,
      TenantProvider tenantProvider,
      @Value("${bone.file.minio.bucket:platform-files}") String configuredBucket) {
    this.fileStoragePort = fileStoragePort;
    this.tenantProvider = tenantProvider;
    this.configuredBucket = configuredBucket;
  }

  @PreAuthorize("hasAuthority('file:objects:write')")
  @Operation(summary = "上传文件")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<FileObjectResp> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "bucket", required = false) String bucket) {
    Long tenantId = tenantProvider.currentTenantIdOrNull();
    if (tenantId == null) {
      throw FileErrors.of(FileErrorCodes.TENANT_CONTEXT_MISSING);
    }
    BucketPolicy.assertBucketAllowed(bucket, configuredBucket);
    String originalName = file.getOriginalFilename();
    // 服务端生成带租户前缀的键；扩展名白名单在校验器内（不在白名单即 TYPE_NOT_ALLOWED）。
    String objectName = FileObjectKeyGenerator.generate(tenantId, originalName);
    try (InputStream in = file.getInputStream()) {
      var stored =
          fileStoragePort.upload(bucket, objectName, in, file.getSize(), file.getContentType());
      return ApiResponse.success(
          new FileObjectResp(
              stored.objectName(),
              originalName,
              stored.bucket(),
              file.getContentType(),
              stored.size(),
              tenantId));
    } catch (IOException e) {
      throw FileErrors.of(FileErrorCodes.UPLOAD_FAILED, originalName, e);
    }
  }

  @Operation(summary = "下载文件")
  @GetMapping("/{objectName}")
  public void download(
      @PathVariable String objectName,
      @RequestParam(value = "bucket", required = false) String bucket,
      HttpServletResponse response) {
    Long tenantId = tenantProvider.currentTenantIdOrNull();
    // 无租户上下文 / 跨租户对象名 → 失败关闭（TENANT_CONTEXT_MISSING / ACCESS_DENIED）。
    FileObjectKeyGenerator.assertTenantScope(objectName, tenantId);
    BucketPolicy.assertBucketAllowed(bucket, configuredBucket);
    try (InputStream in = fileStoragePort.download(bucket, objectName);
        OutputStream out = response.getOutputStream()) {
      response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
      response.setHeader(
          HttpHeaders.CONTENT_DISPOSITION, FileObjectKeyGenerator.contentDisposition(objectName));
      in.transferTo(out);
    } catch (IOException e) {
      throw FileErrors.of(FileErrorCodes.DOWNLOAD_FAILED, objectName, e);
    }
  }

  @PreAuthorize("hasAuthority('file:objects:write')")
  @Operation(summary = "删除文件")
  @DeleteMapping("/{objectName}")
  public ApiResponse<Void> delete(
      @PathVariable String objectName,
      @RequestParam(value = "bucket", required = false) String bucket) {
    Long tenantId = tenantProvider.currentTenantIdOrNull();
    FileObjectKeyGenerator.assertTenantScope(objectName, tenantId);
    BucketPolicy.assertBucketAllowed(bucket, configuredBucket);
    fileStoragePort.delete(bucket, objectName);
    return ApiResponse.success();
  }

  @Operation(summary = "存储连接测试")
  @GetMapping("/test")
  public ApiResponse<Boolean> test() {
    return ApiResponse.success(fileStoragePort.testConnection());
  }
}
