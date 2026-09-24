package com.bone.file.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.file.application.port.out.FileStoragePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 文件服务控制器 */
@Tag(name = "文件服务", description = "文件上传/下载/删除接口")
@RestController
@RequestMapping(PlatformApiPaths.FILE_V1 + "/files")
@RequiredArgsConstructor
public class FileController {

  private final FileStoragePort fileStorageService;

  @Operation(summary = "上传文件")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<String> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "bucket", required = false) String bucket) {
    String objectName = UUID.randomUUID() + "-" + file.getOriginalFilename();
    try (InputStream in = file.getInputStream()) {
      var stored =
          fileStorageService.upload(bucket, objectName, in, file.getSize(), file.getContentType());
      return ApiResponse.success(stored.objectName());
    } catch (Exception e) {
      throw new IllegalStateException("文件上传失败: " + e.getMessage(), e);
    }
  }

  @Operation(summary = "下载文件")
  @GetMapping("/{objectName}")
  public void download(
      @PathVariable String objectName,
      @RequestParam(value = "bucket", required = false) String bucket,
      HttpServletResponse response) {
    try (InputStream in = fileStorageService.download(bucket, objectName);
        OutputStream out = response.getOutputStream()) {
      response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
      response.setHeader(
          HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + objectName + "\"");
      in.transferTo(out);
    } catch (Exception e) {
      throw new IllegalStateException("文件下载失败: " + e.getMessage(), e);
    }
  }

  @Operation(summary = "删除文件")
  @DeleteMapping("/{objectName}")
  public ApiResponse<Void> delete(
      @PathVariable String objectName,
      @RequestParam(value = "bucket", required = false) String bucket) {
    fileStorageService.delete(bucket, objectName);
    return ApiResponse.success();
  }

  @Operation(summary = "存储连接测试")
  @GetMapping("/test")
  public ApiResponse<Boolean> test() {
    return ApiResponse.success(fileStorageService.testConnection());
  }
}
