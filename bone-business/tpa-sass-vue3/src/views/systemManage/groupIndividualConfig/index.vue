<script setup lang="ts">
import PublishDialog from "@/components/config/PublishDialog/index.vue";
import Group from "./group/index.vue";
import Upload from "./upload/index.vue";

defineOptions({
  name: "GroupIndividualConfig",
});

const currentTabPane = ref("GROUP");

const handlePreview = () => {
  console.log("预览");
};
const handleEditRecord = () => {
  console.log("编辑记录");
};

const closePublishDialog = () => {
  publishDialog.value.visible = false;
};
const publishDialog = ref({
  visible: false,
  onClose: closePublishDialog,
});
const handlePublish = () => {
  publishDialog.value.visible = true;
};
</script>

<template>
  <div class="app-container">
    <el-card class="mb-2" shadow="never">
      <div class="flex justify-between items-center">
        <div class="font-bold text-[var(--el-text-color-primary)]">
          团单个险配置
        </div>
        <div>
          <el-button-group>
            <el-button type="default" @click="handleEditRecord">
              编辑记录
            </el-button>
            <el-button type="success" @click="handlePreview">预览</el-button>
            <el-button type="primary" @click="handlePublish">发布</el-button>
          </el-button-group>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-tabs type="border-card" v-model="currentTabPane">
        <el-tab-pane lazy label="团单配置" name="GROUP">
          <group />
        </el-tab-pane>
        <el-tab-pane lazy label="个险配置" name="INDIVIDUAL" />
        <el-tab-pane lazy label="导入配置" name="UPLOAD">
          <upload />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <publish-dialog
      v-model="publishDialog.visible"
      @close="publishDialog.onClose"
    />
  </div>
</template>

<style lang="scss" scoped></style>
