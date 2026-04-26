<script setup lang="ts">
import ProcessPageAPI, {
  PrecheckDetailConfig,
  UpdatePrecheckDetailPage,
} from "@/api/processPage";
import FieldFormatList from "@/components/config/FieldFormatList/index.vue";
import ConfigBizFieldDialog from "@/components/config/ConfigBizFieldDialog/index.vue";
import clearIcon from "@/assets/icons/clear.png";
import unclearIcon from "@/assets/icons/unclear.png";
import incompleteIcon from "@/assets/icons/incomplete.png";
import { SHORT_CUT_LIST } from "@/constants";
defineOptions({
  name: "CommonPrecheckDetailConfig",
});

const props = defineProps<{
  bizIdentityCode?: string;
}>();

const imageQualityList = [
  { name: "清晰", code: 1, icon: clearIcon },
  { name: "影像件不清晰", code: 2, icon: unclearIcon },
  { name: "影像件不完整", code: 3, icon: incompleteIcon },
];

const detailConfig = ref<PrecheckDetailConfig>({
  id: "",
  name: "",
  description: "",
  modelNameList: [],
  pageHead: {
    enablePageHead: 0,
    pageHeadField: [],
    pageHeadModelId: "",
  },
  openDetail: 0,
  specification: "",
  tip: "",
  imageQuality: 0,
  imageType: 0,
});

const initDetailConfig = async () => {
  detailConfig.value = await ProcessPageAPI.getPrecheckDetailPage(
    props.bizIdentityCode || ""
  );
};

onMounted(() => {
  initDetailConfig();
});

/** 页头业务信息逻辑 */
const updateHeadDialog = ref<{
  visible: boolean;
  params: {
    modelId: string;
    selectedFields: any[];
  };
  onClose: () => void;
  onConfirm: (bizFieldList: any[]) => void;
}>({
  visible: false,
  params: {
    modelId: "",
    selectedFields: [],
  },
  onClose: () => {
    updateHeadDialog.value.visible = false;
    updateHeadDialog.value.params = {
      modelId: "",
      selectedFields: [],
    };
  },
  onConfirm: (bizFieldList: any[]) => {
    detailConfig.value.pageHead.pageHeadField = bizFieldList;
  },
});
const handleConfigPageHead = () => {
  if (!detailConfig.value.pageHead.pageHeadModelId) {
    ElMessage.warning("该页面暂不支持页头信息");
    detailConfig.value.pageHead.enablePageHead = 0;
    return;
  }

  updateHeadDialog.value.params.modelId =
    detailConfig.value.pageHead.pageHeadModelId;
  updateHeadDialog.value.params.selectedFields =
    detailConfig.value.pageHead.pageHeadField || [];
  updateHeadDialog.value.visible = true;
};

const loading = ref(false);
const handleSave = async () => {
  try {
    loading.value = true;
    const data: UpdatePrecheckDetailPage = {
      id: detailConfig.value.id,
      description: detailConfig.value.description,
      enablePageHead: detailConfig.value.pageHead.enablePageHead,
      pageHeadFieldList: detailConfig.value.pageHead.pageHeadField || [],
      openDetail: detailConfig.value.openDetail,
      specification: detailConfig.value.specification,
      tip: detailConfig.value.tip,
      imageQuality: detailConfig.value.imageQuality,
      imageType: detailConfig.value.imageType,
    };
    await ProcessPageAPI.updatePrecheckDetailPage(data);
    ElMessage.success("保存成功");
    initDetailConfig();
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <div>
    <el-card shadow="never">
      <el-form label-width="100px">
        <el-form-item label="数据模型">
          {{ detailConfig.modelNameList?.join("、") }}
        </el-form-item>
        <el-form-item label="页面名称">初审列表页</el-form-item>
        <el-form-item label="页面介绍说明" class="w-[60%]">
          <el-input v-model="detailConfig.description" />
        </el-form-item>
        <el-form-item label="页头业务信息">
          <div class="flex items-center">
            <el-switch
              v-model="detailConfig.pageHead.enablePageHead"
              :active-value="1"
              :inactive-value="0"
            />
            <el-button
              v-if="detailConfig.pageHead.enablePageHead"
              class="ml-5"
              type="primary"
              link
              @click="handleConfigPageHead"
            >
              配置业务字段
            </el-button>
          </div>
        </el-form-item>

        <el-form-item v-if="detailConfig.pageHead.pageHeadField?.length">
          <field-format-list
            :field-list="detailConfig.pageHead.pageHeadField || []"
          />
        </el-form-item>

        <div class="text-sm text-[var(--el-color-primary)] mt-6 mb-2">
          内容主体信息
        </div>
        <el-form-item label="打开详情弹窗">
          <el-switch
            v-model="detailConfig.openDetail"
            :active-value="1"
            :inactive-value="0"
          />
        </el-form-item>
        <el-form-item label="初审标准规范" class="w-[60%]">
          <div class="line-height-32px">
            默认使用普康初审标准规范，若在具体主体自定义了初审规范内容，以各主体内容优先展示。
          </div>
          <el-input
            type="textarea"
            :rows="4"
            v-model="detailConfig.specification"
          />
        </el-form-item>

        <div class="text-sm text-[var(--el-color-primary)] mt-5 mb-2">
          影像分类规则
        </div>

        <el-form-item label="清晰提示文案" class="w-[60%]">
          <el-input v-model="detailConfig.tip" />
        </el-form-item>

        <el-form-item label="影像清晰码值" class="w-[60%]">
          <el-table :data="imageQualityList" border>
            <el-table-column label="类型" prop="name" align="center" />
            <el-table-column label="码值" prop="code" align="center" />
            <el-table-column label="图标" prop="icon" align="center">
              <template #default="{ row }">
                <img :src="row.icon" class="w-4 h-4" />
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>

        <el-form-item label="影像分类码值">
          默认使用普康影像分类标准码值，若在具体主体自定义分类并完成映射，则以各主体分类为准。
          <el-button type="primary" link>查看普康标准分类码值</el-button>
        </el-form-item>

        <el-form-item label="操作快捷键" class="w-[60%]">
          <el-table :data="SHORT_CUT_LIST" border stripe>
            <el-table-column label="动作" prop="action" align="center" />
            <el-table-column label="快捷键" prop="shortcut" align="center" />
          </el-table>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="flex justify-end">
          <el-button type="danger" @click="handleSave" :loading="loading">
            保存
          </el-button>
        </div>
      </template>
    </el-card>

    <ConfigBizFieldDialog
      v-model="updateHeadDialog.visible"
      v-bind="updateHeadDialog.params"
      @confirm="updateHeadDialog.onConfirm"
      @close="updateHeadDialog.onClose"
    />
  </div>
</template>

<style lang="scss" scoped></style>
