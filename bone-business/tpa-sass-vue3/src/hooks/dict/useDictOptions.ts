import { usePagedList } from "@/hooks/index";
import PkDictAPI from "@/api/pk-dict";

export default function useDictOptions(pageSize = 20) {
  const {
    list: dictOptions,
    pageQuery: dictOptionsPageQuery,
    methods,
  } = usePagedList({
    fetchFunction: PkDictAPI.getByType,
    defaultPageSize: pageSize,
  });

  return {
    dictOptions,
    dictOptionsPageQuery,
    dictOptionsMethods: {
      init: async (type: number, parentCode: string) => {
        await methods.init({ type, parentCode });
      },
      search: async (name: string) => {
        if (
          !dictOptionsPageQuery.value.type ||
          !dictOptionsPageQuery.value.parentCode
        ) {
          console.error("请先初始化数据源");
          return;
        }

        await methods.search({ name });
      },
      loadMore: async () => {
        if (
          !dictOptionsPageQuery.value.type ||
          !dictOptionsPageQuery.value.parentCode
        ) {
          console.error("请先初始化数据源");
          return;
        }

        await methods.loadMore();
      },
      loadSelected: async (
        type: number,
        dictType: string,
        value: string | string[]
      ) => {
        if (!value) return;

        const values = Array.isArray(value) ? value : value.split(",");
        // 检查是否所有选中值都已在现有选项中
        const missingValues = values.filter(
          (v) => !dictOptions.value.some((opt) => opt.code === v)
        );

        if (missingValues.length > 0) {
          // 调用后端API获取这些特定值对应的选项数据
          const selectedOptions = await PkDictAPI.getByCode(
            type,
            dictType,
            missingValues
          );
          // 将获取到的选项添加到现有选项列表中
          dictOptions.value = [...dictOptions.value, ...selectedOptions];
        }
      },
    },
  };
}
