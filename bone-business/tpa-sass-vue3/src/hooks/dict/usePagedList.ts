import { ref } from "vue";

// 分页查询参数
interface IPageQuery {
  pageSize: number;
  pageNum: number;
  totalSize: number;
  [key: string]: any;
}

// 分页列表方法
interface PagedListMethods {
  init: (params?: Partial<IPageQuery>) => Promise<void>;
  loadMore: () => Promise<void>;
  search: (query: Partial<IPageQuery>) => Promise<void>;
}

// 分页查询结果
interface FetchResult {
  rows: any[];
  totalSize: number;
}

/**
 * 分页列表
 * @param fetchFunction 分页查询方法
 * @param defaultPageSize 默认每页数量
 * @returns
 */
export default function usePagedList({
  fetchFunction,
  defaultPageSize = 20,
}: {
  fetchFunction: (query: IPageQuery) => Promise<FetchResult>;
  defaultPageSize?: number;
}) {
  const list = ref<any[]>([]);
  const pageQuery: Ref<IPageQuery> = ref({
    pageSize: defaultPageSize,
    pageNum: 1,
    totalSize: 0,
  });

  const loadList = async () => {
    try {
      const res = await fetchFunction(pageQuery.value);
      const newRows = res.rows;

      if (pageQuery.value.pageNum === 1) {
        list.value = newRows;
      } else {
        // 添加去重逻辑
        const oldCodes = new Set(list.value.map((item) => item.code));
        const uniqueNewRows = newRows.filter(
          (item) => !oldCodes.has(item.code)
        );
        list.value = [...list.value, ...uniqueNewRows];
      }

      pageQuery.value.totalSize = res.totalSize;
    } catch (error) {
      console.error("加载选项失败:", error);
    }
  };

  const methods: PagedListMethods = {
    init: async (params = {}) => {
      pageQuery.value = {
        // 重置所有值
        pageSize: defaultPageSize,
        pageNum: 1,
        totalSize: 0,
        ...params,
      };
      list.value = [];
      await loadList();
    },
    loadMore: async () => {
      if (
        pageQuery.value.pageNum * pageQuery.value.pageSize <
        pageQuery.value.totalSize
      ) {
        pageQuery.value.pageNum++;
        await loadList();
      }
    },
    search: async (query) => {
      pageQuery.value = {
        ...pageQuery.value,
        ...query,
        pageNum: 1,
      };
      list.value = [];
      await loadList();
    },
  };

  return {
    list,
    pageQuery,
    methods,
  };
}
