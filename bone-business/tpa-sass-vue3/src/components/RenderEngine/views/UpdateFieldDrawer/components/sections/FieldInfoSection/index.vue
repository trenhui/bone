<script setup>
import { getBaseCompTypeName } from "@/enums/baseComp/BaseCompEnum";
const config = defineModel("config", {
  type: Object,
  required: true,
});

const showNameRule = [
  {
    required: true,
    message: "请输入展示标题",
    trigger: "blur",
  },
];
</script>

<template>
  <div class="mb-6">
    <p
      class="pl-2 mt-1 mb-2 border-l-3 border-l-solid border-l-[var(--el-color-primary)] color-[#606266] font-bold text-base"
    >
      字段信息
    </p>
    <div class="px-2">
      <el-form-item label="业务字段">
        <span class="text-base font-bold color-[#606266]">
          {{ config.name }}
        </span>
      </el-form-item>
      <el-form-item label="组件类型">
        <span>{{ getBaseCompTypeName(config.type) }}</span>
      </el-form-item>
      <el-form-item label="组件标识">
        <span>{{ config.code }}</span>
      </el-form-item>
      <el-form-item
        label="展示标题"
        required
        prop="showName"
        :rules="showNameRule"
      >
        <el-input v-model="config.showName" maxlength="20" />
      </el-form-item>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
