<script setup>
import { getAlignmentTypeLabel } from "@/enums/baseComp/AlignmentTypeEnum";

const config = defineModel("config", {
  type: Object,
  required: true,
});

const isTable = inject("isTable");

const displayWidth = computed({
  get() {
    return config.value.width < 50 ? 100 : config.value.width;
  },
  set(newVal) {
    config.value.width = newVal;
  },
});
</script>

<template>
  <div class="mb-6">
    <p
      class="pl-2 mt-1 mb-2 border-l-3 border-l-solid border-l-[var(--el-color-primary)] color-[#606266] font-bold text-base"
    >
      基础属性
    </p>
    <div class="px-2">
      <el-form-item label="对齐方式">
        <span>{{ getAlignmentTypeLabel(config.alignment) || "左对齐" }}</span>
      </el-form-item>
      <el-form-item label="宽度">
        <el-input-number
          v-model="displayWidth"
          controls-position="right"
          min="1"
          :disabled="!isTable"
        />
      </el-form-item>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
