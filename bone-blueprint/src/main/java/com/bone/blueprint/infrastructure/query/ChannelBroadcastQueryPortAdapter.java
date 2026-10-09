package com.bone.blueprint.infrastructure.query;

import com.bone.blueprint.application.query.port.ChannelBroadcastQueryPort;
import com.bone.blueprint.infrastructure.messaging.outbox.BroadcastTaskStatus;
import com.bone.blueprint.infrastructure.messaging.outbox.ChannelBroadcastTask;
import com.bone.blueprint.infrastructure.messaging.outbox.ChannelBroadcastTaskRepository;
import com.bone.core.model.PageResult;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link ChannelBroadcastQueryPort} 的基础设施实现：把技术持久化模型翻译成运营可读的视图。
 *
 * <p>翻译而非直接暴露实体：{@code ChannelBroadcastTask} 是投递状态机（含 {@code mergedIntoId} 这类中继内部字段），
 * 直接给前端会让接口契约随中继实现漂移。
 */
@Component
@RequiredArgsConstructor
public class ChannelBroadcastQueryPortAdapter implements ChannelBroadcastQueryPort {

  private final ChannelBroadcastTaskRepository broadcastTaskRepository;

  @Override
  public PageResult<TaskView> pageByStatus(Long tenantId, String status, int page, int size) {
    BroadcastTaskStatus parsed = parseStatus(status);
    PageResult<ChannelBroadcastTask> result =
        parsed == null
            ? broadcastTaskRepository.findPageByTenant(tenantId, page, size)
            : broadcastTaskRepository.findPageByStatus(tenantId, parsed, page, size);
    List<TaskView> views = toViews(result.getRecords());
    return PageResult.of(views, result.getTotal(), result.getPage(), result.getSize());
  }

  @Override
  public TaskView findById(Long tenantId, String id) {
    if (id == null || id.isBlank()) {
      return null;
    }
    try {
      ChannelBroadcastTask task = broadcastTaskRepository.findById(Long.valueOf(id.trim()));
      // 租户校验放在读取后：SDK 的 findById 走租户过滤也不够（跨租户 id 猜测），
      // 显式比对 tenantId 让「拿别的租户任务 ID 试重试」这种探测直接失败。
      if (task == null || task.getTenantId() == null || !task.getTenantId().equals(tenantId)) {
        return null;
      }
      return toView(task);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static BroadcastTaskStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return BroadcastTaskStatus.valueOf(status.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      // 非法状态值不能当成「不过滤」——那会让运营输入错一个字母就看到全量数据，误判为筛选失效。
      throw new IllegalArgumentException("不支持的广播任务状态: " + status);
    }
  }

  private static List<TaskView> toViews(List<ChannelBroadcastTask> rows) {
    List<TaskView> views = new ArrayList<>();
    if (rows == null) {
      return views;
    }
    for (ChannelBroadcastTask row : rows) {
      views.add(toView(row));
    }
    return views;
  }

  private static TaskView toView(ChannelBroadcastTask task) {
    return new TaskView(
        String.valueOf(task.getId()),
        task.getProductId() == null ? null : String.valueOf(task.getProductId()),
        task.getChannelCode(),
        task.getChannelProductId(),
        task.getProductName(),
        task.getTargetStock(),
        task.getStatus() == null ? null : task.getStatus().name(),
        task.getRetryCount(),
        task.getNextRetryAt(),
        task.getLastError(),
        task.getMergedIntoId(),
        task.getCreatedAt());
  }
}
