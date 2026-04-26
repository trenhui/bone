<script setup lang="ts">
import UploadAPI, {
  UploadDataComponent,
  GroupFieldRule,
  SingleFieldRule,
} from "@/api/upload";
import FieldAPI from "@/api/field";
import {
  UploadTypeEnum,
  getUploadTypeLabel,
} from "@/enums/upload/UploadTypeEnum";
import { UploadModeOptions } from "@/enums/upload/UploadModeEnum";
import { UploadCheckOptions } from "@/enums/upload/UploadCheckEnum";
import { UploadHeadCheckOptions } from "@/enums/upload/UploadHeadCheckEnum";
import SettingGroupFieldRuleDialog from "./components/SettingGroupFieldRuleDialog.vue";
import { isEmpty } from "lodash-es";
defineOptions({
  name: "ConfigureDataUploadDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  id: string;
  pageName?: string;
  onlyRead?: boolean;
}>();

const isOnlyRead = computed(() => props.onlyRead || false);

const selectableFieldList = ref<any>([]);
const uploadComponent = ref<UploadDataComponent>({
  id: "",
  title: "",
  dataType: 0,
  model: {
    modelId: "",
    modelName: "",
  },
  fieldList: [],
  fieldIdList: [],
  singleFieldRuleList: [],
  groupFieldRuleList: [],
  templateFileName: "",
  fileMaxSize: 200,
  fileMaxCount: 2000000,
  fileFormat: "",
  importTypeList: [],
  checkType: 0,
  importDescription: "",
});

/** 获取上传组件配置 */
const getUploadComponent = async () => {
  const res = await UploadAPI.getUploadDataById(props.id);
  uploadComponent.value = res;

  //将导入字段fieldList转成fieldIdList
  if (uploadComponent.value.fieldList) {
    uploadComponent.value.fieldIdList = uploadComponent.value.fieldList.map(
      (item) => item.id
    );
  }

  //将组合规则中的fieldList转成fieldIdList；fieldListA和fieldListB转成fieldIdListA和fieldIdListB
  if (uploadComponent.value.groupFieldRuleList) {
    uploadComponent.value.groupFieldRuleList =
      uploadComponent.value.groupFieldRuleList.map((row) => {
        row.fieldIdList = row.fieldList?.map((item) => item.id);
        row.fieldIdListA = row.fieldListA?.map((item) => item.id);
        row.fieldIdListB = row.fieldListB?.map((item) => item.id);
        return row;
      });
  }

  //获取对应数据模型字段集作为可选择字段
  if (uploadComponent.value.model.modelId) {
    getModelFieldList(uploadComponent.value.model.modelId);
  }
};

/** 获取对应数据模型字段集作为可选择字段 */
const getModelFieldList = async (modelId: string) => {
  const res = (await FieldAPI.getByModelId(modelId)) as any;
  selectableFieldList.value = res.fieldList;
};

const handleFieldChange = (fieldIdList: string[]) => {
  uploadComponent.value.fieldList = fieldIdList.map((id) =>
    selectableFieldList.value.find((item: any) => item.id === id)
  );
};

watch(
  dialogVisible,
  (val) => {
    if (val) {
      getUploadComponent();
    }
  },
  { immediate: true }
);

const formRef = ref();
const confirmLoading = ref(false);
const isChange = ref(false);

const handleClose = () => {
  dialogVisible.value = false;
  emits("close", isChange.value);
  isChange.value = false;
};

/** 单个字段规则设置 */
const handleAddingSingleFieldRule = () => {
  if (isEmpty(uploadComponent.value.singleFieldRuleList)) {
    uploadComponent.value.singleFieldRuleList = [];
  }
  uploadComponent.value.singleFieldRuleList.push({
    fieldId: "",
    required: false,
    unique: false,
    isEditing: true,
  });
};
const handleAddSingleFieldRule = (row: SingleFieldRule) => {
  if (!row.fieldId) {
    ElMessage.warning("请填写完整后确认");
    return;
  }
  row.isEditing = false;
};
const handleCancelSingleFieldRule = (index: number) => {
  uploadComponent.value.singleFieldRuleList.splice(index, 1);
};
const handleDeleteSingleFieldRule = (row: SingleFieldRule) => {
  ElMessageBox.confirm("确定删除该字段规则吗？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    center: true,
  }).then(() => {
    uploadComponent.value.singleFieldRuleList =
      uploadComponent.value.singleFieldRuleList.filter((item) => item !== row);
  });
};

