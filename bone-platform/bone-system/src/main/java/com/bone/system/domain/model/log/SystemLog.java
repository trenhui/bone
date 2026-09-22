package com.bone.system.domain.model.log;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.system.domain.model.log.vo.LogLevel;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("sys_log")
public class SystemLog extends AggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private LogLevel level;
  private String service;
  private String content;
  private String traceId;
  private LocalDateTime createdAt;

  public static SystemLog create(
      Long id, LogLevel level, String service, String content, String traceId) {
    SystemLog log = new SystemLog();
    log.id = id;
    log.level = level;
    log.service = service;
    log.content = content;
    log.traceId = traceId;
    log.createdAt = LocalDateTime.now();
    return log;
  }
}
