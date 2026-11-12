package com.bone.claim.infrastructure.schedule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.metadata.sdk.domain.exception.OptimisticLockingFailureException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 调度单写者服务（P1-2）负向探针：不依赖真实 DB，用 Mock 仓储断言「fail-closed 互斥」判别力。
 *
 * <p>本探针存在的意义——<b>自证旧实现会红</b>：P1-2 之前 5 个 {@code @Scheduled} Job 无单写者保护， 多副本会并发双跑对账/关单。若有人把 {@link
 * ScheduleLockService#acquire} 退化成「save 即成功、吞掉唯一键/ 版本冲突异常」，下列用例会立刻转红：
 *
 * <ul>
 *   <li>{@link #firstGrabConflictFailsClosed()}：并发首抢唯一键冲突必须 {@code return false}，而非抛异常或返回 true；
 *   <li>{@link #renewConflictFailsClosed()}：并发续约版本冲突必须 {@code return false}；
 *   <li>{@link #releaseOnlyReleasesOwnLock()}：绝不允许越权释放他副本持有的锁。
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleLockServiceTest {

  private static final String LOCK_NAME = "schedule:test";
  private static final String OWNER = "inst-A";

  @Mock private ScheduleLockRepository repository;

  private ScheduleLockService service;

  @BeforeEach
  void setUp() {
    // 固定租约 30min、固定实例标识，免去 @Value 与 UUID 不确定性。
    service = new ScheduleLockService(repository, 30L, OWNER);
  }

  // ── 首抢路径 ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("无存量锁、save 成功 ⇒ 首抢成功")
  void firstGrabSucceedsWhenNoExistingLock() {
    when(repository.findByCriteria(any())).thenReturn(Collections.emptyList());
    when(repository.save(any())).thenReturn(1L);

    assertTrue(service.acquire(LOCK_NAME, 30L, OWNER));
  }

  @Test
  @DisplayName("负向探针：并发首抢唯一键冲突 ⇒ fail-closed 返回 false（绝不双跑）")
  void firstGrabConflictFailsClosed() {
    when(repository.findByCriteria(any())).thenReturn(Collections.emptyList());
    doThrow(new DataIntegrityViolationException("duplicate lock_name"))
        .when(repository)
        .save(any());

    assertFalse(service.acquire(LOCK_NAME, 30L, OWNER));
    // 冲突后不得再尝试 save；也不得执行任务（由 execute 保证）。
    verify(repository, times(1)).save(any());
  }

  // ── 续约路径 ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("存量锁已过期、续约 save 成功 ⇒ 抢占成功")
  void renewSucceedsWhenExistingLockExpired() {
    // lockedUntil=1 恒早于 System.currentTimeMillis()，视为已过期可被抢占。
    ScheduleLock expired = ScheduleLock.create(LOCK_NAME, 1L, 1L, "inst-ghost");
    when(repository.findByCriteria(any())).thenReturn(List.of(expired));
    when(repository.save(any())).thenReturn(1L);

    assertTrue(service.acquire(LOCK_NAME, 30L, OWNER));
  }

  @Test
  @DisplayName("负向探针：并发续约版本冲突 ⇒ fail-closed 返回 false（绝不双跑）")
  void renewConflictFailsClosed() {
    ScheduleLock expired = ScheduleLock.create(LOCK_NAME, 1L, 1L, "inst-ghost");
    when(repository.findByCriteria(any())).thenReturn(List.of(expired));
    doThrow(new OptimisticLockingFailureException("ScheduleLock", LOCK_NAME, 0L))
        .when(repository)
        .save(any());

    assertFalse(service.acquire(LOCK_NAME, 30L, OWNER));
  }

  @Test
  @DisplayName("存量锁被他副本持有且未到期 ⇒ 本实例放弃")
  void heldByOtherAndNotExpiredFails() {
    // lockedUntil=Long.MAX_VALUE 恒晚于当前时间，视为他副本持锁中。
    ScheduleLock held = ScheduleLock.create(LOCK_NAME, Long.MAX_VALUE, 1L, "inst-B");
    when(repository.findByCriteria(any())).thenReturn(List.of(held));

    assertFalse(service.acquire(LOCK_NAME, 30L, OWNER));
    // 未到期 ⇒ 不应进入续约 save。
    verify(repository, never()).save(any());
  }

  // ── 释放边界 ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("负向探针：仅释放本实例持有的锁，越权释放他副本锁必须被拒绝")
  void releaseOnlyReleasesOwnLock() {
    // 场景一：owner 匹配 ⇒ 应 release + save。
    ScheduleLock mine = ScheduleLock.create(LOCK_NAME, Long.MAX_VALUE, 1L, OWNER);
    when(repository.findByCriteria(any())).thenReturn(List.of(mine));
    when(repository.save(any())).thenReturn(1L);

    service.release(LOCK_NAME, OWNER);
    verify(repository, times(1)).save(any());

    // 场景二：owner 不匹配 ⇒ 绝不动锁。
    ScheduleLock others = ScheduleLock.create(LOCK_NAME, Long.MAX_VALUE, 1L, "inst-C");
    when(repository.findByCriteria(any())).thenReturn(List.of(others));

    service.release(LOCK_NAME, OWNER);
    // 第二次 release 只读锁、未 save（越权保护生效）。
    verify(repository, times(1)).save(any());
  }

  // ── 端到端 execute ────────────────────────────────────────────────────

  @Test
  @DisplayName("抢到锁 ⇒ 执行 task 且 finally 释放")
  void executeRunsTaskAndReleases() {
    // 首抢读：无存量 ⇒ 插入（save #1）；释放读：返回本实例持有的锁 ⇒ 释放（save #2）。
    when(repository.findByCriteria(any()))
        .thenReturn(
            Collections.emptyList(),
            List.of(ScheduleLock.create(LOCK_NAME, Long.MAX_VALUE, 1L, OWNER)));
    when(repository.save(any())).thenReturn(1L);

    boolean[] ran = {false};
    boolean executed = service.execute(LOCK_NAME, () -> ran[0] = true);

    assertTrue(executed);
    assertTrue(ran[0]);
    // acquire 一次 save（首抢）+ release 一次 save（释放）⇒ 共 2 次。
    verify(repository, times(2)).save(any());
  }

  @Test
  @DisplayName("未抢到锁 ⇒ 跳过 task（多副本不双跑，核心验收点）")
  void executeSkipsTaskWhenNotAcquired() {
    // 他副本持锁中 ⇒ acquire 返回 false。
    ScheduleLock held = ScheduleLock.create(LOCK_NAME, Long.MAX_VALUE, 1L, "inst-B");
    when(repository.findByCriteria(any())).thenReturn(List.of(held));

    boolean[] ran = {false};
    boolean executed = service.execute(LOCK_NAME, () -> ran[0] = true);

    assertFalse(executed);
    assertFalse(ran[0]);
    verify(repository, never()).save(any());
  }
}
