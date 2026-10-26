package com.bone.workflow.starter.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.workflow.ports.spi.WorkflowInstanceRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * P0-2 回归：状态投影意图执行器必须以<b>发件箱行 id</b>作为幂等键，而非 {@code {instanceId}:{intent}}。
 *
 * <p><b>为什么这是必须钉死的契约</b>：发件箱每行意图一个 id，同一行重试（claim 超时 / 退避重试）复用同一 id ⇒ 平台去重； 而<b>同一实例的不同意图</b>（如
 * suspend→resume→suspend 产生的两条 {@code SUSPENDED}）行 id 不同 ⇒ 不应被折叠。 若退回 {@code {instanceId}:{intent}}
 * 作键，重复出现的 {@code SUSPENDED}/{@code RESUMED} 会被平台判定为「同一操作」而丢弃， 第二次挂起的业务侧投影丢失——且单测极难复现（只有多轮挂起才会暴露）。
 *
 * <p>执行器只依赖窄端口 {@link RuntimeActionInvoker}（不耦合 {@code RuntimeActionApplicationService} 本身）， 因此本测试以
 * mock 端口捕获幂等键，干净验证契约，不受平台动作调用框架代理 / 非公有父类不可打桩的限制。
 */
class RuntimeActionIntentExecutorTest {

  private final RuntimeActionInvoker actionInvoker = mock(RuntimeActionInvoker.class);
  private final WorkflowInstanceRepository instanceRepository =
      mock(WorkflowInstanceRepository.class);

  private final RuntimeActionIntentExecutor executor =
      new RuntimeActionIntentExecutor(actionInvoker, instanceRepository);

  /** 一条终态意图（有业务动作码，会真正调用平台）。 */
  private static StatusIntent terminal(String id, String intent, String actionCode) {
    return new StatusIntent(
        id,
        "i1",
        "expense_reimbursement",
        "r1",
        1L,
        intent,
        actionCode,
        null,
        "NEW",
        0,
        Instant.now());
  }

  @Test
  @DisplayName("幂等键 = 发件箱行 id（每行唯一），透传给平台动作调用")
  void idempotencyKey_isOutboxRowId() {
    when(instanceRepository.findInstance("i1")).thenReturn(Optional.empty());

    executor.execute(terminal("1001", "APPROVED", "approve"));

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(actionInvoker)
        .invoke(
            eq("expense_reimbursement"),
            eq(1L),
            eq("r1"),
            eq("approve"),
            any(),
            any(),
            keyCaptor.capture(),
            any());
    assertThat(keyCaptor.getValue())
        .as("幂等键必须是发件箱行 id，而非 {instanceId}:{intent}——否则重复意图被错误折叠")
        .isEqualTo("1001");
  }

  @Test
  @DisplayName("同实例的两条不同意图（不同行 id）不被折叠——各自独立推进业务状态")
  void distinctRowsAreNotCollapsed() {
    when(instanceRepository.findInstance("i1")).thenReturn(Optional.empty());

    // 模拟 suspend→resume→suspend：两次 SUSPENDED 无动作码不调用平台；但即便换成有动作码的意图，
    // 不同行 id 也必须各自落地，不能被 {instanceId}:{intent} 折叠成一次。
    executor.execute(terminal("1001", "APPROVED", "approve"));
    executor.execute(terminal("1002", "APPROVED", "approve"));

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(actionInvoker, times(2))
        .invoke(any(), anyLong(), any(), any(), any(), any(), keyCaptor.capture(), any());
    assertThat(keyCaptor.getAllValues())
        .as("两条不同行 id 的意图必须分别推进，键不得相同（折叠 = 第二次投影丢失）")
        .containsExactly("1001", "1002");
  }

  @Test
  @DisplayName("缺租户上下文时拒绝推进业务状态（fail-closed）")
  void missingTenant_failsClosed() {
    StatusIntent noTenant =
        new StatusIntent(
            "2001",
            "i1",
            "expense_reimbursement",
            "r1",
            null,
            "APPROVED",
            "approve",
            null,
            "NEW",
            0,
            Instant.now());

    assertThatThrownBy(() -> executor.execute(noTenant))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("租户上下文");

    verify(actionInvoker, times(0))
        .invoke(any(), anyLong(), any(), any(), any(), any(), any(), any());
  }
}
