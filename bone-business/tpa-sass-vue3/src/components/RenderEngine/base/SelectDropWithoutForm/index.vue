<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { useDictSelect } from "@/hooks";

defineOptions({
  name: "SelectDropWithoutForm",
});

const seperator = ",";

const modelValue = defineModel({
  type: [Array, String],
});
const props = defineProps({
  field: {
    type: Object,
    required: true,
  },
  format: {
    type: String,
    default: "array",
  },
  disabled: {
    type: Boolean,
    default: false,
  },
});

const sourceType = computed(() => props.field?.selectDatasource?.type);
const sourceCode = computed(() => props.field?.selectDatasource?.code);
const isMultiple = computed(() => props.field?.multiple ?? true);
const isFilterable = computed(() => props.field?.filterable ?? true);

// 添加计算属性处理内部值和外部值的转换
const innerValue = computed({
  get: () => {
    if (isMultiple.value) {
      // 多选模式：返回数组
      if (typeof modelValue.value === "string") {
        return modelValue.value ? modelValue.value.split(seperator) : [];
      }
      if (Array.isArray(modelValue.value)) {
        return modelValue.value;
      }
      return [];
    } else {
      // 单选模式：返回单个值
      if (typeof modelValue.value === "string") {
        return modelValue.value;
      }
      if (Array.isArray(modelValue.value)) {
        return modelValue.value[0] || "";
      }
      return modelValue.value || "";
    }
  },
  set: (val) => {
    if (isMultiple.value) {
      // 多选模式：val 是数组
      if (props.format === "string") {
        modelValue.value = val.join(seperator);
      } else {
        modelValue.value = val;
      }
    } else {
      // 单选模式：val 是单个值
      if (props.format === "string") {
        modelValue.value = val;
      } else if (props.format === "array") {
        modelValue.value = val ? [val] : [];
      } else {
        modelValue.value = val;
      }
    }
  },
});

// 现在 useDictSelect 支持响应式参数，直接传入计算属性即可
const { dictOptions, loadDictData, searchDictData, loadMoreDictData } =
  useDictSelect(sourceType, sourceCode);

// 初始加载数据
onMounted(() => {
  if (sourceType.value && sourceCode.value) {
    loadDictData();
  }
});
</script>

<template>
  <el-select
    v-model="innerValue"
    :filterable="isFilterable"
    :multiple="isMultiple"
    :remote="isFilterable"
    :remote-method="searchDictData"
    :remote-show-suffix="true"
    v-loadMore="loadMoreDictData"
    style="width: 100%"
    :disabled="disabled"
    clearable
  >
    <el-option
      v-for="option in dictOptions"
      :key="option.code"
      :label="option.name"
      :value="option.code"
    />
  </el-select>
</template>

<style lang="scss" scoped></style>
