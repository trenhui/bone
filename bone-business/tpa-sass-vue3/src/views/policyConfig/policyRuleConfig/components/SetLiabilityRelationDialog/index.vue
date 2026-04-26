<script setup lang="ts">
import ShareRelation from "./shareRelation/index.vue";
import NextRelation from "./nextRelation/index.vue";
defineOptions({
  name: "SetLiabilityRelationDialog",
});

const emits = defineEmits(["close", "confirm"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  policyNo: string;
}>();

const handleClose = () => {
  dialogVisible.value = false;
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    append-to-body
    :close-on-click-modal="false"
    title="设置责任关系"
    width="60%"
  >
    <div class="dialog-content">
      <el-tabs type="card">
        <el-tab-pane label="共保关系">
          <share-relation :policy-no="policyNo" />
        </el-tab-pane>
        <el-tab-pane lazy label="先后赔付关系">
          <next-relation :policy-no="policyNo" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <template #footer>
      <span class="footer">
        <el-button @click="handleClose">关闭</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  max-height: 60vh;
  padding: 20px;
  overflow-y: auto;
}
</style>
