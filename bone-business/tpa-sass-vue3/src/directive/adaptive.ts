import type { DirectiveBinding } from "vue";

declare global {
  interface HTMLElement {
    resizeListener?: () => void;
  }
}

const doResize = (el: HTMLElement, binding: DirectiveBinding) => {
  // 如果传入的值为false，则不应用自适应高度
  if (binding.value === false) return;

  // 获取底部需要保留的高度，默认为62
  const bottomOffset = binding.value || 55;

  // 获取元素的父容器
  const parent = el.parentElement;
  if (!parent) return;

  // 获取元素距离顶部的偏移量
  const elementTop = el.getBoundingClientRect().top;

  // app-container的上下内边距为20
  const appPadding = 20;

  // 计算可用高度 = 视窗高度 - 元素顶部偏移 - 底部保留高度 - app-container的内边距
  const availableHeight =
    window.innerHeight - elementTop - bottomOffset - appPadding;

  // 设置表格容器高度，减去1px以防止出现滚动条
  el.style.height = `${availableHeight - 1}px`;

  // 找到.el-table__body-wrapper元素并设置其高度
  const bodyWrapper = el.querySelector(".el-table__body-wrapper");
  if (bodyWrapper) {
    (bodyWrapper as HTMLElement).style.height = `${availableHeight - 41}px`; // 减去表头高度和1px边距
  }
};

export default {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    el.resizeListener = () => {
      doResize(el, binding);
    };

    // 初始化时执行一次
    doResize(el, binding);

    // 监听窗口大小变化
    window.addEventListener("resize", el.resizeListener);
  },

  updated(el: HTMLElement, binding: DirectiveBinding) {
    doResize(el, binding);
  },

  unmounted(el: HTMLElement) {
    // 移除事件监听
    if (el.resizeListener) {
      window.removeEventListener("resize", el.resizeListener);
    }
  },
};
