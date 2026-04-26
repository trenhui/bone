<script setup lang="ts">
import { getValueByJsonPath } from "@/utils/jsonpathUtils";
defineOptions({
  name: "HeadDescription",
});

defineProps<{
  fieldList: any[];
  data: any;
}>();
</script>

<template>
  <div class="grid-layout w-full">
    <div class="grid-item" v-for="item in fieldList" :key="item.id">
      <span class="item-label">{{ item.bizName }}</span>
      <span class="item-value">
        {{ getValueByJsonPath(data, item.dataBinding) }}
      </span>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.grid-layout {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
}

.grid-item {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.item-label {
  font-size: 14px;
  font-weight: bold;
  color: var(--el-text-color-regular);
}

.item-value {
  margin-left: 12px;
  font-size: 14px;
  color: var(--el-text-color-regular);
}
</style>
