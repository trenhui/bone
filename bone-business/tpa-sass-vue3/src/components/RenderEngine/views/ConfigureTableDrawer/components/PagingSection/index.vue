<script>
const options = [
  { label: "是", value: 1 },
  { label: "否", value: 0 },
];
const defaultPageSizeOptions = [
  { label: "10", value: 10 },
  { label: "20", value: 20 },
  { label: "30", value: 30 },
  { label: "50", value: 50 },
];
</script>

<script setup>
defineOptions({
  name: "PagingSection",
});

const tableConfig = defineModel("tableConfig", {
  type: Object,
  required: true,
});
</script>

<template>
  <div class="section">
    <p class="section-title">分页器</p>
    <div class="section-content">
      <el-form-item label="显示分页">
        <el-select v-model="tableConfig.paginationOpened" placeholder="请选择">
          <el-option
            v-for="item in options"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="默认行数">
        <el-select v-model="tableConfig.defaultPageSize" placeholder="请选择">
          <el-option
            v-for="item in defaultPageSizeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="显示总条数">
        <el-select v-model="tableConfig.displayTotalSize" placeholder="请选择">
          <el-option
            v-for="item in options"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="填充屏幕">
        <el-select v-model="tableConfig.fillScreen" placeholder="请选择">
          <el-option
            v-for="item in options"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
