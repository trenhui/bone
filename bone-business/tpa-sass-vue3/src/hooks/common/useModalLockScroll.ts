import { Ref, ref, watch, onMounted, onUnmounted } from "vue";

// 全局计数器，用于跟踪当前打开的modal数量
const globalOpenModalCount = ref(0);

/**
 * 监听modal的可见性状态，并相应地锁定或解锁body滚动
 * @param visible Ref<boolean> modal的可见性状态
 */
export const useModalLockScroll = (visible: Ref<boolean>) => {
  // 局部状态，用于跟踪当前实例是否已锁定滚动
  const isLocked = ref(false);

  /**
   * 锁定滚动
   * 只有在当前实例未锁定的情况下才执行锁定操作
   */
  const lock = () => {
    if (!isLocked.value) {
      globalOpenModalCount.value++;
      isLocked.value = true;
      updateBodyOverflow();
    }
  };

  /**
   * 解锁滚动
   * 只有在当前实例已锁定的情况下才执行解锁操作
   */
  const unlock = () => {
    if (isLocked.value) {
      globalOpenModalCount.value = Math.max(0, globalOpenModalCount.value - 1);
      isLocked.value = false;
      updateBodyOverflow();
    }
  };

  // 组件挂载时，如果visible为true，则锁定滚动
  onMounted(() => {
    if (visible.value) {
      lock();
    }
  });

  // 监听visible的变化，相应地锁定或解锁滚动
  watch(visible, (newVal) => {
    if (newVal) {
      lock();
    } else {
      unlock();
    }
  });

  // 组件卸载时，如果当前实例处于锁定状态，则解锁滚动
  onUnmounted(() => {
    if (isLocked.value) {
      unlock();
    }
  });
};

/**
 * 更新body的overflow样式
 * 根据全局计数器决定是否锁定滚动
 */
function updateBodyOverflow() {
  document.body.style.overflow =
    globalOpenModalCount.value > 0 ? "hidden" : "auto";
}
