package com.bone.studio.generator.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.studio.generator.application.CodeGenerationAsyncApplicationService;
import com.bone.studio.generator.application.CreateCodeGenerationApplicationService;
import com.bone.studio.generator.application.GenerationTaskOperationApplicationService;
import com.bone.studio.generator.application.command.cmd.CreateCodeGenerationCommand;
import com.bone.studio.generator.application.dto.GeneratorOperationView;
import com.bone.studio.generator.common.GeneratorApiPaths;
import com.bone.studio.generator.config.GeneratorProperties;
import com.bone.studio.generator.domain.model.data.GenerationTask;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(GeneratorApiPaths.CODE_GENERATION)
@RequiredArgsConstructor
@Slf4j
public class CodeGenerationController {

  private final CreateCodeGenerationApplicationService createCodeGenerationHandler;
  private final CodeGenerationAsyncApplicationService codeGenerationAsyncApplicationService;
  private final GenerationTaskOperationApplicationService operationService;
  private final GeneratorProperties generatorProperties;

  @PostMapping
  public ResponseEntity<ApiResponse<?>> createCodeGeneration(
      @RequestBody CreateCodeGenerationCommand command,
      @RequestParam(required = false) Boolean sync) {
    if (resolveSync(sync)) {
      String taskId = createCodeGenerationHandler.handle(command);
      // taskId 为字符串数据，success(String) 会命中 message 重载，须用双参形式
      return ResponseEntity.ok(ApiResponse.success("创建成功", taskId));
    }
    String taskId = codeGenerationAsyncApplicationService.submit(command);
    Map<String, Object> accepted = new LinkedHashMap<>();
    accepted.put("operationId", taskId);
    accepted.put("taskId", taskId);
    String location = GeneratorApiPaths.V1_PREFIX + "/operations/" + taskId;
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .location(URI.create(location))
        .body(ApiResponse.success(accepted));
  }

  @GetMapping("/tasks/{taskId}/download")
  public ResponseEntity<ByteArrayResource> downloadCode(@PathVariable String taskId) {
    GenerationTask task = operationService.findByTaskId(taskId);
    List<?> files = task == null ? null : task.getGeneratedFiles();
    if (task == null || files == null || files.isEmpty()) {
      return ResponseEntity.noContent().build();
    }

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    Set<String> seen = new HashSet<>();
    try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
      // generated_files 是 JSON 列：SDK 把值对象 GeneratedFile 反序列化成 LinkedHashMap，
      // 无法直接转型，这里按 Map 取字段后打包。
      int index = 0;
      for (Object raw : files) {
        Map<?, ?> file = (Map<?, ?>) raw;
        String filePath = asString(file.get("filePath"));
        String fileName = asString(file.get("fileName"));
        String content = asString(file.get("content"));
        String entryName = filePath != null ? filePath : (fileName != null ? fileName : "file");
        // ZipEntry 不允许重名，出现冲突时追加序号兜底
        String uniqueName = entryName;
        if (!seen.add(uniqueName)) {
          uniqueName = entryName + "." + index;
        }
        zip.putNextEntry(new ZipEntry(uniqueName));
        zip.write(content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        index++;
      }
    } catch (Exception e) {
      log.error("[downloadCode] 打包生成产物失败 taskId={}", taskId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    String fileName =
        (task.getProjectName() == null ? "generated" : task.getProjectName()) + ".zip";
    ByteArrayResource resource = new ByteArrayResource(buffer.toByteArray());
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(resource.contentLength())
        .body(resource);
  }

  private static String asString(Object value) {
    return value == null ? null : value.toString();
  }

  @GetMapping("/tasks/{taskId}/status")
  public ApiResponse<String> getTaskStatus(@PathVariable String taskId) {
    GeneratorOperationView view = operationService.toOperationView(taskId);
    if (view == null) {
      // success(String) 会命中 message 重载，状态字符串须用双参形式放入 data
      return ApiResponse.success("查询成功", "UNKNOWN");
    }
    if (view.getResult() != null && view.getResult().get("status") != null) {
      return ApiResponse.success("查询成功", view.getResult().get("status").toString());
    }
    return ApiResponse.success("查询成功", view.isDone() ? "FAILED" : "PROCESSING");
  }

  private boolean resolveSync(Boolean syncParam) {
    if (!generatorProperties.getLro().isCodeGenerationEnabled()) {
      return true;
    }
    if (syncParam != null) {
      return syncParam;
    }
    return generatorProperties.getLro().isCodeGenerationSyncByDefault();
  }
}
