import { ref } from "vue";
import Sortable from "sortablejs";
import type { ElTable } from "element-plus";

export const useTableSortable = () => {
  const tableRef = ref<InstanceType<typeof ElTable> | null>(null);
  let sortableInstance: Sortable | null = null;

  const initSortable = (
    params: any,
    callback: (newIndex: number, oldIndex: number) => void
  ) => {
    if (!tableRef.value) {
      console.warn("tableRef is not defined");
      return;
    }

    const el = tableRef.value.$el.querySelector(
      ".el-table__body-wrapper tbody"
    );
    if (!el) {
      console.warn("table body not found");
      return;
    }

    sortableInstance = Sortable.create(el, {
      animation: 150,
      ghostClass: "sortable-ghost",
      ...(params || {}),
      onEnd(event) {
        const { newIndex, oldIndex } = event;
        if (newIndex === oldIndex) return;
        if (typeof newIndex === "number" && typeof oldIndex === "number") {
          callback?.(newIndex, oldIndex);
        }
      },
    });
  };

  const destroySortable = () => {
    if (sortableInstance) {
      sortableInstance.destroy();
      sortableInstance = null;
    }
  };

  return {
    tableRef,
    initSortable,
    destroySortable,
  };
};
