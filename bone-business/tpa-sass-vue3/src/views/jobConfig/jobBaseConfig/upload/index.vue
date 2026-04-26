<script setup lang="ts">
import ConfigureImgDocUploadDialog from "@/components/RenderEngine/views/ConfigureImgDocUploadDialog/index.vue";
import ConfigureDataUploadDialog from "@/components/RenderEngine/views/ConfigureDataUploadDialog/index.vue";
import ConfigureImageUploadDialog from "@/components/RenderEngine/views/ConfigureImageUploadDialog/index.vue";
import {
  UploadTypeEnum,
  getUploadTypeLabel,
} from "@/enums/upload/UploadTypeEnum";

import UploadAPI from "@/api/upload";
defineOptions({
  name: "JobBaseConfigUpload",
});

const tableLoading = ref(false);
const uploadList = ref<any>([]);

const getUploadList = async () => {
  tableLoading.value = true;
  try {
    uploadList.value = await UploadAPI.getJobUploadComponentList();
  } catch (error) {
    console.error(error);
  } finally {
    tableLoading.value = false;
  }
};

onMounted(() => {
  getUploadList();
});

const configureUploadDialog = ref({
  visible: false,
  params: {
    id: "",
    dataType: -1,
    pageName: "",
  },
  onClose: (isChange = false) => {
    configureUploadDialog.value.visible = false;
    configureUploadDialog.value.params = {
      id: "",
      dataType: -1,
      pageName: "",
    };

    if (isChange) {
      getUploadList();
    }
  },
});

const handleEdit = (row: any) => {
  configureUploadDialog.value.params = {
    id: row.id,
    dataType: row.dataType,
    pageName: row.applicablePage,
  };
  configureUploadDialog.value.visible = true;
};
</script>

<template>
  <div>
    <div class="flex justify-end items-center mb-2">
      <el-button type="primary">新增导入组件</el-button>
    </div>
    <el-table v-loading="tableLoading" :data="uploadList" stripe border>
      <el-table-column type="index" label="序号" align="center" width="80" />
      <el-table-column label="适用页面" align="center" prop="applicablePage" />
      <el-table-column label="导入标题名称" align="center" prop="title" />
      <el-table-column label="导入类型" align="center">
        <template #default="{ row }">
          {{ getUploadTypeLabel(row.dataType) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">
            编辑
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <configure-img-doc-upload-dialog
      v-if="configureUploadDialog.params.dataType === UploadTypeEnum.IMG_DOC"
      v-model="configureUploadDialog.visible"
      v-bind="configureUploadDialog.params"
      @close="configureUploadDialog.onClose"
    />
    <configure-data-upload-dialog
      v-else-if="configureUploadDialog.params.dataType === UploadTypeEnum.DATA"
      v-model="configureUploadDialog.visible"
      v-bind="configureUploadDialog.params"
      @close="configureUploadDialog.onClose"
    />
    <configure-image-upload-dialog
      v-else-if="configureUploadDialog.params.dataType === UploadTypeEnum.IMG"
      v-model="configureUploadDialog.visible"
      v-bind="configureUploadDialog.params"
      @close="configureUploadDialog.onClose"
    />
  </div>
</template>

<style lang="scss" scoped></style>
