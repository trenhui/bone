import type { Directive, DirectiveBinding } from "vue";

/**
 * 下拉框加载更多
 */
const loadMore: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const child = el.querySelector(".el-select__input");
    const id = child?.getAttribute("aria-controls");
    const popper = document.getElementById(id ?? "");
    const selectDownDom = popper?.parentElement;

    if (selectDownDom) {
      selectDownDom.addEventListener("scroll", function (this: HTMLElement) {
        /**
         * scrollHeight 获取元素内容高度(只读)
         * scrollTop 获取或者设置元素的偏移值,
         *  常用于:计算滚动条的位置, 当一个元素的容器没有产生垂直方向的滚动条, 那它的scrollTop的值默认为0.
         * clientHeight 读取元素的可见高度(只读)
         * 如果元素滚动到底, 下面等式返回true, 没有则返回false:
         * ele.scrollHeight - ele.scrollTop === ele.clientHeight;
         */
        const scrollBottom = this.scrollHeight - this.scrollTop;
        const threshold = this.clientHeight + 1; // 添加1px容差

        if (scrollBottom <= threshold) {
          binding.value();
        }
      });
    }
  },
};

export default loadMore;
