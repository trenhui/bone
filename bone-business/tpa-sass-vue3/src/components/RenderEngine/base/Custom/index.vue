<script setup lang="ts">
import RelateLiability from "./RelateLiability/index.vue";
import RelateLiabilityView from "./RelateLiability/view.vue";
import { createProps, createEmits } from "./custom";
defineOptions({
  name: "PKCustom",
});

const props = defineProps(createProps());
const emits = defineEmits(createEmits());

const componentType = computed(() => {
  if (props.code === "relateLiability") {
    return props.isCellView ? RelateLiabilityView : RelateLiability;
  }
  return null;
});
</script>

<template>
  <component
    :is="componentType"
    v-bind="props"
    @change="emits('change', $event)"
    @blur="emits('blur', $event)"
  />
</template>

<style lang="scss" scoped></style>
