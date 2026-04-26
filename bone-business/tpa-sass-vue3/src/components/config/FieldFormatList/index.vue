<script setup lang="ts">
import { isEmpty } from "lodash-es";

defineOptions({
  name: "FieldFormatList",
});

const props = defineProps<{
  fieldList: any[];
}>();

//每行五个元素
const columncount = 5;
const rowcount = computed(() => {
  if (isEmpty(props.fieldList)) return 0;
  return Math.ceil(props.fieldList.length / columncount);
});
</script>

<template>
  <div v-if="fieldList && fieldList.length > 0" class="grid-container">
    <div class="grid-item" v-for="item in fieldList" :key="item.id">
      {{ item.bizName }}
    </div>
  </div>
</template>

<style lang="scss" scoped>
.grid-container {
  display: grid;
  grid-template-rows: repeat(v-bind(rowcount), auto); /* 行高根据内容自动调整 */
  grid-template-columns: repeat(v-bind(columncount), 1fr); /* 默认5列布局 */
}

.grid-item {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-right: 25px;
  padding-left: 25px;
  margin: -0.5px; // 使用负margin来解决边框重叠问题
  color: var(--el-text-color-regular);
  border: 1px solid var(--el-border-color-darker);
}
</style>
