import { usePagedList } from "@/hooks/index";
import PkDictAPI from "@/api/pk-dict";

export default function useDictList(pageSize = 20) {
  const {
    list: dictList,
    pageQuery: dictPageQuery,
    methods,
  } = usePagedList({
    fetchFunction: PkDictAPI.getTypes,
    defaultPageSize: pageSize,
  });

  return {
    dictList,
    dictPageQuery,
    dictListMethods: {
      ...methods,
      init: async (type: number) => {
        await methods.init({ type });
      },
      loadSelected: async (type: number, value: string) => {
        if (!value) return;

        // 检查是否选中值已在现有选项中
        const exists = dictList.value.some((opt) => opt.code === value);

        if (exists) return;

        const selectedOption = await PkDictAPI.getTypeByCode(type, value);
        dictList.value = [...dictList.value, selectedOption];
      },
    },
  };
}
