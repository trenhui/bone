<script setup></script>

<template>
  <div class="mb-6">
    <p
      class="pl-2 mt-1 mb-2 border-l-3 border-l-solid border-l-[var(--el-color-primary)] color-[#606266] font-bold text-base"
    >
      组件属性
    </p>
    <div class="px-2">
      <slot>
        <div class="text-center text-gray-500">该组件暂无专属属性</div>
      </slot>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
