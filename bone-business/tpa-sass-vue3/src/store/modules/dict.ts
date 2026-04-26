import DictAPI, { DictBatchVO } from "@/api/pk-dict";
import { DictItem } from "@/api/pk-dict";
import { DictSourceTypeEnum } from "@/enums/DictSourceTypeEnum";
import { ref, Ref } from "vue";
import { useStorage } from "@vueuse/core";

/**
 * 基础字典配置类型
 */
interface BaseDictConfig {
  type: number;
  parentCode: string;
}

/**
 * 基础字典配置数组
 * 这些字典会在应用初始化时一次性加载
 * @description 每个配置项包含：
 * - type: 字典类型
 * - parentCode: 父级编码
 */
export const BASE_DICT_CONFIGS: BaseDictConfig[] = [
  { type: DictSourceTypeEnum.ENUM, parentCode: "yesOrNo" },
  { type: DictSourceTypeEnum.ENUM, parentCode: "gender" },
  { type: DictSourceTypeEnum.ENUM, parentCode: "slipAttribute" },
  { type: DictSourceTypeEnum.ENUM, parentCode: "sourceType" },
  { type: DictSourceTypeEnum.ENUM, parentCode: "invoiceInputType" },
  { type: DictSourceTypeEnum.ENUM, parentCode: "collectTransferType" },
  { type: DictSourceTypeEnum.ENUM, parentCode: "invoicePaperType" },
  { type: DictSourceTypeEnum.ENUM, parentCode: "collectType" },
  { type: DictSourceTypeEnum.ENUM, parentCode: "visitType" },
  { type: DictSourceTypeEnum.ENUM, parentCode: "invoiceType" },
  { type: DictSourceTypeEnum.ENUM, parentCode: "verifyValid" },
  { type: DictSourceTypeEnum.ENUM, parentCode: "medicalType" },
  { type: DictSourceTypeEnum.ENUM, parentCode: "hangUpStatus" },
  { type: DictSourceTypeEnum.ENUM, parentCode: "hangUpType" },
  { type: DictSourceTypeEnum.ENUM, parentCode: "bizType" },
  { type: DictSourceTypeEnum.ENUM, parentCode: "processType" },
];

/**
 * 字典状态接口
 * @interface DictState
 */
interface DictState {
  /** 字典项列表 */
  items: DictItem[];
  /** 分页查询参数 */
  pageQuery: {
    /** 当前页码 */
    pageNum: number;
    /** 每页大小 */
    pageSize: number;
    /** 总记录数 */
    totalSize: number;
    /** 字典类型 */
    type?: number;
    /** 父级编码 */
    parentCode?: string;
  };
}

const ENUM_DICT_STORAGE_KEY = "PK_ENUM_DICT";
const DICT_INITIALIZED_KEY = "PK_DICT_INITIALIZED";
const DEFAULT_PAGE_SIZE = 50;

