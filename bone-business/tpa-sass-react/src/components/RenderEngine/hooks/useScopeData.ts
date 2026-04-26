
import {
  getValueByJsonPath,
  setValueByJsonPath,
  isJsonPath,
} from "@/utils/jsonpathUtils";
import React, {
  createContext,
  useContext,
  useState,
  useCallback,
  useMemo,
  useEffect,
} from "react";

interface ScopeData {
  [key: string]: any;
}

interface ScopeContextType {
  data: ScopeData;
  parent?: ScopeContextType;
}

const ScopeContext = createContext<ScopeContextType | undefined>(undefined);

interface ScopeProviderProps {
  children: React.ReactNode;
  initialData?: ScopeData;
}

export function ScopeProvider({ children, initialData = {} }: ScopeProviderProps) {
  const parentContext = useContext(ScopeContext);
  const [currentData] = useState<ScopeData>(() => ({ ...initialData }));

  const contextValue = useMemo(
    () => ({
      data: currentData,
      parent: parentContext,
    }),
    [currentData, parentContext]
  );

  return (
    <ScopeContext.Provider value={contextValue}>{children}</ScopeContext.Provider>
  );
}

interface UseScopeDataResult {
  getData: (path: string) => any;
  setData: (path: string, value: any) => void;
  setDatas: (data: ScopeData) => void;
  getCurrentData: () => ScopeData;
  getAllData: () => ScopeData;
  hasData: (path: string) => boolean;
  deleteData: (path: string) => boolean;
}

export function useScopeData(): UseScopeDataResult {
  const context = useContext(ScopeContext);

  if (!context) {
    throw new Error("useScopeData must be used within a ScopeProvider");
  }

  const findValue = useCallback((path: string, startContext?: ScopeContextType): any => {
    const keys = path.split(".");
    let ctx: ScopeContextType | undefined = startContext || context;

    while (ctx) {
      let value = ctx.data;
      let found = true;

      for (const key of keys) {
        if (value && typeof value === "object" && key in value) {
          value = value[key];
        } else {
          found = false;
          break;
        }
      }

      if (found && value !== undefined) {
        return value;
      }

      ctx = ctx.parent;
    }

    return undefined;
  }, [context]);

  const findValueByJsonPath = useCallback((path: string, startContext?: ScopeContextType): any => {
    let ctx: ScopeContextType | undefined = startContext || context;

    while (ctx) {
      try {
        const value = getValueByJsonPath(ctx.data, path);
        if (value !== undefined) {
          return value;
        }
      } catch (error) {
        console.debug("JsonPath解析失败:", path, error);
      }
      ctx = ctx.parent;
    }

    return undefined;
  }, [context]);

  const getData = useCallback(
    (path: string) => {
      if (isJsonPath(path)) {
        return findValueByJsonPath(path);
      }
      return findValue(path);
    },
    [findValue, findValueByJsonPath]
  );

  const setData = useCallback(
    (path: string, value: any) => {
      if (isJsonPath(path)) {
        return setValueByJsonPath(context.data, path, value);
      }

      const parts = path.split(".");
      const last = parts.pop()!;
      const target = parts.reduce((acc, key) => {
        if (!acc || typeof acc !== "object") {
          throw new Error(`Cannot set property '${key}' on ${acc}`);
        }
        return (acc[key] = acc[key] || {});
      }, context.data as any);
      target[last] = value;
    },
    [context.data]
  );

  const setDatas = useCallback(
    (data: ScopeData) => {
      Object.assign(context.data, data);
    },
    [context.data]
  );

  const getCurrentData = useCallback(() => context.data, [context.data]);

  const getAllData = useCallback(() => {
    const allData = {};
    const contexts: ScopeContextType[] = [];

    let ctx: ScopeContextType | undefined = context;
    while (ctx) {
      contexts.unshift(ctx);
      ctx = ctx.parent;
    }

    contexts.forEach((c) => {
      Object.assign(allData, c.data);
    });

    return allData;
  }, [context]);

  const hasData = useCallback(
    (path: string): boolean => {
      if (isJsonPath(path)) {
        return findValueByJsonPath(path) !== undefined;
      }
      return findValue(path) !== undefined;
    },
    [findValue, findValueByJsonPath]
  );

  const deleteData = useCallback(
    (path: string) => {
      if (isJsonPath(path)) {
        console.warn("JsonPath删除功能暂未实现");
        return false;
      }

      const parts = path.split(".");
      const last = parts.pop()!;

      if (parts.length === 0) {
        delete context.data[last];
        return true;
      }

      const target = parts.reduce((acc, key) => acc?.[key], context.data);
      if (target && typeof target === "object") {
        delete target[last];
        return true;
      }

      return false;
    },
    [context.data]
  );

  return {
    getData,
    setData,
    setDatas,
    getCurrentData,
    getAllData,
    hasData,
    deleteData,
  };
}
