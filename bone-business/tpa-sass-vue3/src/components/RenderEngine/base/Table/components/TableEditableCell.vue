<script setup>
import { ref, computed, watch, shallowRef } from "vue";
import { useDataBinding } from "@/components/RenderEngine/hooks/useDataBinding";
import { useScopeData } from "@/components/RenderEngine/hooks/useScopeData";
import { DataFormatEnum } from "@/enums/baseComp/DataFormatEnum";
import { BaseCompType } from "@/enums";
import { useDictStore } from "@/store";
import { formatDate } from "@/utils/date";
import { getValueByJsonPath } from "@/utils/jsonpathUtils";
import { getDateFormat } from "@/enums/baseComp/DateFormatEnum";

defineOptions({ name: "TableEditableCell" });

const scopeData = useScopeData();
const tableManager = scopeData.getData("tableManager");

const emits = defineEmits(["dblclick"]);
const props = defineProps({
  tableItem: {
    type: Object,
    default: () => ({}),
  },
  tableRowId: {
    type: String,
    default: "",
  },
  tableId: {
    type: String,
    default: "",
  },
  tableIndex: {
    type: Number,
    default: -1,
  },
  isEditing: {
    type: Boolean,
    default: false,
  },
});

const cellValue = shallowRef("");
const targetDataBinding = ref("");
const targetProp = ref("");
const targetValue = ref("");
const displayValue = ref("");

// extraStore路径
const extraStorePath = computed(() => {
  const { dataBinding, code } = props.tableItem;
  const { targetDataBinding } = useDataBinding(dataBinding);

  //分成两种处理方式，标准字段就是去掉code拼接extraStore.code; 扩展字段为去掉extraPropertites.code拼接extraStore.code
  const parts = targetDataBinding.value.split(".");

  if (parts.includes("extraProperties")) {
    // 扩展字段：去掉 `.extraProperties.code`
    const idx = parts.indexOf("extraProperties");
    return parts.slice(0, idx).join(".") + `.extraStore.${code}`;
  } else {
    // 标准字段：去掉最后的 `.code`
    return parts.slice(0, -1).join(".") + `.extraStore.${code}`;
  }
});

const getExtraStoreValue = () => {
  return (
    getValueByJsonPath(
      tableManager.getRow(props.tableId, props.tableIndex),
      extraStorePath.value
    ) || undefined
  );
};

const getSelectDropValue = async (value) => {
  const extraStoreValue = getExtraStoreValue();

  if (extraStoreValue) {
    if (extraStoreValue.otherContent && extraStoreValue.otherFlag) {
      return extraStoreValue.name + "(" + extraStoreValue.otherContent + ")";
    } else {
      return extraStoreValue.name;
    }
  }

  const dictStore = useDictStore();
  const dictLabels = await dictStore.getDictLabel(
    props.tableItem.selectDatasource.type,
    props.tableItem.selectDatasource.code,
    value
  );
  // 如果value是数组，则返回数组中每个值对应的标签
  if (Array.isArray(value)) {
    return value.map((item) => dictLabels[item] || item).join(",");
  }

  return dictLabels[value] || value;
};

// 监听 cellValue 变化，处理 SelectDrop 的异步标签获取
watch(
  [() => cellValue.value, () => getExtraStoreValue()],
  async ([newValue, extraStoreValue]) => {
    if (newValue === null || newValue === undefined || newValue === "") {
      displayValue.value = "";
      return;
    }

    if (props.tableItem.type.replace("PK", "") === BaseCompType.SelectDrop) {
      displayValue.value = await getSelectDropValue(newValue);
    }
  },
  { immediate: true }
);

const handleSelectCtrlValue = (value) => {
  try {
    if (!value) return value;
    const parsedValue = JSON.parse(value);
    if (!parsedValue?.desc?.length) return undefined;
    const validValues = parsedValue.desc.filter((item) => item && item.trim());
    return validValues.length ? validValues.join("/") : undefined;
  } catch (e) {
    return value;
  }
};

