<script setup lang="ts">
defineOptions({
  name: "TableContainer",
});

defineProps<{
  headers: string[];
  items: any[];
}>();

const model = defineModel<any>({ default: () => ({}), required: true });
</script>

<template>
  <div class="table-container">
    <div class="row">
      <div
        class="header label is-last:border-right-0"
        v-for="header in headers"
        :key="header"
      >
        {{ header }}
      </div>
    </div>

    <div class="row" v-for="item in items" :key="item.id">
      <div class="label">{{ item.label }}</div>
      <div class="value">
        <el-select
          v-model="model[item.key]"
          placeholder="请选择"
          style="width: 100%"
        >
          <el-option
            v-for="option in item.options"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.table-container {
  display: grid;
  grid-template-columns: 250px 1fr;
  font-size: 14px;
  border-collapse: collapse;
  border: 1px solid #dcdfe6;
  border-bottom: none;
}

.row {
  display: contents;
}

.header {
  background-color: var(--el-fill-color);
}

.label,
.value {
  display: flex;
  align-items: center; /* 垂直居中 */
  justify-content: center; /* 水平居中 */
}

.label {
  padding: 12px;
  border-right: 1px solid #dcdfe6;
  border-bottom: 1px solid #dcdfe6;
}

.value {
  padding: 12px;
  border-bottom: 1px solid #dcdfe6;
}
</style>
