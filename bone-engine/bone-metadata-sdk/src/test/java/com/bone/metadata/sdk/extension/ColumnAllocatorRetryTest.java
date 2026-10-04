package com.bone.metadata.sdk.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.metadata.sdk.domain.enums.DataType;
import com.bone.metadata.sdk.domain.exception.FieldAllocationException;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.ColumnAllocation;
import com.bone.metadata.sdk.extension.repository.ColumnAllocationRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

/**
 * {@link ColumnAllocator} 冲突重试语义的回归测试。
 *
 * <p><b>为什么必须有这个测试</b>：原实现把待插入列表在重试循环<b>之外</b>构造好，重试时原样重复插入 同一批冲突索引——冲突后并未重读 {@code
 * MAX(column_index)}，因此必然连续冲突并在 MAX_RETRIES 次后 抛 {@link
 * FieldAllocationException}。这把「一次可自愈的并发冲突」放大成了「确定性失败」。修复后 每次尝试都重算 baseIndex 并重建插入列表，故本测试在修复前必然失败。
 */
class ColumnAllocatorRetryTest {

  private final ColumnAllocationRepository repo = mock(ColumnAllocationRepository.class);
  private final ColumnNamingStrategy naming = mock(ColumnNamingStrategy.class);

  private final ColumnAllocator allocator = new ColumnAllocator(repo, naming);

  private final AllocationContext ctx =
      AllocationContext.of(1001L, "bone-iam", "Account", "account");

  @Test
  @DisplayName("唯一键冲突后必须重算 baseIndex，用新索引重试而非重复插入同一批冲突索引")
  void retryRecomputesBaseIndex() {
    // 无回收列可用 → 走 createNewAllocations
    // 注意必须给可变列表：产品代码对返回值调用 addAll，List.of() 会抛 UnsupportedOperation
    when(repo.findRecycledColumnsWithLock(any(), any(), anyInt())).thenReturn(new ArrayList<>());
    // 第一次读到 MAX=0（baseIndex=1），冲突后应读到 MAX=1（baseIndex=2）
    when(repo.findCurrentMaxIndexWithLock(any(), any())).thenReturn(0).thenReturn(1);
    when(naming.generate(any(), anyInt())).thenReturn("ext_string_01", "ext_string_02");

    // 第一次插入抛唯一键冲突，第二次成功
    when(repo.batchInsert(any()))
        .thenThrow(new DuplicateKeyException("uk_alloc_tenant_app_biz_type_idx"))
        .thenReturn(new int[0]);

    List<String> names = allocator.allocate(ctx, DataType.STRING, 2);

    assertThat(names).hasSize(2);

    // 关键断言：两次 batchInsert 收到的 columnIndex 必须不同
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ColumnAllocation>> captor = ArgumentCaptor.forClass(List.class);
    verify(repo, times(2)).batchInsert(captor.capture());

    List<List<ColumnAllocation>> attempts = captor.getAllValues();
    List<Integer> firstIndexes =
        attempts.get(0).stream().map(ColumnAllocation::getColumnIndex).toList();
    List<Integer> secondIndexes =
        attempts.get(1).stream().map(ColumnAllocation::getColumnIndex).toList();

    assertThat(firstIndexes).containsExactly(1, 2);
    // 若重试沿用同一批索引，这里会仍是 (1, 2) —— 正是原缺陷
    assertThat(secondIndexes).containsExactly(2, 3);

    // MAX 也必须被重新读取过（每次尝试一次）
    verify(repo, times(2)).findCurrentMaxIndexWithLock(any(), any());
  }

  @Test
  @DisplayName("冲突次数耗尽后才抛 FieldAllocationException，且原异常作为 cause 保留")
  void throwsAfterExhaustingRetries() {
    when(repo.findRecycledColumnsWithLock(any(), any(), anyInt())).thenReturn(new ArrayList<>());
    when(repo.findCurrentMaxIndexWithLock(any(), any())).thenReturn(0);
    when(naming.generate(any(), anyInt())).thenReturn("ext_string_01");
    when(repo.batchInsert(any())).thenThrow(new DuplicateKeyException("always conflict"));

    assertThatCode(() -> allocator.allocate(ctx, DataType.STRING, 1))
        .isInstanceOf(FieldAllocationException.class)
        .hasMessageContaining("分配列重试失败")
        // cause 保留才能定位是真冲突还是 DB 故障
        .hasCauseInstanceOf(DuplicateKeyException.class);
  }
}
