import TableAPI from "@/api/table";
import { SummaryMethodEnum } from "@/enums/table/SummaryMethodEnum";
import { aggregateSummaryRow } from "@/utils/aggregation/index";
import { getValueByJsonPath } from "@/utils/jsonpathUtils";

export function useSummary(componentManager) {
  const summaryRowData = ref([]);

  const setSummary = computed(() => ({ columns, data }) => {
    if (!summaryRowData.value.length) return undefined;

    return columns.map((column, index) => {
      if (index === 0) return "合计";

      const summaryItem = summaryRowData.value.find(
        (item) => item.fieldId === column.columnKey
      );
      if (!summaryItem) return "";

      if (summaryItem.dataSummaryType === SummaryMethodEnum.COUNT) {
        return data.length;
      }

      const targetField = componentManager.get(summaryItem.fieldId);
      if (!targetField) return "";

      const values = data.map((item) =>
        getValueByJsonPath(item, targetField.dataBinding)
      );

      try {
        return aggregateSummaryRow(values, summaryItem.dataSummaryType) ?? "";
      } catch {
        return "";
      }
    });
  });

  const initSummaryRowData = async (data) => {
    summaryRowData.value = data || [];
  };

  return {
    summaryRowData,
    setSummary,
    initSummaryRowData,
  };
}
