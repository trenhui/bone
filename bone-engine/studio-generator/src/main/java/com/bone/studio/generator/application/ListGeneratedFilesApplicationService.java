package com.bone.studio.generator.application;

import com.bone.core.capability.Capability;
import com.bone.studio.generator.application.dto.GeneratedFileView;
import com.bone.studio.generator.domain.gateway.GenerationTaskReadPort;
import com.bone.studio.generator.domain.model.data.GenerationTask;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 查看生成任务的产物文件。
 *
 * <p><b>为何必须走读端口而不是仓储</b>：与 {@code CodeGenerationController#downloadCode} 同源—— {@code
 * generated_files} 是 JSON 列，SDK 反序列化后元素实际是 {@code LinkedHashMap}， 聚合上的 {@code List<GeneratedFile>}
 * 类型标注只是声明；此处沿用 download 的 Map 取值口径，避免二次踩坑。
 */
@Component
@RequiredArgsConstructor
@Capability(
    name = "listGeneratedFiles",
    description = "查看代码生成任务的产物文件清单/内容",
    inputSchema = "{}",
    outputSchema = "{}")
public class ListGeneratedFilesApplicationService {

  private final GenerationTaskReadPort generationTaskReadPort;

  /**
   * @param taskId 任务 ID
   * @param includeContent 是否带文件正文（清单模式仅回路径与大小）
   */
  public List<GeneratedFileView> handle(String taskId, boolean includeContent) {
    GenerationTask task = generationTaskReadPort.findByTaskId(taskId).orElse(null);
    if (task == null || task.getGeneratedFiles() == null || task.getGeneratedFiles().isEmpty()) {
      return List.of();
    }
    List<GeneratedFileView> views = new ArrayList<>(task.getGeneratedFiles().size());
    for (Object raw : task.getGeneratedFiles()) {
      if (!(raw instanceof java.util.Map<?, ?> file)) {
        continue;
      }
      String filePath = asString(file.get("filePath"));
      String fileName = asString(file.get("fileName"));
      String content = asString(file.get("content"));
      long size =
          content == null ? 0L : content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
      views.add(new GeneratedFileView(filePath, fileName, size, includeContent ? content : null));
    }
    return views;
  }

  private static String asString(Object value) {
    return value == null ? null : value.toString();
  }
}
