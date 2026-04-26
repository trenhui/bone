import {
  getValueByJsonPath,
  setValueByJsonPath,
  isJsonPath,
} from "@/utils/jsonpathUtils";
import { inject, provide, computed, watch, reactive } from "vue";

interface ScopeData {
  [key: string]: any;
}

interface ScopeContext {
  data: ScopeData;
  parent?: ScopeContext;
}

const SCOPE_DATA_KEY = "SCOPE_DATA";

// 组件树数据共享 - 优化版本，避免内存膨胀
export function useScopeData(initialData: ScopeData = {}) {
  // 获取父级上下文
  const parentContext = inject<ScopeContext | undefined>(
    SCOPE_DATA_KEY,
    undefined
  );

  // 创建当前层级的响应式数据
  const currentData = { ...initialData };

  // 创建当前上下文，只存储当前层的数据
  const currentContext: ScopeContext = {
    data: currentData,
    parent: parentContext,
  };

  // 向下提供当前上下文
  provide(SCOPE_DATA_KEY, currentContext);

  // 链式查找数据的函数 - 优先级：当前层 > 父级层
  const findValue = (path: string): any => {
    const keys = path.split(".");

    // 从当前层开始向上查找
    let context: ScopeContext | undefined = currentContext;

    while (context) {
      let value = context.data;
      let found = true;

      // 在当前层级查找完整路径
      for (const key of keys) {
        if (value && typeof value === "object" && key in value) {
          value = value[key];
        } else {
          found = false;
          break;
        }
      }

      // 找到完整路径且值不为undefined时返回
      if (found && value !== undefined) {
        return value;
      }

      // 如果当前层没找到完整路径，继续向父级查找
      context = context.parent;
    }

    return undefined;
  };

  // JsonPath 查找函数 - 优先级：当前层 > 父级层
  const findValueByJsonPath = (path: string): any => {
    let context: ScopeContext | undefined = currentContext;

    while (context) {
      try {
        const value = getValueByJsonPath(context.data, path);
        // 只有真正找到值时才返回（排除undefined和null的区别）
        if (value !== undefined) {
          return value;
        }
      } catch (error) {
        // JsonPath 解析失败，继续向上查找
        console.debug("JsonPath解析失败:", path, error);
      }
      context = context.parent;
    }

    return undefined;
  };

  return {
    // 获取数据 - 使用链式查找
    getData: (path: string) => {
      if (isJsonPath(path)) {
        return findValueByJsonPath(path);
      }
      return findValue(path);
    },

    // 设置单个数据 - 只设置到当前层
    setData: (path: string, value: any) => {
      if (isJsonPath(path)) {
        return setValueByJsonPath(currentData, path, value);
      }

      const parts = path.split(".");
      const last = parts.pop()!;
      const target = parts.reduce((acc, key) => {
        // 确保acc是对象且不为null
        if (!acc || typeof acc !== "object") {
          throw new Error(`Cannot set property '${key}' on ${acc}`);
        }
        return (acc[key] = acc[key] || {});
      }, currentData as any);
      target[last] = value;
    },

    // 设置多个数据 - 只设置到当前层
    setDatas: (data: ScopeData) => {
      Object.assign(currentData, data);
    },

    // 监听数据变化 - 监听链式查找的结果，返回停止监听函数
    watch: (path: string, callback: (value: any, oldValue: any) => void) => {
      const value = computed(() => {
        if (isJsonPath(path)) {
          return findValueByJsonPath(path);
        }
        return findValue(path);
      });

      // 返回停止监听的函数
      return watch(value, callback, {
        immediate: false,
        deep: true,
      });
    },

    // 获取当前层数据
    getCurrentData: () => currentData,

    // 获取所有层级数据（调试用）- 修复数据覆盖逻辑
    getAllData: () => {
      const allData = {};
      const contexts: ScopeContext[] = [];

      // 收集所有上下文，从根到叶
      let context: ScopeContext | undefined = currentContext;
      while (context) {
        contexts.unshift(context); // 插入到开头，保证父级在前
        context = context.parent;
      }

      // 按照父级->子级的顺序合并数据，子级覆盖父级
      contexts.forEach((ctx) => {
        Object.assign(allData, ctx.data);
      });

      return allData;
    },

    // 检查路径是否存在
    hasData: (path: string): boolean => {
      if (isJsonPath(path)) {
        return findValueByJsonPath(path) !== undefined;
      }
      return findValue(path) !== undefined;
    },

    // 删除数据（仅当前层）
    deleteData: (path: string) => {
      if (isJsonPath(path)) {
        // JsonPath删除逻辑需要自行实现
        console.warn("JsonPath删除功能暂未实现");
        return false;
      }

      const parts = path.split(".");
      const last = parts.pop()!;

      if (parts.length === 0) {
        // 删除根级属性
        delete currentData[last];
        return true;
      }

      const target = parts.reduce((acc, key) => acc?.[key], currentData);
      if (target && typeof target === "object") {
        delete target[last];
        return true;
      }

      return false;
    },
  };
}
