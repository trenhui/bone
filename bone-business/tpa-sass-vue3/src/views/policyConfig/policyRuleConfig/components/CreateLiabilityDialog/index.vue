<script setup lang="ts">
import LiabilityAPI, {
  LiabilityObject,
  CoverageDTO,
  LiabilityDTO,
} from "@/api/liability";
import { validateChineseLetterNumberUnderline } from "@/utils/strUtils";
defineOptions({
  name: "CreateLiabilityDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  liability: LiabilityObject;
  coverageList: CoverageDTO[];
}>();

type NewLiability = Pick<
  LiabilityDTO,
  "coverageId" | "liabilityName" | "planId"
> & {
  /** 是否编辑 */
  isEditing?: boolean;
};
const liabilityList = ref<NewLiability[]>([
  {
    coverageId: "",
    liabilityName: "",
    planId: props.liability.plan.id,
    isEditing: true,
  },
]);
const confirmLoading = ref(false);
const isNeedRefresh = ref(false);

const handleClose = () => {
  dialogVisible.value = false;
  confirmLoading.value = false;
  emits("close", isNeedRefresh.value);
  isNeedRefresh.value = false;
  liabilityList.value = [
    {
      coverageId: "",
      liabilityName: "",
      planId: props.liability.plan.id,
      isEditing: true,
    },
  ];
};

const handleAddingCoverage = () => {
  liabilityList.value.push({
    coverageId: "",
    liabilityName: "",
    planId: props.liability.plan.id,
    isEditing: true,
  });
};

const handleAddingLiability = (row: NewLiability) => {
  liabilityList.value.push({
    coverageId: row.coverageId,
    liabilityName: "",
    planId: props.liability.plan.id,
    isEditing: true,
  });
};

const handleAdd = (row: NewLiability, index: number) => {
  if (!row.liabilityName || !row.coverageId) {
    ElMessage.warning("请填写完整后确认");
    return;
  }

  if (!validateChineseLetterNumberUnderline(row.liabilityName, 2, 20)) {
    ElMessage.warning("责任名称必须为2-20个中文字母数字下划线");
    return;
  }

  //同计划下责任名称不能重复
  const isRepeat = liabilityList.value.some(
    (item: NewLiability, i: number) =>
      item.liabilityName === row.liabilityName &&
      item.planId === row.planId &&
      i !== index
  );
  if (isRepeat) {
    ElMessage.warning("同计划下责任名称不允许重复");
    return;
  }

  row.isEditing = false;
};

const handleCancel = (index: number) => {
  liabilityList.value.splice(index, 1);
};

const handleDelete = (row: NewLiability) => {
  ElMessageBox.confirm("确定删除该行？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    center: true,
  })
    .then(() => {
      liabilityList.value = liabilityList.value.filter(
        (item: NewLiability) => item !== row
      );
    })
    .catch(() => {});
};

const handleConfirm = async () => {
  try {
    confirmLoading.value = true;

    const hasEditingRow = liabilityList.value.some(
      (row: NewLiability) => row.isEditing
    );
    if (hasEditingRow) {
      ElMessage.warning("请先完成正在编辑的行");
      confirmLoading.value = false;
      return;
    }

    //保存的网络请求
    await LiabilityAPI.createLiability({
      policyNo: props.liability.plan.policyNo,
      liabilityCreateRequestList: liabilityList.value,
    });

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
    title="新增责任"
    width="50%"
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

      <el-table border :data="liabilityList">
        <el-table-column label="险种名称" align="center">
          <template #default="{ row }">
            <el-select :disabled="!row.isEditing" v-model="row.coverageId">
              <el-option
                v-for="item in props.coverageList"
                :key="item.id!"
                :label="item.coverageName"
                :value="item.id!"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="责任名称" align="center">
          <template #default="{ row }">
            <el-input
              :disabled="!row.isEditing"
              v-model="row.liabilityName"
              minlength="2"
              maxlength="20"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center">
          <template #default="scope">
            <div v-if="scope.row.isEditing">
              <el-button
                type="success"
                icon="Check"
                link
                @click="handleAdd(scope.row, scope.$index)"
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
            <div v-else>
              <el-button
                type="primary"
                link
                @click="handleAddingLiability(scope.row)"
              >
                增加该险种下的责任
              </el-button>
              <el-button type="danger" link @click="handleDelete(scope.row)">
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-button class="mt-2" link type="primary" @click="handleAddingCoverage">
        新增同计划下的责任
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
