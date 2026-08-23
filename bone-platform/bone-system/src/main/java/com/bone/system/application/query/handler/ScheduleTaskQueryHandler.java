package com.bone.system.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.system.application.query.dto.ScheduleTaskDTO;
import com.bone.system.application.query.qry.ScheduleTaskPageQuery;
import com.bone.system.domain.schedule.ScheduleTask;
import com.bone.system.domain.schedule.vo.TaskStatus;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleTaskQueryHandler {
  private final com.bone.system.domain.repository.ScheduleTaskRepository scheduleTaskRepository;

  public ScheduleTaskDTO getById(Long id) {
    ScheduleTask task = scheduleTaskRepository.findById(id);
    return task != null ? toDTO(task) : null;
  }

  public PageResult<ScheduleTaskDTO> page(ScheduleTaskPageQuery qry) {
    FluentQuery<ScheduleTask> query = QueryBuilder.from(ScheduleTask.class);
    if (qry.getKeyword() != null && !qry.getKeyword().isBlank()) {
      query.where(ScheduleTask::getName).like(qry.getKeyword());
    }
    if (qry.getStatus() != null && !qry.getStatus().isBlank()) {
      query.where(ScheduleTask::getStatus).eq(TaskStatus.fromString(qry.getStatus()));
    }
    com.bone.core.model.PageResult<ScheduleTask> result =
        query.page(qry.getPageNum(), qry.getPageSize());
    List<ScheduleTaskDTO> dtoList =
        result.getRecords().stream().map(this::toDTO).collect(Collectors.toList());
    return PageResult.of(dtoList, result.getTotal(), result.getPage(), result.getSize());
  }

  private ScheduleTaskDTO toDTO(ScheduleTask task) {
    return ScheduleTaskDTO.builder()
        .id(task.getId())
        .name(task.getName())
        .cron(task.getCron())
        .handler(task.getHandler())
        .status(task.getStatus().name())
        .lastRunAt(task.getLastRunAt())
        .nextRunAt(task.getNextRunAt())
        .createdAt(task.getCreatedAt())
        .updatedAt(task.getUpdatedAt())
        .build();
  }
}
