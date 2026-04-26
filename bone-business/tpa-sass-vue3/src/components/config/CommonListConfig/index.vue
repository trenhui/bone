<script setup lang="ts">
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import ProcessPageAPI, {
  ProcessListConfig,
  UpdateProcessListConfig,
  TabCondition,
} from "@/api/processPage";
import { isEmpty } from "lodash-es";
import ConfigBizFieldDialog from "../ConfigBizFieldDialog/index.vue";
import ConfigSearchFieldDialog from "../ConfigSearchFieldDialog/index.vue";
import ConfigTabDialog from "../ConfigTabDialog/index.vue";

defineOptions({
  name: "CommonListConfig",
});

const props = defineProps<{
  code: string;
}>();

const form = ref<UpdateProcessListConfig>({
  pageId: "",
  desc: "",
  enablePageHead: 0,
  pageHeadFieldList: [],
  enableTab: 0,
  tabConditionList: [],
  enableSearch: 0,
  searchFieldList: [],
  dataRange: 1,
  tableId: "",
});

const listConfig = ref<ProcessListConfig>({
  modelNameList: [],
  pageBody: {},
  pageBaseInfo: { code: "", name: "", description: "", id: "" },
  pageHead: { enablePageHead: 0, pageHeadField: [], pageHeadModelId: "" },
  tableId: "",
  dataRange: 1,
  enableTab: 0,
  tabConditionList: [],
  uploadComponentList: [],
});

const initConfig = () => {
  ProcessPageAPI.getProcessListPage(props.code).then((res) => {
    listConfig.value = res;
    form.value.pageId = listConfig.value.pageBaseInfo.id;
    form.value.desc = listConfig.value.pageBaseInfo.description || "";
    form.value.enablePageHead = listConfig.value.pageHead.enablePageHead;
    form.value.pageHeadFieldList =
      listConfig.value.pageHead.pageHeadField || [];
    form.value.enableTab = listConfig.value.enableTab;
    form.value.tabConditionList =
      listConfig.value.tabConditionList?.map((item) => ({
        ...item,
        fieldId: item.field?.id || "",
      })) || [];
    form.value.enableSearch = listConfig.value.pageBody[0]?.enableSearch;
    form.value.searchFieldList =
      listConfig.value.pageBody[0].searchFieldList || [];
    form.value.dataRange = listConfig.value.dataRange;
    form.value.tableId = listConfig.value.tableId;
  });
};

onMounted(() => {
  initConfig();
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
    form.value.pageHeadFieldList = bizFieldList;
  },
});
const handleConfigPageHead = () => {
  if (!listConfig.value.pageHead.pageHeadModelId) {
    ElMessage.warning("该页面暂不支持页头信息");
    form.value.enablePageHead = 0;
    return;
  }

  updateHeadDialog.value.params.modelId =
    listConfig.value.pageHead.pageHeadModelId;
  updateHeadDialog.value.params.selectedFields =
    form.value.pageHeadFieldList || [];
  updateHeadDialog.value.visible = true;
};

/** 搜索栏逻辑 */
const updateSearchDialog = ref<{
  visible: boolean;
  params: {
    tableId: string;
    selectedFields: any[];
  };
  onClose: () => void;
  onConfirm: (searchFieldList: any[]) => void;
}>({
  visible: false,
  params: {
    tableId: "",
    selectedFields: [],
  },
  onClose: () => {
    updateSearchDialog.value.visible = false;
    updateSearchDialog.value.params = {
      tableId: "",
      selectedFields: [],
    };
  },
  onConfirm: (searchFieldList: any[]) => {
    form.value.searchFieldList = searchFieldList;
  },
});
const handleConfigSearch = () => {
  if (!listConfig.value.tableId) {
    ElMessage.warning("该页面暂不支持搜索栏");
    form.value.enableSearch = 0;
    return;
  }

  updateSearchDialog.value.params.tableId = listConfig.value.tableId || "";
  updateSearchDialog.value.params.selectedFields =
    form.value.searchFieldList || [];
  updateSearchDialog.value.visible = true;
};

/** 条件Tab页逻辑 */
const updateTabDialog = ref<{
  visible: boolean;
  params: {
    tableId: string;
    oriTabConditionList: TabCondition[];
  };
  onClose: () => void;
  onConfirm: (tabConditionList: TabCondition[]) => void;
}>({
  visible: false,
  params: {
    tableId: "",
    oriTabConditionList: [],
  },
  onClose: () => {
    updateTabDialog.value.visible = false;
    updateTabDialog.value.params = {
      tableId: "",
      oriTabConditionList: [],
    };
  },
  onConfirm: (tabConditionList: TabCondition[]) => {
    form.value.tabConditionList = tabConditionList;
  },
});
const handleConfigTab = () => {
  if (!listConfig.value.tableId) {
    ElMessage.warning("该页面暂不支持Tab页");
    form.value.enableTab = 0;
    return;
  }

  updateTabDialog.value.params.tableId = listConfig.value.tableId || "";
  updateTabDialog.value.params.oriTabConditionList =
    form.value.tabConditionList || [];
  updateTabDialog.value.visible = true;
};

