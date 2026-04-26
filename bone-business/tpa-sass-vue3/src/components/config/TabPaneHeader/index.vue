<script setup lang="ts">
defineOptions({
  name: "TabPaneHeader",
});

defineProps({
  process: String,
  item: String,
});
</script>

<template>
  <div class="my-2 ml-2">
    <span class="text-14px text-[var(--el-text-color-secondary)] font-bold">
      正在处理：
    </span>
    <span class="text-16px text-[var(--el-color-primary)] font-bold">
      {{ process }} - {{ item }}
    </span>
  </div>
</template>

<style lang="scss" scoped></style>