const handleSingleFieldChange = (row: any) => {
  row.bizName = selectableFieldList.value.find(
    (item: any) => item.id === row.fieldId
  )?.bizName;
};

/** 组合字段规则设置 */
const handleAddingGroupFieldRule = () => {
  if (isEmpty(uploadComponent.value.groupFieldRuleList)) {
    uploadComponent.value.groupFieldRuleList = [];
  }
  uploadComponent.value.groupFieldRuleList.push({
    fieldIdList: [],
    fieldIdListA: [],
    fieldIdListB: [],
    unique: false,
    isEditing: true,
  });
};
const handleAddGroupFieldRule = (row: GroupFieldRule) => {
  if (!row.fieldIdList) {
    ElMessage.warning("请填写完整后确认");
    return;
  }
  row.isEditing = false;
};
const handleCancelGroupFieldRule = (index: number) => {
  uploadComponent.value.groupFieldRuleList.splice(index, 1);
};
const handleDeleteGroupFieldRule = (row: GroupFieldRule) => {
  ElMessageBox.confirm("确定删除该组合字段规则吗？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    center: true,
  }).then(() => {
    uploadComponent.value.groupFieldRuleList =
      uploadComponent.value.groupFieldRuleList.filter((item) => item !== row);
  });
};

const handleGroupFieldChange = (row: GroupFieldRule) => {
  row.fieldList = row.fieldIdList?.map((id) =>
    uploadComponent.value.fieldList.find((item) => item.id === id)
  );
};

const settingGroupFieldRuleDialog = ref({
  isVisible: false,
  params: {},
  onClose: () => {
    settingGroupFieldRuleDialog.value.isVisible = false;
    settingGroupFieldRuleDialog.value.params = {};
  },
  onConfirm: ({ fieldIdListA, fieldIdListB, index }: any) => {
    const row = uploadComponent.value.groupFieldRuleList[index];
    row.fieldIdListA = fieldIdListA;
    row.fieldIdListB = fieldIdListB;
    row.fieldListA = fieldIdListA.map((id: string) =>
      uploadComponent.value.fieldList.find((item) => item.id === id)
    );
    row.fieldListB = fieldIdListB.map((id: string) =>
      uploadComponent.value.fieldList.find((item) => item.id === id)
    );
  },
});
const handleSettingGroupFieldRule = (row: GroupFieldRule, index: number) => {
  console.log(row);
  if (!row.fieldIdList || row.fieldIdList.length === 0) {
    ElMessage.warning("请先选择组合字段");
    return;
  }
  settingGroupFieldRuleDialog.value.isVisible = true;
  settingGroupFieldRuleDialog.value.params = {
    index,
    fieldList:
      row.fieldList ||
      row.fieldIdList?.map((id) =>
        uploadComponent.value.fieldList.find((item) => item.id === id)
      ) ||
      [],
    fieldIdListA: row.fieldIdListA,
    fieldIdListB: row.fieldIdListB,
  };
};

/** 获取组合字段规则文本 */
const getGroupFieldRuleText = (row: GroupFieldRule) => {
  if (
    row.fieldListA &&
    row.fieldListB &&
    row.fieldListA.length > 0 &&
    row.fieldListB.length > 0
  ) {
    return `${row.fieldListA
      .map((item: any) => item.bizName)
      .join("，")} 的值相同时，${row.fieldListB
      .map((item: any) => item.bizName)
      .join("，")} 的值必须相同`;
  }
  return "";
};

/** 保存 */
const handleConfirm = useDebounceFn(async () => {
  try {
    const hasEditingRow =
      uploadComponent.value.singleFieldRuleList?.some((row) => row.isEditing) ||
      uploadComponent.value.groupFieldRuleList?.some((row) => row.isEditing);

    if (hasEditingRow) {
      ElMessage.warning("请先完成正在编辑的规则");
      return;
    }

    confirmLoading.value = true;
    await UploadAPI.updateUploadData(uploadComponent.value);
    isChange.value = true;
    ElMessage.success("设置成功");
    handleClose();
  } catch (error) {
    console.error(error);
    confirmLoading.value = false;
  } finally {
    confirmLoading.value = false;
  }
});
</script>

