<script setup lang="ts">
import { BaseCompType } from "@/enums";
import SelectDropWithoutForm from "@/components/RenderEngine/base/SelectDropWithoutForm/index.vue";
import SelectCtrlWithoutForm from "@/components/RenderEngine/base/SelectCtrlWithoutForm/index.vue";
defineOptions({
  name: "LinkageRuleCardFixedValue",
});

const modelValue = defineModel<string>({
  type: String,
});
const props = defineProps({
  componentType: {
    type: String,
    required: true,
  },
  selectDatasource: {
    type: Object,
    required: true,
  },
  selectLevel: {
    type: Number,
    default: 2,
  },
  disabled: {
    type: Boolean,
    default: false,
  },
});

const isSelectDrop = computed(
  () => props.componentType === BaseCompType.SelectDrop
);
const isSelectCtrl = computed(
  () => props.componentType === BaseCompType.SelectCtrl
);
</script>

<template>
  <div class="w-full">
    <!-- SelectDrop类型 -->
    <SelectDropWithoutForm
      v-if="isSelectDrop"
      v-model="modelValue"
      :field="{
        selectDatasource: selectDatasource,
      }"
      format="string"
      style="width: 100%"
      :disabled="disabled"
    />

    <!-- SelectCtrl类型 -->
    <SelectCtrlWithoutForm
      v-else-if="isSelectCtrl"
      v-model="modelValue"
      :field="{
        selectDatasource: selectDatasource,
        selectLevel: selectLevel,
      }"
      style="width: 100%"
      :disabled="disabled"
    />

    <!-- 其他类型 -->
    <el-input
      v-else
      v-model="modelValue"
      placeholder="固定值"
      :disabled="disabled"
    />
  </div>
</template>

<style lang="scss" scoped></style>
