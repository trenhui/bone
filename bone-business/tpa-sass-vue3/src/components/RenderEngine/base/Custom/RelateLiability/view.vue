<script setup>
import { createProps } from "../custom";

defineOptions({ name: "RelateLiabilityView" });

const props = defineProps(createProps());

const modelValue = ref([]);

const initModelValue = (val) => {
  modelValue.value = val;
};

watch(
  () => props.targetValue,
  (newVal) => {
    initModelValue(newVal);
  },
  { immediate: true }
);

const getLiabilityShow = (item) => {
  return `${item.active !== "DELETED" ? "" : "已删除"} ${item.name}`;
};
</script>

<template>
  <div class="flex items-center flex-col">
    <div
      class="border border-gray-300 rounded-md px-2 mb-1 flex flex-nowrap"
      v-for="item in modelValue"
      :key="item.uuid"
    >
      <span :class="item.active !== 'DELETED' ? '' : 'text-red-500'">
        {{ getLiabilityShow(item) }}
      </span>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
