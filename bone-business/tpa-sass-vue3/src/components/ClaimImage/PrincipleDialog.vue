<script setup lang="ts">
defineOptions({
  name: "PrincipleDialog",
});

const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  specification: string;
}>();

const onClose = () => {
  dialogVisible.value = false;
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="onClose"
    title="初审规范"
    width="40%"
  >
    <div class="content">
      <div v-html="specification"></div>
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped>
.content {
  min-height: 150px;
  max-height: 300px;
  padding: 10px;
  overflow-y: auto;
  white-space: pre-wrap;
}
</style>