/** 保存逻辑 */
const confirmLoading = ref(false);
const handleSave = async () => {
  confirmLoading.value = true;
  try {
    await ProcessPageAPI.updateProcessListPage(form.value);

    ElMessage.success("保存成功");
    initConfig();
  } catch (error) {
    console.error(error);
  } finally {
    confirmLoading.value = false;
  }
};
</script>

<template>
  <div>
    <el-card shadow="never">
      <el-form label-width="auto">
        <el-form-item label="数据模型">
          {{ listConfig.modelNameList?.join("、") }}
        </el-form-item>
        <el-form-item label="页面名称">
          {{ listConfig.pageBaseInfo.name }}
        </el-form-item>
        <el-form-item label="页面介绍说明" class="w-[50%]">
          <el-input v-model="form.desc" />
        </el-form-item>
        <el-form-item label="规则设置" class="w-[50%]">
          <el-descriptions class="w-full" :column="1" border size="large">
            <el-descriptions-item
              label="条件Tab"
              align="center"
              class-name="w-[70%]"
            >
              <el-switch
                v-model="form.enableTab"
                :active-value="1"
                :inactive-value="0"
              />
              <el-button
                v-show="form.enableTab"
                class="ml-3"
                type="primary"
                icon="edit"
                link
                @click="handleConfigTab"
              >
                编辑
              </el-button>
            </el-descriptions-item>
            <el-descriptions-item label="数据范围" align="center">
              <el-select v-model="form.dataRange">
                <el-option label="全局" :value="1" />
                <el-option label="根据账号" :value="2" />
                <el-option label="根据字段" :value="3" />
              </el-select>
            </el-descriptions-item>
            <el-descriptions-item label="页头信息" align="center">
              <el-switch
                v-model="form.enablePageHead"
                :active-value="1"
                :inactive-value="0"
              />
              <el-button
                v-show="form.enablePageHead"
                class="ml-3"
                type="primary"
                icon="edit"
                link
                @click="handleConfigPageHead"
              >
                编辑
              </el-button>
            </el-descriptions-item>
            <el-descriptions-item label="搜索栏" align="center">
              <el-switch
                v-model="form.enableSearch"
                :active-value="1"
                :inactive-value="0"
              />
              <el-button
                v-show="form.enableSearch"
                class="ml-3"
                type="primary"
                icon="edit"
                link
                @click="handleConfigSearch"
              >
                编辑
              </el-button>
            </el-descriptions-item>
          </el-descriptions>
        </el-form-item>

        <el-form-item
          label="页头业务信息"
          v-if="form.enablePageHead && !isEmpty(form.pageHeadFieldList)"
        >
          <field-format-list :field-list="form.pageHeadFieldList" />
        </el-form-item>

        <el-form-item
          label="搜索栏"
          v-if="form.enableSearch && !isEmpty(form.searchFieldList)"
        >
          <field-format-list :field-list="form.searchFieldList" />
        </el-form-item>
      </el-form>

      <div class="w-full mt-3">
        <p class="text-14px text-[var(--el-text-color-regular)] font-normal">
          列表信息
        </p>
        <PKRender
          :schema="listConfig.pageBody?.[0]"
          :display-mode="DisplayModeEnum.CONFIG"
          :page-data="{ ...listConfig.pageBaseInfo }"
          @update:schema="initConfig"
        />
      </div>

      <!-- <div class="w-full mt-3" v-if="!isEmpty(listConfig.uploadComponentList)">
        <p class="text-14px text-[var(--el-text-color-regular)] font-normal">
          导入组件
        </p>
        <template v-for="item in listConfig.uploadComponentList" :key="item.id">
          <common-upload-table
            :upload-component="item"
            :page-name="listConfig.pageBaseInfo.name"
            @change="initConfig"
          />
        </template>
      </div> -->

      <template #footer>
        <div class="flex justify-end">
          <el-button type="danger" @click="handleSave">保存</el-button>
        </div>
      </template>
    </el-card>

    <ConfigBizFieldDialog
      v-model="updateHeadDialog.visible"
      v-bind="updateHeadDialog.params"
      @confirm="updateHeadDialog.onConfirm"
      @close="updateHeadDialog.onClose"
    />

    <ConfigSearchFieldDialog
      v-model="updateSearchDialog.visible"
      v-bind="updateSearchDialog.params"
      @confirm="updateSearchDialog.onConfirm"
      @close="updateSearchDialog.onClose"
    />

    <ConfigTabDialog
      v-model="updateTabDialog.visible"
      v-bind="updateTabDialog.params"
      @confirm="updateTabDialog.onConfirm"
      @close="updateTabDialog.onClose"
    />
  </div>
</template>

<style lang="scss" scoped></style>
