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

  /** 退避抖动源：多实例同时冲突时随机化等待，避免同相位重试再次撞车。 */
  private static final Random RANDOM = new Random();

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
    // 唯一键冲突时必须**重算 baseIndex** 再重试：MAX(column_index) 的行锁在聚合查询上可能不生效
    // （优化器走覆盖索引时 InnoDB 不加行锁），且首次分配（表内尚无行）恰是竞争最激烈、
    // 行锁最可能失效的场景。此时两个实例会读到同一个 MAX，二者都尝试插入同一批索引，
    // 靠唯一键兜底——若重试沿用同一份 toInsert，必然连续冲突 MAX_RETRIES 次后抛
    // FieldAllocationException，把「一次可自愈的并发冲突」放大成「确定性失败」。
    // 故每次尝试都重新读 MAX 并重建待插入列表。
    DuplicateKeyException lastConflict = null;
    for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
      long baseIndex = allocateFromDB(ctx, type, count);
      List<ColumnAllocation> toInsert =
          IntStream.range(0, count)
              .mapToObj(i -> buildAllocation(ctx, type, (int) (baseIndex + i)))
              .collect(Collectors.toList());
      try {
        repo.batchInsert(toInsert);
        return toInsert;
      } catch (DuplicateKeyException conflict) {
        lastConflict = conflict;
        if (attempt == MAX_RETRIES) {
          break;
        }
        // 指数退避 + 随机抖动，让出窗口给并发方完成插入后再重算索引
        long backoff = (50L << (attempt + 1)) + RANDOM.nextInt(50);
        log.warn("列分配遇到唯一键冲突，第 {} 次重试（重算 baseIndex 后）：{}", attempt + 1, conflict.getMessage());
        try {
          TimeUnit.MILLISECONDS.sleep(backoff);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new FieldAllocationException("分配重试被中断", ie);
        }
      }
    }
    throw new FieldAllocationException("分配列重试失败，次数：" + MAX_RETRIES, lastConflict);
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
