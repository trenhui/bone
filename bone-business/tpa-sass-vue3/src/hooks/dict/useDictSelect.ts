import { ref, computed, watch, unref, type Ref, type ComputedRef } from "vue";
import { useDictStore } from "@/store/modules/dict";
import { DictItem } from "@/api/pk-dict";

type MaybeRef<T> = T | Ref<T> | ComputedRef<T>;

export default function useDictSelect(
  sourceType: MaybeRef<number | undefined>,
  sourceCode: MaybeRef<string | undefined>,
  options?: {
    pageSize?: number;
    isCascader?: boolean;
    maxLevel?: number;
  }
) {
  const dictStore = useDictStore();
  const dictOptions = ref<DictItem[]>([]);

  // 监听数据源参数变化，自动重新加载数据
  watch(
    [() => unref(sourceType), () => unref(sourceCode)],
    ([newType, newCode], [oldType, oldCode]) => {
      // 只要参数发生了变化就重新加载，即使新值是 null 也要处理
      if (newType !== oldType || newCode !== oldCode) {
        // 只有当新的参数都有值时才加载数据
        if (newType && newCode) {
          loadDictData();
        } else {
          // 如果参数不完整，清空选项
          dictOptions.value = [];
        }
      }
    }
  );

  const loadDictData = async (parentCode?: string) => {
    const currentSourceType = unref(sourceType);
    const currentSourceCode = unref(sourceCode);

    if (currentSourceType && (parentCode || currentSourceCode)) {
      const code = parentCode || currentSourceCode;
      if (code) {
        dictOptions.value = await dictStore.getDict(currentSourceType, code);
      }
    }
  };

  const searchDictData = async (name: string) => {
    const currentSourceType = unref(sourceType);
    const currentSourceCode = unref(sourceCode);

    if (currentSourceType && currentSourceCode) {
      dictOptions.value = await dictStore.getDictWithSearch(
        currentSourceType,
        currentSourceCode,
        name
      );
    }
  };

  const loadMoreDictData = async () => {
    const currentSourceType = unref(sourceType);
    const currentSourceCode = unref(sourceCode);

    if (currentSourceType && currentSourceCode) {
      const { items, hasMore } = await dictStore.loadMore(
        currentSourceType,
        currentSourceCode
      );
      dictOptions.value = items;
      return hasMore;
    }
    return false;
  };

  const loadSelectedDictData = async (value: string | string[]) => {
    // 检查一下是否已经存在
    const values = Array.isArray(value) ? value : [value];
    const existingCodes = dictOptions.value.map((item) => item.code);
    const needLoadValues = values.filter((v) => !existingCodes.includes(v));

    // 如果所有值都已存在，则不需要重新加载
    if (needLoadValues.length === 0) {
      return;
    }

    const currentSourceType = unref(sourceType);
    const currentSourceCode = unref(sourceCode);

    if (currentSourceType && currentSourceCode && needLoadValues.length > 0) {
      await dictStore.loadSelectedDict(
        currentSourceType,
        currentSourceCode,
        needLoadValues.length === 1 ? needLoadValues[0] : needLoadValues
      );
      await loadDictData();
    }
  };

  // 级联选择器专用的加载方法
  const loadCascaderData = async (
    node: any,
    resolve: (data: any[]) => void
  ) => {
    const currentSourceType = unref(sourceType);
    const currentSourceCode = unref(sourceCode);

    if (!currentSourceType || !currentSourceCode) {
      resolve([]);
      return;
    }

    const { level, data } = node;
    const parentCode = level === 0 ? currentSourceCode : data.code;

    await loadDictData(parentCode);

    if (!dictOptions.value || dictOptions.value.length === 0) {
      resolve([]);
      return;
    }

    resolve(
      dictOptions.value.map((item) => ({
        ...item,
        leaf: level >= (options?.maxLevel || 1),
      }))
    );
  };

  return {
    dictOptions,
    loadDictData,
    searchDictData,
    loadMoreDictData,
    loadSelectedDictData,
    loadCascaderData,
  };
}
