<script setup lang="ts">
import { SHORT_CUT_LIST } from "@/constants";
defineOptions({
  name: "ShortCutDialog",
});

const dialogVisible = defineModel({ type: Boolean, default: false });

const onClose = () => {
  dialogVisible.value = false;
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="onClose"
    title="快捷键说明"
    width="40%"
  >
    <div class="p-2">
      <el-table :data="SHORT_CUT_LIST" border stripe>
        <el-table-column label="动作" prop="action" align="center" />
        <el-table-column label="快捷键" prop="shortcut" align="center" />
      </el-table>
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped></style>
