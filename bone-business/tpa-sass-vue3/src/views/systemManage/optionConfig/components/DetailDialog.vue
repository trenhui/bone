<script setup lang="ts">
import OptionConfigAPI, {
  IOptionDetail,
  IOptionValue,
  IOptionValueItem,
} from "@/api/systemManage/optionConfig";
import { cloneDeep } from "lodash-es";
import ImportDialog from "./ImportDialog.vue";
import ExtraPropertyDialog from "./ExtraPropertyDialog.vue";
defineOptions({
  name: "SystemOptionDetailDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  id: string;
}>();

const detail = ref<IOptionDetail>({
  id: "",
  name: "",
  code: "",
  desc: "",
  useScope: 0,
  status: 0,
  latestVersion: "",
  extraPropertyKey: [],
});
const optionValueList = ref<IOptionValue>({
  extraPropertyKey: "",
  optionValueInfo: {
    pageNum: 0,
    pageSize: 0,
    totalSize: 0,
    rows: [],
  },
});
const pageParams = ref({
  pageNum: 1,
  pageSize: 10,
  totalSize: 0,
});

const isChange = ref(false);
const isEditDesc = ref(false);
const searchName = ref("");
const searchValue = ref("");

watch(
  () => dialogVisible.value,
  (value) => {
    if (value) {
      getDetail();
      getOptionValueList();
    }
  },
  { immediate: true }
);

const getDetail = async () => {
  const res = await OptionConfigAPI.getOptionDetail(props.id);
  detail.value = res;
};

const loading = ref(false);
const getOptionValueList = async () => {
  try {
    loading.value = true;

    const res = await OptionConfigAPI.getOptionValueList(
      props.id,
      pageParams.value.pageNum,
      pageParams.value.pageSize,
      searchValue.value,
      searchName.value
    );
    optionValueList.value = res;

    pageParams.value.totalSize = res.optionValueInfo.totalSize;

    if (!res.optionValueInfo.rows) {
      optionValueList.value.optionValueInfo.rows = [];
    }

    if (res.optionValueInfo.rows.length > 0) {
      res.optionValueInfo.rows.forEach((item) => {
        if (item.extraProperty) {
          try {
            item.extraPropertyObj = JSON.parse(item.extraProperty);
          } catch (error) {
            item.extraPropertyObj = {};
          }
        } else {
          item.extraPropertyObj = {};
        }
      });
    }

    if (res.extraPropertyKey) {
      optionValueList.value.extraPropertyKeyArr = JSON.parse(
        res.extraPropertyKey
      );
    }
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const handleConfirmValue = async (row: IOptionValueItem) => {
  if (!row.code?.trim()) {
    ElMessage.warning("选项值标识必填");
    return;
  }
  if (!row.name?.trim()) {
    ElMessage.warning("选项值名称必填");
    return;
  }

  // 添加重复检查
  const isDuplicate = optionValueList.value.optionValueInfo.rows.some(
    (item) => item !== row && (item.code === row.code || item.name === row.name)
  );
  if (isDuplicate) {
    ElMessage.warning("选项值名称或标识存在重复");
    return;
  }

  // 数据规范化
  row.code = row.code.trim();
  row.name = row.name.trim();

  row.extraProperty = JSON.stringify(row.extraPropertyObj);

  if (row.isAdding) {
    await OptionConfigAPI.addOptionValue(props.id, row);
    getOptionValueList();
    ElMessage.success("添加成功");
    return;
  } else if (row.isUpdating) {
    await OptionConfigAPI.updateOptionValue(row);
    getOptionValueList();
    ElMessage.success("更新成功");
  }
};

const handleClose = () => {
  dialogVisible.value = false;
  emits("close", isChange.value);
  isChange.value = false;
  isEditDesc.value = false;
  isAllowEdit.value = false;
};

const handleSizeChange = (size: number) => {
  pageParams.value.pageSize = size;
  getOptionValueList();
};

const handleCurrentChange = (page: number) => {
  pageParams.value.pageNum = page;
  getOptionValueList();
};

const handleDeleteValue = (row: IOptionValueItem) => {
  ElMessageBox.confirm("确定删除该选项值吗？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
  }).then(async () => {
    await OptionConfigAPI.deleteOptionValue(row.id);
    getOptionValueList();
    ElMessage.success("删除成功");
  });
};

const originValues = ref<Record<string, IOptionValueItem>>({});

const handleAddValue = () => {
  optionValueList.value.optionValueInfo.rows.unshift({
    id: "",
    code: "",
    name: "",
    extraProperty: "",
    extraPropertyObj: {},
    status: 1,
    isAdding: true,
  });
};

const handleEditValue = (row: IOptionValueItem) => {
  originValues.value[row.id!] = cloneDeep(row);
  row.isUpdating = true;
};

const handleCancelValue = (row: IOptionValueItem) => {
  if (row.isAdding) {
    optionValueList.value.optionValueInfo.rows.splice(
      optionValueList.value.optionValueInfo.rows.indexOf(row),
      1
    );
    return;
  }
  row.isUpdating = false;
  const originValue = originValues.value[row.id!];
  row.code = originValue.code;
  row.name = originValue.name;
  row.extraProperty = originValue.extraProperty;
  row.extraPropertyObj = originValue.extraPropertyObj;
  row.status = originValue.status;
  delete originValues.value[row.id!];
};

let originDesc = "";
const handleEditDesc = () => {
  originDesc = detail.value.desc;
  isEditDesc.value = true;
};

const handleSaveDesc = async () => {
  try {
    await OptionConfigAPI.updateOptionSet({
      optionSetId: props.id,
      setDesc: detail.value.desc,
    });
    ElMessage.success("更新成功");
    getDetail();
    isEditDesc.value = false;
  } catch (error) {
    console.error(error);
  }
};

const handleCancelDesc = () => {
  detail.value.desc = originDesc;
  isEditDesc.value = false;
};

const handleSearch = () => {
  pageParams.value.pageNum = 1;
  getOptionValueList();
};

const handleReset = () => {
  searchValue.value = "";
  searchName.value = "";
  pageParams.value.pageNum = 1;
  getOptionValueList();
};

const handleAddExtraProperty = () => {
  console.log("添加字段");
};

const isAllowEdit = ref(false);
const handleEdit = () => {
  if (isAllowEdit.value) {
    const hasEdit = optionValueList.value.optionValueInfo.rows.some(
      (item) => item.isAdding || item.isUpdating
    );
    if (hasEdit) {
      ElMessage.warning("请先完成当前编辑");
      return;
    }
  }

  isAllowEdit.value = !isAllowEdit.value;
};

const importDialogRef = ref<InstanceType<typeof ImportDialog>>();
const handleBatchImport = () => {
  importDialogRef.value?.open(detail.value.id, detail.value.name);
};

// 编辑额外字段功能
const extraPropertyDialogRef = ref<InstanceType<typeof ExtraPropertyDialog>>();
const handleEditExtraProperty = () => {
  extraPropertyDialogRef.value?.open(
    detail.value.id,
    detail.value.extraPropertyKey || []
  );
};
const handleExtraPropertySuccess = () => {
  getDetail();
  getOptionValueList();
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    title="选项集详情"
    width="60%"
    top="5vh"
    destroy-on-close
    :close-on-click-modal="false"
  >
    <div class="dialog-content">
      <el-descriptions :column="2">
        <el-descriptions-item label="选项集名称">
          {{ detail.name }}
        </el-descriptions-item>
        <el-descriptions-item label="选项集标识">
          {{ detail.code }}
        </el-descriptions-item>
        <!-- <el-descriptions-item label="版本">
          {{ detail.latestVersion }}
        </el-descriptions-item> -->
        <el-descriptions-item label="描述">
          <template v-if="!isEditDesc">
            <span>{{ detail.desc }}</span>
            <el-button
              type="primary"
              link
              icon="Edit"
              @click="handleEditDesc"
            />
          </template>
          <template v-else>
            <div class="flex gap-2">
              <el-input
                v-model="detail.desc"
                type="textarea"
                :rows="2"
                maxlength="100"
                show-word-limit
              />

              <el-button type="primary" size="small" @click="handleSaveDesc">
                保存
              </el-button>
              <el-button type="primary" size="small" @click="handleCancelDesc">
                取消
              </el-button>
            </div>
          </template>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider />

      <div class="flex justify-between items-center mb-2">
        <div class="flex items-center gap-2">
          <div class="font-bold flex-shrink-0">选项值</div>
          <el-input v-model="searchValue" placeholder="请输入选项值标识" />
          <el-input v-model="searchName" placeholder="请输入选项值名称" />
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>

        <div>
          <el-button type="primary" link @click="handleBatchImport">
            批量导入
          </el-button>
          <el-button
            v-if="isAllowEdit"
            type="primary"
            link
            @click="handleAddValue"
          >
            添加选项值
          </el-button>
          <el-button
            v-if="isAllowEdit"
            type="primary"
            link
            @click="handleEditExtraProperty"
          >
            编辑额外字段
          </el-button>
          <el-button type="primary" link @click="handleEdit">
            {{ isAllowEdit ? "取消编辑" : "编辑" }}
          </el-button>
        </div>
      </div>

      <el-table
        height="430"
        border
        stripe
        v-loading="loading"
        :data="optionValueList.optionValueInfo.rows"
      >
        <el-table-column label="选项值标识" prop="valueCode" align="center">
          <template #default="scope">
            <el-input v-if="scope.row.isAdding" v-model.trim="scope.row.code" />
            <span v-else>{{ scope.row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column label="选项值名称" prop="valueName" align="center">
          <template #default="scope">
            <el-input
              v-if="scope.row.isAdding || scope.row.isUpdating"
              v-model.trim="scope.row.name"
            />
            <span v-else>{{ scope.row.name }}</span>
          </template>
        </el-table-column>

        <template v-for="key in optionValueList.extraPropertyKeyArr" :key="key">
          <el-table-column :label="key" align="center">
            <template #default="scope">
              <el-input
                v-if="scope.row.isAdding || scope.row.isUpdating"
                v-model.trim="scope.row.extraPropertyObj[key]"
              />
              <span v-else>{{ scope.row.extraPropertyObj[key] }}</span>
            </template>
          </el-table-column>
        </template>

        <el-table-column label="启用状态" prop="valueEnable" align="center">
          <template #default="scope">
            <el-switch
              v-if="scope.row.isUpdating || scope.row.isAdding"
              v-model="scope.row.status"
              :active-value="1"
              :inactive-value="0"
            />
            <span v-else>
              {{ scope.row.status === 1 ? "启用" : "禁用" }}
            </span>
          </template>
        </el-table-column>

        <el-table-column
          v-if="isAllowEdit"
          label="操作"
          prop="optionValueDesc"
          width="160"
          align="center"
        >
          <template #default="scope">
            <template v-if="scope.row.isAdding || scope.row.isUpdating">
              <el-button
                type="success"
                icon="Check"
                link
                @click="handleConfirmValue(scope.row)"
              >
                确认
              </el-button>
              <el-button
                type="warning"
                icon="Close"
                link
                @click="handleCancelValue(scope.row)"
              >
                取消
              </el-button>
            </template>

            <template v-else>
              <el-button
                type="warning"
                icon="Edit"
                link
                @click="handleEditValue(scope.row)"
              >
                编辑
              </el-button>
              <el-button
                type="danger"
                icon="Delete"
                link
                @click="handleDeleteValue(scope.row)"
              >
                删除
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex justify-end items-center mt-3">
        <el-pagination
          v-model:current-page="pageParams.pageNum"
          v-model:page-size="pageParams.pageSize"
          :total="pageParams.totalSize"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[5, 10, 20, 30, 40, 50]"
          background
        />
      </div>
    </div>

    <import-dialog ref="importDialogRef" @success="getOptionValueList" />
    <extra-property-dialog
      ref="extraPropertyDialogRef"
      @success="handleExtraPropertySuccess"
    />
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  max-height: 80vh;
  padding: 20px;
  overflow-y: auto; // 添加垂直滚动条
}

:deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
