<script setup lang="ts">
import LiabilityAPI, { LiabilityObject, CoverageDTO } from "@/api/liability";
import {
  validateChineseLetterNumberUnderline,
  validateLetterNumberUnderline,
} from "@/utils/strUtils";
defineOptions({
  name: "CreateCoverageDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  liability: LiabilityObject;
}>();

const coverageList = ref<CoverageDTO[]>([
  {
    id: null,
    coverageName: "",
    coverageCode: "",
    isEditing: true,
    coverageLimit: -1,
    limitType: -1,
    policyNo: props.liability.plan.policyNo,
    planId: props.liability.plan.id,
  },
]);
const confirmLoading = ref(false);
const isNeedRefresh = ref(false);

const handleClose = () => {
  dialogVisible.value = false;
  coverageList.value = [
    {
      id: null,
      coverageName: "",
      coverageCode: "",
      isEditing: true,
      coverageLimit: -1,
      limitType: -1,
      policyNo: props.liability.plan.policyNo,
      planId: props.liability.plan.id,
    },
  ];
  confirmLoading.value = false;
  emits("close", isNeedRefresh.value);
  isNeedRefresh.value = false;
};

const handleAdding = () => {
  coverageList.value.push({
    id: null,
    coverageName: "",
    coverageCode: "",
    isEditing: true,
    coverageLimit: -1,
    limitType: -1,
    policyNo: props.liability.plan.policyNo,
    planId: props.liability.plan.id,
  });
};

const handleLimitChange = (row: CoverageDTO, value: number | undefined) => {
  row.coverageLimit = value || 0;
};

const handleAdd = (row: CoverageDTO) => {
  if (
    !row.coverageName ||
    !validateChineseLetterNumberUnderline(row.coverageName, 2, 20)
  ) {
    ElMessage.warning("请正确输入险种名称，2-20字，中文字母数字下划线");
    return;
  }

  if (
    coverageList.value.some(
      (item: CoverageDTO) =>
        item != row && item.coverageName === row.coverageName
    )
  ) {
    ElMessage.warning("同保单计划不允许有重复的险种名称，请修改");
    return;
  }

  if (
    row.coverageCode &&
    !validateLetterNumberUnderline(row.coverageCode, 1, 20)
  ) {
    ElMessage.warning("请正确输入险种code，字母数字下划线");
    return;
  }

  if (row.limitType !== -1 && row.coverageLimit <= 0) {
    ElMessage.warning("请正确输入险种保额");
    return;
  }

  if (row.limitType === -1) {
    row.coverageLimit = -1;
  }

  row.isEditing = false;
};

const handleCancel = (index: number) => {
  coverageList.value.splice(index, 1);
};

const handleDelete = (row: CoverageDTO) => {
  ElMessageBox.confirm("确定删除该行？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    center: true,
  })
    .then(() => {
      coverageList.value = coverageList.value.filter(
        (item: CoverageDTO) => item !== row
      );
    })
    .catch(() => {});
};

const handleConfirm = async () => {
  try {
    confirmLoading.value = true;

    const hasEditingRow = coverageList.value.some(
      (row: CoverageDTO) => row.isEditing
    );
    if (hasEditingRow) {
      ElMessage.warning("请先完成正在编辑的行");
      confirmLoading.value = false;
      return;
    }

    await LiabilityAPI.createCoverage(coverageList.value);

    ElMessage.success("保存成功");
    isNeedRefresh.value = true;
    handleClose();
  } catch (error) {
    console.log("error", error);
  } finally {
    confirmLoading.value = false;
  }
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    append-to-body
    :close-on-click-modal="false"
    title="新增险种"
    width="55%"
  >
    <div class="dialog-content">
      <el-table border :data="[0]">
        <el-table-column label="计划名称" align="center">
          <template #default>
            <span>{{ props.liability?.plan?.planName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="计划Code" align="center">
          <template #default>
            <span>{{ props.liability?.plan?.planCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="计划保额" align="center">
          <template #default>
            <span>
              {{
                props.liability?.plan?.planLimit === -1
                  ? "无限额"
                  : `${props.liability?.plan?.planLimit}`
              }}
            </span>
          </template>
        </el-table-column>
      </el-table>

      <el-divider />

      <el-table border :data="coverageList">
        <el-table-column label="险种名称" align="center">
          <template #default="{ row }">
            <el-input
              v-if="row.isEditing"
              v-model="row.coverageName"
              minlength="2"
              maxlength="20"
            />
            <span v-else>{{ row.coverageName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="险种Code" align="center">
          <template #default="{ row }">
            <el-input v-if="row.isEditing" v-model="row.coverageCode" />
            <span v-else>{{ row.coverageCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="险种保额" align="center">
          <template #default="{ row }">
            <el-row v-if="row.isEditing" :gutter="5">
              <el-col :span="10">
                <el-select v-model="row.limitType">
                  <el-option label="有限额" :value="1" />
                  <el-option label="无限额" :value="-1" />
                </el-select>
              </el-col>
              <el-col :span="14" v-if="row.limitType === 1">
                <el-input-number
                  v-model="row.coverageLimit"
                  :min="0"
                  :precision="2"
                  @change="(value) => handleLimitChange(row, value)"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-col>
            </el-row>
            <span v-else>
              {{
                row.coverageLimit === -1
                  ? "无限额"
                  : `有限额，限额：${row.coverageLimit}`
              }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="150px">
          <template #default="scope">
            <div v-if="scope.row.isEditing">
              <el-button
                type="success"
                icon="Check"
                link
                @click="handleAdd(scope.row)"
              >
                确认
              </el-button>
              <el-button
                type="warning"
                icon="Close"
                link
                @click="handleCancel(scope.$index)"
              >
                取消
              </el-button>
            </div>

            <el-button
              v-else
              type="danger"
              icon="Delete"
              link
              @click="handleDelete(scope.row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button class="mt-2" link type="primary" @click="handleAdding">
        新增险种
      </el-button>
    </div>

    <template #footer>
      <span class="footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确认</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  max-height: 50vh;
  padding: 20px;
  overflow-y: auto;
}
</style>
