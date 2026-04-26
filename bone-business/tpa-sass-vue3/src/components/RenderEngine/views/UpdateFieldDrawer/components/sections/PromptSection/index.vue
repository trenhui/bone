<script setup>
const config = defineModel("config", {
  type: Object,
  required: true,
});
</script>

<template>
  <div class="mb-6">
    <p
      class="pl-2 mt-1 mb-2 border-l-3 border-l-solid border-l-[var(--el-color-primary)] color-[#606266] font-bold text-base"
    >
      组件提示
    </p>
    <div class="px-2">
      <el-form-item label="提示语">
        <el-input
          v-model="config.prompt"
          placeholder="请输入提示语"
          maxlength="50"
        />
      </el-form-item>
      <slot>
        <el-form-item label="占位文案">
          <el-input
            v-model="config.placeholder"
            placeholder="请输入占位文案"
            maxlength="50"
          />
        </el-form-item>
      </slot>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
