<script setup>
defineOptions({
  name: "BaseInfoSection",
});

const tableConfig = defineModel("tableConfig", {
  type: Object,
  required: true,
});
</script>

<template>
  <div class="section">
    <p class="section-title">模型信息</p>
    <div class="section-content">
      <el-form-item label="模型类别">
        <span class="main-text">{{ tableConfig.modelType }}</span>
      </el-form-item>
      <el-form-item label="展示形式">
        <span>区块表格</span>
      </el-form-item>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.main-text {
  font-size: 16px;
  font-weight: bold;
  color: #606266;
}
</style>