const showCellValue = computed(() => {
  if (cellValue.value === null || cellValue.value === undefined) {
    return undefined;
  }

  switch (props.tableItem.type.replace("PK", "")) {
    case BaseCompType.DateTime:
      return formatDate(
        cellValue.value,
        getDateFormat(props.tableItem.dateFormatType)
      );
    case BaseCompType.DateRange:
      return cellValue.value.replace(",", " ~ ");
    case BaseCompType.SelectDrop:
      return displayValue.value;
    case BaseCompType.SelectCtrl:
      return handleSelectCtrlValue(cellValue.value);
    case BaseCompType.InputNum:
      return props.tableItem.dataFormat === DataFormatEnum.percentage
        ? cellValue.value + "%"
        : cellValue.value;
    default:
      return cellValue.value;
  }
});

const initDataBinding = () => {
  const { dataBinding } = props.tableItem;
  const { targetDataBinding: _dataBinding, targetProp: _prop } = useDataBinding(
    dataBinding,
    props.tableIndex
  );

  targetDataBinding.value = _dataBinding.value;
  targetProp.value = _prop.value;

  // 统一在这里获取和设置数据，避免在 watch 中重复触发联动规则
  targetValue.value = tableManager.getByJp(
    props.tableId,
    targetDataBinding.value
  );

  // 同步更新 cellValue，确保数据一致性
  cellValue.value = targetValue.value;
};

const initTableFieldProps = computed(() => {
  return {
    ...props.tableItem,
    targetValue: targetValue.value,
    targetProp: targetProp.value,
    showFieldLabel: false,
    isTableField: true,
    tableRow: tableManager.getRow(props.tableId, props.tableIndex),
  };
});

// 监听tableIndex变化，重新计算数据绑定
watch(
  () => props.tableRowId,
  () => {
    initDataBinding();
  },
  { immediate: true }
);

// 保留 targetValue 的监听，但修改为直接监听表格数据变化
watch(
  () => [
    props.tableId,
    targetDataBinding.value,
    tableManager.getByJp(props.tableId, targetDataBinding.value),
  ],
  ([tableId, dataBinding, newValue]) => {
    targetValue.value = newValue;
    if (cellValue.value !== newValue) {
      cellValue.value = newValue;
    }
  },
  { immediate: true }
);

const handleChange = (newVal) => {
  cellValue.value = newVal;
  tableManager.setByJp(props.tableId, targetDataBinding.value, cellValue.value);
};

const errorHintDataBinding = computed(() => {
  const { dataBinding } = props.tableItem;
  const { targetDataBinding } = useDataBinding(dataBinding);
  //去掉最后一个.后的内容，拼接syncHintMap.code
  return (
    targetDataBinding.value.split(".").slice(0, -1).join(".") +
    ".syncHintMsg." +
    props.tableItem.code
  );
});

const errorHint = computed(() => {
  // 如果错误提示的绑定为空，则不显示错误提示
  let value = undefined;
  value = getValueByJsonPath(
    tableManager.getRow(props.tableId, props.tableIndex),
    errorHintDataBinding.value
  );

  if (!value) {
    return undefined;
  }

  // 当有值时，显示黄色错误提示
  if (showCellValue.value) {
    return {
      value: value,
      color: "#ff9a2e",
    };
  }

  // 当没有值时，显示红色错误提示
  return {
    value: value,
    color: "#f76560",
  };
});
</script>

<template>
  <div class="editable-cell">
    <div v-if="!isEditing">
      <el-tooltip
        v-if="errorHint"
        effect="dark"
        :content="errorHint.value"
        placement="top"
      >
        <el-icon :color="errorHint.color" size="1.2em">
          <Warning />
        </el-icon>
      </el-tooltip>
      <template v-if="tableItem.type.replace('PK', '') !== 'Custom'">
        <span class="whitespace-pre-wrap">{{ showCellValue ?? "" }}</span>
      </template>
      <template v-else>
        <component
          :is="tableItem.type"
          v-bind="initTableFieldProps"
          :isCellView="true"
        />
      </template>
    </div>
    <component
      v-else
      :is="tableItem.type"
      v-bind="initTableFieldProps"
      @change="handleChange"
    />
  </div>
</template>

<style lang="scss" scoped>
.editable-cell {
  width: 100%;
  height: 100%;
  padding: 0;
  transition: background-color 0.5s ease;
}
</style>
