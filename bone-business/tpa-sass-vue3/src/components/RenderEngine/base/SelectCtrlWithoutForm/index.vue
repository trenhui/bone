<script setup>
import { SelectLevelEnum } from "@/enums/baseComp/SelectLevelEnum";
import { ref, computed } from "vue";
import { useDictSelect } from "@/hooks";

defineOptions({
  name: "SelectCtrlWithoutForm",
});

const modelValue = defineModel({
  type: String,
});
const props = defineProps({
  field: {
    type: Object,
    required: true,
  },
  disabled: {
    type: Boolean,
    default: false,
  },
});

//modelValue JSON字符串格式{code:string[],desc:string[]}

const cascaderRef = ref(null);

const innerValue = computed({
  get: () => {
    if (!modelValue.value) return [];
    try {
      const parsed = JSON.parse(modelValue.value);
      return parsed.code || [];
    } catch {
      return [];
    }
  },
  set: (val) => {
    if (!val || val.length === 0) {
      modelValue.value = "";
      return;
    }

    // 获取选中节点的完整数据
    const checkedNodes = cascaderRef.value.getCheckedNodes()[0];
    if (!checkedNodes) return;

    modelValue.value = JSON.stringify({
      code: checkedNodes.pathValues,
      desc: checkedNodes.pathLabels,
    });
  },
});

const sourceType = computed(() => props.field?.selectDatasource?.type);
const sourceCode = computed(() => props.field?.selectDatasource?.code);
const maxLevel = computed(() => {
  return props.field?.selectLevel === SelectLevelEnum.THREE ? 2 : 1;
});

console.log(maxLevel.value);

const { dictOptions, loadCascaderData } = useDictSelect(
  sourceType,
  sourceCode,
  { isCascader: true, maxLevel: maxLevel.value }
);

const cascaderProps = {
  value: "code",
  label: "name",
  children: "children",
  checkStrictly: true,
  lazy: true,
  // 懒加载层级数据
  lazyLoad: loadCascaderData,
};
</script>

<template>
  <el-cascader
    ref="cascaderRef"
    v-model="innerValue"
    :props="cascaderProps"
    style="width: 100%"
    :disabled="disabled"
    :clearable="true"
  />
</template>

<style lang="scss" scoped></style>
