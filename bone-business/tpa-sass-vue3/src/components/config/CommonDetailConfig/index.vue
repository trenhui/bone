<script setup lang="ts">
import ProcessPageAPI from "@/api/processPage";
import ModelAPI from "@/api/model";
import PageAPI from "@/api/page";
import { ModelStatusEnum } from "@/enums/ModelStatusEnum";
import { DisplayModeEnum } from "@/enums";
import { PageCodeEnum } from "@/enums/PageCodeEnum";
import ConfigBizFieldDialog from "../ConfigBizFieldDialog/index.vue";
import FieldAPI from "@/api/field";

const router = useRouter();

const props = defineProps({
  // 获取流程页面的code
  code: {
    type: String,
    default: "",
  },
  // 配置页面时用到的code
  pageCode: {
    type: String,
    default: PageCodeEnum.entry,
  },
  pageName: {
    type: String,
    default: "",
  },
  // 目标页面路由名称
  targetPageName: {
    type: String,
    default: "ClaimDetail",
  },
  // 是否新标签页打开
  isNewTab: {
    type: Boolean,
    default: false,
  },
  // 业务身份编码
  bizIdentityCode: {
    type: String,
    default: "",
  },
});

const tableLoading = ref(false);
const modelList = ref<any>([]);
const detailConfig = ref<any>({});

const initDetailConfig = async () => {
  // 仅用于获取签收详情页面的配置
  detailConfig.value = await ProcessPageAPI.getProcessDetailPage(
    props.bizIdentityCode
  );
};

onMounted(() => {
  if (props.pageCode === PageCodeEnum.newSign) {
    initDetailConfig();
  }
});

const enablePageHead = ref(0);
const pageHeadField = ref<any>([]);
const pageId = ref("");
const pageHeadModelId = ref("");
const getPageHeadEnableInfo = async () => {
  const res = await PageAPI.getPageHeadEnable(
    props.pageCode,
    props.bizIdentityCode
  );
  enablePageHead.value = res.businessFieldEnabled;
  pageId.value = res.id;

  const res2 = await ModelAPI.getHeadModel(
    props.pageCode,
    props.bizIdentityCode
  );
  pageHeadModelId.value = res2.modelId;

  const res3 = await FieldAPI.getFieldListByDisplay(pageHeadModelId.value);
  pageHeadField.value = res3.map((item: any) => ({
    fieldId: item.id,
    bizName: item.fieldName,
  }));
};

const initPageModelList = async () => {
  tableLoading.value = true;

  try {
    modelList.value = await ModelAPI.getPageModelList(
      props.pageCode,
      props.bizIdentityCode
    );
  } catch (error) {
    console.error(error);
  } finally {
    tableLoading.value = false;
  }
};

watch(
  () => props.pageCode,
  () => {
    if (props.pageCode) {
      initPageModelList();
      getPageHeadEnableInfo();
    }
  },
  { immediate: true }
);

// 配置业务字段
const handleConfig = () => {
  if (!props.pageCode) {
    ElMessage.error("未找到对应的页面");
    return;
  }

  const route = router.resolve({
    name: props.targetPageName, // 目标页面的路由名称
    query: {
      pageCode: props.pageCode,
      displayMode: DisplayModeEnum.CONFIG,
      bizIdentityCode: props.bizIdentityCode,
    },
  });

  if (props.isNewTab) {
    window.open(route.href, "_blank");
  } else {
    router.push(route);
  }
};

// 编辑启用状态
const handleStatusChange = async (row: any) => {
  try {
    await ModelAPI.updateModelStatus({
      modelId: row.modelId,
      status: row.status,
    });
    ElMessage.success("更新成功");
    initPageModelList();
  } catch (error: any) {
    console.error(error.message);
  }
};

// 批量编辑模型业务字段
const handleUpdate = (row: any) => {
  updateModelFieldsDrawer.value.visible = true;
  updateModelFieldsDrawer.value.params.id = row.modelId;
};
const updateModelFieldsDrawer = ref({
  visible: false,
  params: { id: "" },
  onClose: () => {
    updateModelFieldsDrawer.value.params.id = "";
  },
});

const handleChangeEnablePageHead = async (newValue: any) => {
  enablePageHead.value = newValue;
  await PageAPI.updatePageHeadEnable(pageId.value, enablePageHead.value);
};

const updateHeadDialog = ref({
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
  onConfirm: async (bizFieldList: any[]) => {
    pageHeadField.value = bizFieldList;
    const data = {
      modelId: pageHeadModelId.value,
      fieldIdList: bizFieldList.map((item: any) => item.fieldId),
    };
    await FieldAPI.updateFieldDisplay(data);
    ElMessage.success("保存成功");
    getPageHeadEnableInfo();
  },
});
const handleConfigPageHead = () => {
  updateHeadDialog.value.visible = true;
  updateHeadDialog.value.params.modelId = pageHeadModelId.value;
  updateHeadDialog.value.params.selectedFields = pageHeadField.value;
};
</script>

<template>
  <el-card shadow="never">
    <el-form-item label="页面名称">{{ pageName }}</el-form-item>
    <el-form-item label="页头业务信息">
      <div class="flex items-center">
        <el-switch
          v-model="enablePageHead"
          :active-value="1"
          :inactive-value="0"
          @change="handleChangeEnablePageHead"
        />
        <el-button
          v-if="enablePageHead"
          class="ml-5"
          type="primary"
          link
          @click="handleConfigPageHead"
        >
          配置业务字段
        </el-button>
      </div>
    </el-form-item>

    <el-form-item v-if="pageHeadField?.length && enablePageHead">
      <field-format-list :field-list="pageHeadField || []" />
    </el-form-item>
    <el-form-item label="业务字段">
      <el-button type="default" size="small" @click="handleConfig">
        配置业务字段
      </el-button>
    </el-form-item>
    <div class="my-3">
      <el-table v-loading="tableLoading" :data="modelList" border>
        <el-table-column label="数据模型类别" align="center" prop="blockName" />
        <el-table-column label="数据模型名称" align="center" prop="modelName" />
        <el-table-column label="模型展示形式" align="center">
          <template #default="{ row }">
            {{ row.presentationFormat == 0 ? "区块字段" : "区块表格" }}
          </template>
        </el-table-column>
        <el-table-column label="使用状态" align="center" prop="status">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="ModelStatusEnum.active"
              :inactive-value="ModelStatusEnum.inactive"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="业务字段" align="center" prop="">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleUpdate(row)">
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <UpdateModelFieldsDrawer
      v-model="updateModelFieldsDrawer.visible"
      v-bind="updateModelFieldsDrawer.params"
      @close="updateModelFieldsDrawer.onClose"
    />

    <ConfigBizFieldDialog
      v-model="updateHeadDialog.visible"
      v-bind="updateHeadDialog.params"
      @confirm="updateHeadDialog.onConfirm"
      @close="updateHeadDialog.onClose"
    />
  </el-card>
</template>

<style lang="scss" scoped>
:deep(.el-form-item) {
  margin-bottom: 10px;
}
</style>
