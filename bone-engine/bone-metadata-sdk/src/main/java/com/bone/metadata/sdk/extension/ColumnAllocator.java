package com.bone.metadata.sdk.extension;

import com.bone.metadata.sdk.domain.enums.AllocationColumnStatus;
import com.bone.metadata.sdk.domain.enums.DataType;
import com.bone.metadata.sdk.domain.exception.FieldAllocationException;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.ColumnAllocation;
import com.bone.metadata.sdk.extension.repository.ColumnAllocationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** ColumnAllocator 负责在并发环境下进行列的预留/分配操作，直接使用 DB 行锁方案获取索引。 */
@Slf4j
public class ColumnAllocator {

  private final ColumnAllocationRepository repo;
  private final ColumnNamingStrategy namingStrategy;
  private static final int MAX_RETRIES = 3;

  public ColumnAllocator(ColumnAllocationRepository repo, ColumnNamingStrategy namingStrategy) {
    this.repo = repo;
    this.namingStrategy = namingStrategy;
  }

  /**
   * 分配 count 个列（事务新开启，确保隔离性，需要上层服务自行开启事务）
   *
   * @param ctx 分配上下文
   * @param type 数据类型
   * @param count 需要分配的个数
   * @return 已分配的列名列表
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<String> allocate(AllocationContext ctx, DataType type, int count) {
    // 1. 优先从数据库读取 RECYCLED 状态的回收列
    List<ColumnAllocation> recycled = repo.findRecycledColumnsWithLock(ctx, type, count);
    int need = count - recycled.size();
    if (need > 0) {
      // 如果回收列不够，则创建新的列
      recycled.addAll(createNewAllocations(ctx, type, need));
    }
    // 最终返回所有已分配的列名
    return recycled.stream().map(ColumnAllocation::getColumnName).collect(Collectors.toList());
  }

  /** 实际创建 count 个新的列记录（仅使用 DB 行锁方案） */
  private List<ColumnAllocation> createNewAllocations(
      AllocationContext ctx, DataType type, int count) {
    // 通过数据库行锁方式获取连续索引
    long baseIndex = allocateFromDB(ctx, type, count);

    // 构建待插入的 ColumnAllocation 实例列表
    List<ColumnAllocation> toInsert =
        IntStream.range(0, count)
            .mapToObj(i -> buildAllocation(ctx, type, (int) (baseIndex + i)))
            .collect(Collectors.toList());

    // 使用重试机制批量插入，处理可能的唯一键冲突
    runWithRetry(() -> repo.batchInsert(toInsert), MAX_RETRIES);
    return toInsert;
  }

  /** 通过 DB 行锁方案：先在事务中 SELECT FOR UPDATE MAX(column_index)，然后 +1 */
  private long allocateFromDB(AllocationContext ctx, DataType type, int count) {
    int currentMax = repo.findCurrentMaxIndexWithLock(ctx, type);
    return currentMax + 1L;
  }

  /** 构建一个新的 ColumnAllocation 对象（尚未写入数据库） */
  private ColumnAllocation buildAllocation(AllocationContext ctx, DataType type, int idx) {
    validateLimit(type, idx);
    String name = namingStrategy.generate(type, idx);
    Long userId = -1000l; // todo
    // UserContext.getCurrentUser()!=null?UserContext.getCurrentUser().getId():-10000;
    return ColumnAllocation.builder()
        .tenantId(ctx.getTenantId())
        .appCode(ctx.getAppCode())
        .bizIdentityCode(ctx.getBizIdentityCode())
        .entityType(ctx.getEntityType())
        .dataType(type)
        // 将状态设为 AVAILABLE 枚举
        .status(AllocationColumnStatus.AVAILABLE)
        .columnName(name)
        .columnIndex(idx)
        .version(0)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .createdBy(userId) // 可根据实际场景设置 createdBy
        .updatedBy(userId)
        .build();
  }

  /** 对唯一键冲突做重试，并统计冲突次数 */
  private void runWithRetry(Runnable action, int maxAttempts) {
    int attempts = 0;
    Random rnd = new Random();
    while (true) {
      try {
        action.run();
        return;
      } catch (DuplicateKeyException ex) {
        if (++attempts > maxAttempts) {
          throw new FieldAllocationException("分配列重试失败，次数：" + maxAttempts, ex);
        }
        // 指数退避 + 随机抖动
        long backoff = (50L << attempts) + rnd.nextInt(50);
        try {
          TimeUnit.MILLISECONDS.sleep(backoff);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new FieldAllocationException("分配重试被中断", ie);
        }
      }
    }
  }

  /** 各种数据类型对应的最大索引限制 */
  private void validateLimit(DataType type, int idx) {
    int limit =
        switch (type) {
          case STRING -> 20;
          case TEXT, BOOLEAN -> 5;
          case JSON -> 3;
          case NUMBER, DATE, INTEGER -> 10;
          case XML, GEO -> 1;
        };
    if (idx > limit) {
      throw new FieldAllocationException("超出最大列数限制: " + type);
    }
  }
}
