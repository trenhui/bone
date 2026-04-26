<script setup lang="ts">
import { CodeEditor } from "monaco-editor-vue3";
import { Condition } from "@/api/jobConfig/flow";
const dialogVisible = ref(false);

const condition = ref<Condition>();
const script = ref("");

const open = (c: Condition) => {
  dialogVisible.value = true;
  condition.value = c;
  script.value = c.script;
};

const handleClose = () => {
  dialogVisible.value = false;
  condition.value = undefined;
  script.value = "";
};

const handleConfirm = async () => {
  condition.value!.script = script.value;
  handleClose();
};

defineExpose({
  open,
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :title="`编辑 ${condition?.name} 脚本`"
    width="45%"
    @close="handleClose"
    :close-on-click-modal="false"
  >
    <div>
      <CodeEditor
        v-model:value="script"
        :key="condition?.name"
        language="java"
        theme="vs-dark"
        height="500px"
      />
    </div>
    <template #footer>
      <span class="footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确定</el-button>
      </span>
    </template>
  </el-dialog>
</template>
