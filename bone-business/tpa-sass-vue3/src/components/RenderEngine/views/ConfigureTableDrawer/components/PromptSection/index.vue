<script setup>
defineOptions({
  name: "PromptSection",
});

const tableConfig = defineModel("tableConfig", {
  type: Object,
  required: true,
});
</script>

<template>
  <div class="section">
    <p class="section-title">容器提示</p>
    <div class="section-content">
      <el-form-item label="提示语">
        <el-input
          v-model="tableConfig.prompt"
          placeholder="请输入提示语"
          maxlength="50"
        />
      </el-form-item>

      <el-form-item label="空数据提示">
        <el-input
          v-model="tableConfig.emptyPrompt"
          placeholder="请输入空数据提示"
          maxlength="50"
        />
      </el-form-item>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