export const useDictStore = defineStore("dict", () => {
  const dictStateMap = ref<Record<string, DictState>>({});
  const isInitialized = useStorage(DICT_INITIALIZED_KEY, false, sessionStorage);
  const pendingRequests = new Map<string, Promise<any>>();

  /**
   * 获取或创建字典状态
   * @param type - 字典类型
   * @param parentCode - 父级编码
   * @returns 字典状态对象
   */
  const getDictState = (type: number, parentCode: string): DictState => {
    const key = `${type}_${parentCode}`;
    if (!dictStateMap.value[key]) {
      dictStateMap.value[key] = {
        items: [],
        pageQuery: {
          pageNum: 1,
          pageSize: DEFAULT_PAGE_SIZE,
          totalSize: 0,
          type,
          parentCode,
        },
      };
    }
    return dictStateMap.value[key];
  };

  /**
   * 确保枚举字典的sessionStorage和内存状态同步
   * @param parentCode - 父级编码
   * @returns 是否需要从API加载数据
   */
  const ensureEnumDictSync = (parentCode: string): boolean => {
    try {
      const state = getDictState(DictSourceTypeEnum.ENUM, parentCode);
      const storageData = sessionStorage.getItem(ENUM_DICT_STORAGE_KEY);

      if (!storageData) {
        // sessionStorage中没有数据，如果内存中有数据则保存到sessionStorage
        if (state.items.length > 0) {
          saveEnumDictToStorage(parentCode, state.items);
          return false; // 内存中有数据，不需要从API加载
        }
        return true; // 都没有数据，需要从API加载
      }

      const data = JSON.parse(storageData);
      const storageItems = data[parentCode];

      if (!storageItems || storageItems.length === 0) {
        // sessionStorage中该parentCode没有数据
        if (state.items.length > 0) {
          // 内存中有数据，保存到sessionStorage
          saveEnumDictToStorage(parentCode, state.items);
          return false;
        }
        return true; // 都没有数据，需要从API加载
      }

      // sessionStorage中有数据
      if (state.items.length === 0) {
        // 内存中没有数据，从sessionStorage同步
        state.items = storageItems;
        state.pageQuery.totalSize = storageItems.length;
        return false;
      }

      // 两边都有数据，检查是否一致（比较长度）
      if (state.items.length !== storageItems.length) {
        // 数据不一致，以sessionStorage为准（因为它是持久化的）
        state.items = storageItems;
        state.pageQuery.totalSize = storageItems.length;
      }

      return false; // 有数据，不需要从API加载
    } catch (error) {
      console.error("同步枚举字典状态失败:", error);
      return true; // 出错时从API重新加载
    }
  };

  /**
   * 创建请求key
   */
  const createRequestKey = (
    type: number,
    parentCode: string,
    searchText?: string,
    value?: string | string[]
  ) => {
    const valueStr = value
      ? Array.isArray(value)
        ? value.join(",")
        : value
      : "";
    return `${type}_${parentCode}_${searchText || ""}_${valueStr}`;
  };

  /**
   * 执行请求，带缓存机制
   */
  const executeRequest = async <T>(
    key: string,
    requestFn: () => Promise<T>
  ): Promise<T> => {
    // 如果已经有相同的请求在进行中，直接返回该请求的Promise
    if (pendingRequests.has(key)) {
      return pendingRequests.get(key);
    }

    // 创建新的请求
    const request = requestFn().finally(() => {
      // 请求完成后从缓存中移除
      pendingRequests.delete(key);
    });

    // 缓存请求
    pendingRequests.set(key, request);
    return request;
  };

  /**
   * 保存枚举字典到 sessionStorage 并同步到内存状态
   * @param parentCode - 父级编码
   * @param items - 字典项数组
   */
  const saveEnumDictToStorage = (
    parentCode: string,
    items: DictItem[]
  ): void => {
    try {
      const existingData = sessionStorage.getItem(ENUM_DICT_STORAGE_KEY);
      const storageData = existingData ? JSON.parse(existingData) : {};
      storageData[parentCode] = items;
      sessionStorage.setItem(
        ENUM_DICT_STORAGE_KEY,
        JSON.stringify(storageData)
      );

      // 同步更新到dictStateMap
      const state = getDictState(DictSourceTypeEnum.ENUM, parentCode);
      state.items = items;
      state.pageQuery.totalSize = items.length;
    } catch (error) {
      console.error("保存枚举字典到存储失败:", error);
      throw error;
    }
  };

  /**
   * 加载字典数据
   * @param type - 字典类型
   * @param parentCode - 父级编码
   * @param searchText - 可选的搜索文本
   * @description 支持分页加载和搜索，自动去重，枚举字典自动缓存
   */
  const loadDictData = async (
    type: number,
    parentCode: string,
    searchText?: string,
    isLoadMore: boolean = false
  ): Promise<void> => {
    const state = getDictState(type, parentCode);
    const { pageQuery } = state;
    const requestKey = createRequestKey(type, parentCode, searchText);

    try {
      // 只有在真正加载更多时才递增pageNum
      if (isLoadMore && !searchText) {
        state.pageQuery.pageNum++;
      }

      const params = {
        ...pageQuery,
        name: searchText,
      };

      const res = await executeRequest(requestKey, () =>
        DictAPI.getByType(params)
      );

      // 使用 Map 进行去重，以 code 为 key
      const itemMap = new Map<string, DictItem>();

      // 先添加现有数据
      state.items.forEach((item) => {
        itemMap.set(item.code, item);
      });

      // 添加新数据，如果有相同 code 会覆盖
      res.rows.forEach((item) => {
        itemMap.set(item.code, item);
      });

      // 转换回数组
      state.items = searchText ? res.rows : Array.from(itemMap.values());
      state.pageQuery.totalSize = res.totalSize;

      if (type === DictSourceTypeEnum.ENUM && !searchText) {
        saveEnumDictToStorage(parentCode, state.items);
      }
    } catch (error) {
      console.error("加载字典数据失败:", error);
      throw error;
    }
  };

  /**
   * 获取字典数据
   * @param type - 字典类型
   * @param parentCode - 父级编码
   * @param forceRefresh - 是否强制刷新
   * @returns 字典项数组
   * @description 优先使用缓存，支持强制刷新，枚举字典优先使用 sessionStorage
   */
  const getDict = async (
    type: number,
    parentCode: string,
    forceRefresh: boolean = false
  ): Promise<DictItem[]> => {
    try {
      if (type === null || type === undefined || !parentCode) {
        return [];
      }

      const state = getDictState(type, parentCode);

      // 对于枚举类型，确保sessionStorage和内存状态同步
      if (type === DictSourceTypeEnum.ENUM && !forceRefresh) {
        const needLoad = ensureEnumDictSync(parentCode);
        if (!needLoad && state.items.length > 0) {
          return state.items;
        }
        // 如果needLoad为true，继续执行下面的加载逻辑
      }

      // 非枚举类型或强制刷新时，检查内存状态
      if (state.items.length > 0 && !forceRefresh) {
        return state.items;
      }

      // 需要重新加载数据
      state.items = [];
      state.pageQuery.pageNum = 1;
      state.pageQuery.totalSize = 0;

      await loadDictData(type, parentCode);
      return state.items;
    } catch (error) {
      console.error("获取字典失败:", error);
      throw error;
    }
  };

  /**
   * 批量获取多个字典数据
   * @param paramList - 字典参数列表
   * @returns 字典项数组
   * @description 适用于初始化或批量获取场景，枚举字典自动缓存
   */
  const getDicts = async (
    paramList: { type: number; parentCode: string }[],
    pageNum: number = 1,
    pageSize: number = 50
  ): Promise<DictBatchVO[]> => {
    try {
      if (paramList.length === 0) {
        return [];
      }

      const res = await DictAPI.batchGetByType(paramList, pageNum, pageSize);

      paramList.forEach((item) => {
        const { type, parentCode } = item;
        const state = getDictState(type, parentCode);

        const resItem = res.find(
          (item) => item.type === type && item.parentCode === parentCode
        );

        state.items = resItem?.data || [];
        state.pageQuery.totalSize = resItem?.totalSize || 0;
        state.pageQuery.pageNum = pageNum;
        state.pageQuery.pageSize = pageSize;

        if (type === DictSourceTypeEnum.ENUM) {
          saveEnumDictToStorage(parentCode, state.items);
        }
      });

      return res;
    } catch (error) {
      console.error("批量获取字典失败:", error);
      throw error;
    }
  };

  /**
   * 加载更多字典数据
   * @param type - 字典类型
   * @param parentCode - 父级编码
   * @returns 包含字典项和是否还有更多数据的对象
   * @description 适用于分页加载更多场景，自动去重
   */
  const loadMore = async (
    type: number,
    parentCode: string
  ): Promise<{ items: DictItem[]; hasMore: boolean }> => {
    try {
      if (type === null || type === undefined || !parentCode) {
        return {
          items: [],
          hasMore: false,
        };
      }

      const state = getDictState(type, parentCode);

      // 对于枚举类型，确保同步并检查是否需要加载
      if (type === DictSourceTypeEnum.ENUM) {
        const needLoad = ensureEnumDictSync(parentCode);
        if (!needLoad) {
          // 枚举字典通常是完整的，不需要分页
          return {
            items: state.items,
            hasMore: false,
          };
        }
        // 如果needLoad为true，继续执行下面的加载逻辑
      }

      if (state.items.length === 0) {
        await loadDictData(type, parentCode);
        return {
          items: state.items,
          hasMore:
            state.pageQuery.pageNum * state.pageQuery.pageSize <
            state.pageQuery.totalSize,
        };
      }

      const hasMore =
        state.pageQuery.pageNum * state.pageQuery.pageSize <
        state.pageQuery.totalSize;
      if (!hasMore) {
        return {
          items: state.items,
          hasMore: false,
        };
      }

      await loadDictData(type, parentCode, undefined, true);

      return {
        items: state.items,
        hasMore:
          state.pageQuery.pageNum * state.pageQuery.pageSize <
          state.pageQuery.totalSize,
      };
    } catch (error) {
      console.error("加载更多字典数据失败:", error);
      throw error;
    }
  };

  /**
   * 搜索字典数据
   * @param type - 字典类型
   * @param parentCode - 父级编码
   * @param searchText - 搜索文本
   * @returns 搜索结果字典项数组
   * @description 搜索时重置状态，返回新的搜索结果，枚举字典优先从sessionStorage搜索
   */
  const getDictWithSearch = async (
    type: number,
    parentCode: string,
    searchText: string
  ): Promise<DictItem[]> => {
    try {
      if (type === null || type === undefined || !parentCode) {
        return [];
      }

      // 对于枚举类型，优先从缓存获取完整数据后进行前端搜索
      if (type === DictSourceTypeEnum.ENUM) {
        const state = getDictState(type, parentCode);
        const needLoad = ensureEnumDictSync(parentCode);

        // 如果需要加载数据，先加载
        if (needLoad) {
          await loadDictData(type, parentCode);
        }

        // 现在进行前端搜索（无论是否需要加载，都应该有数据了）
        if (state.items.length > 0) {
          const filteredItems = state.items.filter(
            (item) =>
              item.name.toLowerCase().includes(searchText.toLowerCase()) ||
              item.code.toLowerCase().includes(searchText.toLowerCase())
          );
          return filteredItems;
        }

        // 如果还是没有数据，返回空数组
        return [];
      }

      // 非枚举类型，使用API搜索
      const state = getDictState(type, parentCode);

      state.items = [];
      state.pageQuery.pageNum = 1;
      state.pageQuery.totalSize = 0;

      await loadDictData(type, parentCode, searchText);
      return state.items;
    } catch (error) {
      console.error("搜索字典数据失败:", error);
      throw error;
    }
  };

  /**
   * 获取字典标签
   * @param type - 字典类型
   * @param parentCode - 父级编码
   * @param code - 字典编码或编码数组
   * @returns 编码到标签的映射对象，如果编码不存在则对应的值为空字符串
   */
  const getDictLabel = async (
    type: number,
    parentCode: string,
    code: string | string[]
  ): Promise<Record<string, string>> => {
    try {
      if (type === null || type === undefined || !parentCode) {
        return {};
      }

      if (!code || (Array.isArray(code) && code.every((item) => !item))) {
        return {};
      }

      const codes = Array.isArray(code) ? code : [code];
      const state = getDictState(type, parentCode);
      const result: Record<string, string> = {};

      // 初始化所有编码的值为空字符串
      codes.forEach((code) => {
        result[code] = "";
      });

      // 确保有基础数据
      if (type === DictSourceTypeEnum.ENUM) {
        const needLoad = ensureEnumDictSync(parentCode);
        if (needLoad) {
          await loadDictData(type, parentCode);
        }
      } else {
        // 如果缓存为空，先加载一次全部数据
        if (state.items.length === 0) {
          await loadDictData(type, parentCode);
        }
      }

      // 先从缓存中查找
      const cachedItems = state.items.filter((item) =>
        codes.some((code) => code == item.code)
      );
      cachedItems.forEach((item) => {
        result[item.code] = item.name;
      });

      // 找出缓存中没有的编码
      const missingCodes = codes.filter(
        (code) => !cachedItems.some((item) => item.code === code)
      );

      // 如果已经加载了全部数据（缓存数据量等于总数），且还有缺失值，说明是脏数据
      if (
        missingCodes.length > 0 &&
        state.items.length === state.pageQuery.totalSize
      ) {
        console.warn(`发现可能的脏数据: ${missingCodes.join(", ")}`);
        return result;
      }

      if (missingCodes.length > 0) {
        // 基于真正需要请求的missingCodes生成requestKey
        const requestKey = createRequestKey(
          type,
          parentCode,
          "selected",
          missingCodes
        );

        // 通过API获取缺失的数据
        const items = await executeRequest(requestKey, () =>
          DictAPI.getByCode(type, parentCode, missingCodes)
        );
        // 将获取到的数据添加到缓存和结果中，使用Map去重
        const itemMap = new Map<string, DictItem>();

        // 先添加现有数据
        state.items.forEach((item) => {
          itemMap.set(item.code, item);
        });

        // 添加新数据，如果有相同 code 会覆盖
        items.forEach((item) => {
          itemMap.set(item.code, item);
        });

        // 更新state.items为去重后的数据
        state.items = Array.from(itemMap.values());
        items.forEach((item) => {
          result[item.code] = item.name;
        });

        // 如果是枚举类型，同步更新到sessionStorage
        if (type === DictSourceTypeEnum.ENUM) {
          saveEnumDictToStorage(parentCode, state.items);
        }
      }

      return result;
    } catch (error) {
      console.error("获取字典标签失败:", error);
      throw error;
    }
  };

  /**
   * 加载选中项
   * @param type - 字典类型
   * @param parentCode - 父级编码
   * @param value - 选中值，可以是单个值或数组
   * @description 先加载基础数据，然后加载缺失的选中项
   */
  const loadSelectedDict = async (
    type: number,
    parentCode: string,
    value: string | string[]
  ): Promise<void> => {
    try {
      if (type === null || type === undefined || !parentCode) {
        return;
      }

      if (!value || (Array.isArray(value) && value.every((item) => !item))) {
        return;
      }

      const state = getDictState(type, parentCode);

      // 确保有基础数据
      if (type === DictSourceTypeEnum.ENUM) {
        const needLoad = ensureEnumDictSync(parentCode);
        if (needLoad) {
          await loadDictData(type, parentCode);
        }
      } else {
        if (state.items.length === 0) {
          await loadDictData(type, parentCode);
        }
      }

      const values = Array.isArray(value) ? value : String(value).split(",");
      const missingValues = values.filter(
        (v) => !state.items.some((item) => item.code === v)
      );

      // 如果已经加载了全部数据（缓存数据量等于总数），且还有缺失值，说明是脏数据
      if (
        missingValues.length > 0 &&
        state.items.length === state.pageQuery.totalSize
      ) {
        console.warn(`发现可能的脏数据: ${missingValues.join(", ")}`);
        return;
      }

      if (missingValues.length > 0) {
        // 基于真正需要请求的missingValues生成requestKey
        const requestKey = createRequestKey(
          type,
          parentCode,
          "selected",
          missingValues
        );

        const selectedItems = await executeRequest(requestKey, () =>
          DictAPI.getByCode(type, parentCode, missingValues)
        );
        // 使用Map去重，避免重复添加相同code的数据
        const itemMap = new Map<string, DictItem>();

        // 先添加现有数据
        state.items.forEach((item) => {
          itemMap.set(item.code, item);
        });

        // 添加新数据，如果有相同 code 会覆盖
        selectedItems.forEach((item) => {
          itemMap.set(item.code, item);
        });

        // 更新state.items为去重后的数据
        state.items = Array.from(itemMap.values());

        // 如果是枚举类型，同步更新到sessionStorage
        if (type === DictSourceTypeEnum.ENUM) {
          saveEnumDictToStorage(parentCode, state.items);
        }
      }
    } catch (error) {
      console.error("加载选中字典失败:", error);
      throw error;
    }
  };

  /**
   * 初始化基础字典
   * @description 在应用启动时调用，一次性加载所有基础字典数据，优先使用sessionStorage中的枚举数据
   * @returns Promise<void>
   */
  const initBaseDicts = async (): Promise<void> => {
    if (isInitialized.value) {
      console.log("基础字典已初始化，跳过重复初始化");
      // 即使已初始化，也要确保sessionStorage数据同步到内存
      BASE_DICT_CONFIGS.forEach((config) => {
        if (config.type === DictSourceTypeEnum.ENUM) {
          ensureEnumDictSync(config.parentCode);
          // ensureEnumDictSync会自动同步sessionStorage和内存状态
        }
      });
      return;
    }

    try {
      console.log("开始初始化基础字典...");

      // 先检查sessionStorage中是否有枚举数据，如果有则同步到内存
      const enumConfigs = BASE_DICT_CONFIGS.filter(
        (config) => config.type === DictSourceTypeEnum.ENUM
      );
      const needLoadConfigs: BaseDictConfig[] = [];

      enumConfigs.forEach((config) => {
        const needLoad = ensureEnumDictSync(config.parentCode);
        if (needLoad) {
          needLoadConfigs.push(config);
        }
      });

      // 添加非枚举类型的配置
      const nonEnumConfigs = BASE_DICT_CONFIGS.filter(
        (config) => config.type !== DictSourceTypeEnum.ENUM
      );
      needLoadConfigs.push(...nonEnumConfigs);

      // 只加载需要的字典
      if (needLoadConfigs.length > 0) {
        await getDicts(needLoadConfigs);
      }

      isInitialized.value = true;
      console.log("基础字典初始化完成");
    } catch (error) {
      console.error("初始化基础字典失败:", error);
      throw error;
    }
  };

  /**
   * 检查基础字典是否已初始化
   * @returns boolean
   */
  const checkInitialized = (): boolean => {
    return isInitialized.value;
  };

  /**
   * 刷新字典
   * @param type - 可选的字典类型
   * @param parentCode - 可选的父级编码
   * @description 清理缓存并重新初始化基础字典
   */
  const refreshDict = async (
    type?: number,
    parentCode?: string
  ): Promise<void> => {
    clearCache(type, parentCode);
    await initBaseDicts();
  };

  /**
   * 清理缓存
   * @param type - 可选的字典类型
   * @param parentCode - 可选的父级编码
   * @description 支持清理单个字典缓存或所有缓存，确保sessionStorage和dictStateMap同步清理
   */
  const clearCache = (type?: number, parentCode?: string): void => {
    try {
      if (type && parentCode) {
        // 清理指定字典的缓存
        if (type === DictSourceTypeEnum.ENUM) {
          // 清理sessionStorage中的枚举字典
          const storageData = sessionStorage.getItem(ENUM_DICT_STORAGE_KEY);
          if (storageData) {
            const data = JSON.parse(storageData);
            delete data[parentCode];
            sessionStorage.setItem(ENUM_DICT_STORAGE_KEY, JSON.stringify(data));
          }
        }

        // 清理dictStateMap中的对应数据
        const key = `${type}_${parentCode}`;
        if (dictStateMap.value[key]) {
          delete dictStateMap.value[key];
        }
      } else {
        // 清理所有缓存
        sessionStorage.removeItem(ENUM_DICT_STORAGE_KEY);
        sessionStorage.removeItem(DICT_INITIALIZED_KEY);
        isInitialized.value = false;
        dictStateMap.value = {};
        // 清理所有待处理的请求
        pendingRequests.clear();
      }
    } catch (error) {
      console.error("清理缓存失败:", error);
      throw error;
    }
  };

  return {
    dictStateMap,
    getDict,
    getDicts,
    getDictWithSearch,
    getDictLabel,
    loadSelectedDict,
    loadMore,
    clearCache,
    initBaseDicts,
    checkInitialized,
    refreshDict,
  };
});