<template>
  <div>
    <el-dialog
      v-model="dialogVisible"
      :before-close="handleClose"
      title="设置导入规则"
      width="50%"
      top="5vh"
    >
      <div class="dialog-content px-4 py-2">
        <el-form ref="formRef" :model="uploadComponent" label-width="auto">
          <el-form-item label="适用场景页面">{{ pageName }}</el-form-item>
          <el-form-item label="导入标题名称" prop="title">
            <el-input v-model="uploadComponent.title" :disabled="isOnlyRead" />
          </el-form-item>
          <el-form-item label="导入类型" prop="dataType">
            {{ getUploadTypeLabel(UploadTypeEnum.DATA) }}
          </el-form-item>
          <p class="text-sm text-[var(--el-color-primary)] mt-5 mb-2">
            配置导入模版
          </p>
          <el-form-item label="数据模型">
            <el-input v-model="uploadComponent.model.modelName" readonly />
          </el-form-item>
          <el-form-item label="设置导入字段" prop="fieldIdList">
            <el-select
              v-model="uploadComponent.fieldIdList"
              multiple
              filterable
              tag-type="primary"
              @change="handleFieldChange"
              :disabled="isOnlyRead"
            >
              <el-option
                v-for="item in selectableFieldList"
                :key="item.id"
                :label="item.bizName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="单个字段规则" prop="singleFieldRuleList">
            <el-table :data="uploadComponent.singleFieldRuleList ?? []" border>
              <el-table-column label="字段名称" align="center">
                <template #default="scope">
                  <span v-if="!scope.row.isEditing">
                    {{ scope.row.bizName }}
                  </span>
                  <el-select
                    v-else
                    v-model="scope.row.fieldId"
                    placeholder="请选择"
                    @change="handleSingleFieldChange(scope.row)"
                  >
                    <el-option
                      v-for="item in uploadComponent.fieldList"
                      :key="item.id"
                      :label="item.bizName"
                      :value="item.id"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="是否必填" align="center">
                <template #default="scope">
                  <span v-if="!scope.row.isEditing">
                    {{ scope.row.required ? "是" : "否" }}
                  </span>
                  <el-switch
                    v-else
                    v-model="scope.row.required"
                    inline-prompt
                    active-text="是"
                    inactive-text="否"
                  />
                </template>
              </el-table-column>
              <el-table-column label="是否唯一" align="center">
                <template #default="scope">
                  <span v-if="!scope.row.isEditing">
                    {{ scope.row.unique ? "是" : "否" }}
                  </span>
                  <el-switch
                    v-else
                    v-model="scope.row.unique"
                    inline-prompt
                    active-text="是"
                    inactive-text="否"
                  />
                </template>
              </el-table-column>
              <el-table-column label="操作" align="center" v-if="!isOnlyRead">
                <template #default="scope">
                  <div v-if="scope.row.isEditing">
                    <el-button
                      type="success"
                      icon="Check"
                      link
                      @click="handleAddSingleFieldRule(scope.row)"
                    >
                      确认
                    </el-button>
                    <el-button
                      type="warning"
                      icon="Close"
                      link
                      @click="handleCancelSingleFieldRule(scope.$index)"
                    >
                      取消
                    </el-button>
                  </div>

                  <el-button
                    v-else
                    type="danger"
                    icon="Delete"
                    link
                    @click="handleDeleteSingleFieldRule(scope.row)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button
              class="mt-2"
              type="primary"
              link
              @click="handleAddingSingleFieldRule"
              v-if="!isOnlyRead"
            >
              添加规则
            </el-button>
          </el-form-item>
          <el-form-item label="组合字段规则" prop="label">
            <el-table :data="uploadComponent.groupFieldRuleList ?? []" border>
              <el-table-column label="组合字段" align="center">
                <template #default="scope">
                  <span v-if="!scope.row.isEditing">
                    {{
                      scope.row.fieldList
                        ?.map((item: any) => item.bizName)
                        .join("，") || "无"
                    }}
                  </span>
                  <el-select
                    v-else
                    v-model="scope.row.fieldIdList"
                    @change="handleGroupFieldChange(scope.row)"
                    multiple
                    filterable
                  >
                    <el-option
                      v-for="item in uploadComponent.fieldList"
                      :key="item.id"
                      :label="item.bizName"
                      :value="item.id"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="文件内组合唯一" align="center">
                <template #default="scope">
                  <span v-if="!scope.row.isEditing">
                    {{ scope.row.unique ? "是" : "否" }}
                  </span>
                  <el-switch
                    v-else
                    v-model="scope.row.unique"
                    inline-prompt
                    active-text="是"
                    inactive-text="否"
                  />
                </template>
              </el-table-column>
              <el-table-column label="组合字段规则" align="center">
                <template #default="scope">
                  <span>
                    {{ getGroupFieldRuleText(scope.row) || "无" }}
                  </span>
                  <el-button
                    type="primary"
                    icon="Edit"
                    link
                    @click="
                      handleSettingGroupFieldRule(scope.row, scope.$index)
                    "
                  >
                    设置
                  </el-button>
                </template>
              </el-table-column>
              <el-table-column label="操作" align="center" v-if="!isOnlyRead">
                <template #default="scope">
                  <div v-if="scope.row.isEditing">
                    <el-button
                      type="success"
                      icon="Check"
                      link
                      @click="handleAddGroupFieldRule(scope.row)"
                    >
                      确认
                    </el-button>
                    <el-button
                      type="warning"
                      icon="Close"
                      link
                      @click="handleCancelGroupFieldRule(scope.$index)"
                    >
                      取消
                    </el-button>
                  </div>

                  <el-button
                    v-else
                    type="danger"
                    icon="Delete"
                    link
                    @click="handleDeleteGroupFieldRule(scope.row)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button
              class="mt-2"
              type="primary"
              link
              @click="handleAddingGroupFieldRule"
              v-if="!isOnlyRead"
            >
              添加规则
            </el-button>
          </el-form-item>
          <el-form-item label="模版文件名" prop="templateFileName">
            <el-input
              v-model="uploadComponent.templateFileName"
              :disabled="isOnlyRead"
            />
          </el-form-item>
          <p class="text-sm text-[var(--el-color-primary)] mt-5 mb-2">
            配置导入校验
          </p>
          <el-form-item label="文件大小上限">100 MB</el-form-item>
          <el-form-item label="文件数据上限">2000000</el-form-item>
          <el-form-item label="限定文件格式">.xls .xlsx</el-form-item>
          <el-form-item label="文件表头校验" prop="headerCheckMode">
            <el-select
              v-model="uploadComponent.headerCheckMode"
              placeholder="请选择"
              disabled
            >
              <el-option
                v-for="item in UploadHeadCheckOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="支持导入方式" prop="importType">
            <el-select
              multiple
              v-model="uploadComponent.importTypeList"
              placeholder="请选择"
              tag-type="primary"
              :disabled="isOnlyRead"
            >
              <el-option
                v-for="item in UploadModeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="导入校验方式" prop="checkType">
            <el-select
              v-model="uploadComponent.checkType"
              placeholder="请选择"
              :disabled="isOnlyRead"
            >
              <el-option
                v-for="item in UploadCheckOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="导入操作说明" prop="importDescription">
            <el-input
              type="textarea"
              :rows="3"
              v-model="uploadComponent.importDescription"
              :disabled="isOnlyRead"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <span class="footer">
          <el-button @click="handleClose">取消</el-button>
          <el-button
            :loading="confirmLoading"
            type="primary"
            @click="handleConfirm"
          >
            确认
          </el-button>
        </span>
      </template>
    </el-dialog>

    <SettingGroupFieldRuleDialog
      v-model="settingGroupFieldRuleDialog.isVisible"
      v-bind="settingGroupFieldRuleDialog.params"
      @close="settingGroupFieldRuleDialog.onClose"
      @confirm="settingGroupFieldRuleDialog.onConfirm"
    />
  </div>
</template>

<style lang="scss" scoped>
.dialog-content {
  max-height: 70vh;
  overflow-y: auto;
}
</style>
