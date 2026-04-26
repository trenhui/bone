<script setup lang="ts">
import CreatePlanDialog from "../../components/CreatePlanDialog/index.vue";
import CreateCoverageDialog from "../../components/CreateCoverageDialog/index.vue";
import CreateLiabilityDialog from "../../components/CreateLiabilityDialog/index.vue";
import EditPlanDialog from "../../components/EditPlanDialog/index.vue";
import EditCoverageDialog from "../../components/EditCoverageDialog/index.vue";
import EditLiabilityDialog from "../../components/EditLiabilityDialog/index.vue";
import SetLiabilityRelationDialog from "../../components/SetLiabilityRelationDialog/index.vue";
import { useDialogManager } from "./dialogManager";
import LiabilityAPI, { LiabilityObject } from "@/api/liability";
import { isEmpty } from "lodash-es";
import type { TableColumnCtx } from "element-plus";
import LiabilityPushConfigDialog from "../../components/LiabilityPushConfigDialog/index.vue";

defineOptions({
  name: "LiabilityRule",
});

const props = defineProps({
  policyNo: {
    type: String,
    required: true,
  },
});

const dialogComponents: Record<string, Component> = {
  createPlan: CreatePlanDialog,
  createCoverage: CreateCoverageDialog,
  createLiability: CreateLiabilityDialog,
  editPlan: EditPlanDialog,
  editCoverage: EditCoverageDialog,
  editLiability: EditLiabilityDialog,
  setLiabilityRelation: SetLiabilityRelationDialog,
  liabilityPushConfig: LiabilityPushConfigDialog,
} as const;
const { dialogs, show, hide } = useDialogManager();

const liabilityList = ref<LiabilityObject[]>([]);
const loading = ref(false);
const queryLiabilityList = async () => {
  try {
    loading.value = true;
    liabilityList.value = await LiabilityAPI.queryLiabilityList(props.policyNo);
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  queryLiabilityList();
});

interface SpanMethodProps {
  row: LiabilityObject;
  column: TableColumnCtx<LiabilityObject>;
  rowIndex: number;
  columnIndex: number;
}

const spanMethod = ({ row, columnIndex }: SpanMethodProps) => {
  if (isEmpty(row.coverage)) {
    if (columnIndex === 3) {
      return [1, 2];
    } else if (columnIndex === 4) {
      return [0, 0];
    }
  }
};

const currentRow = ref<LiabilityObject | null>(null);
const handleCurrentChange = (row: LiabilityObject) => {
  currentRow.value = row;
};

const handleCreatePlan = () => {
  show(
    "createPlan",
    {
      policyNo: props.policyNo,
    },
    (isNeedRefresh: boolean) => {
      if (isNeedRefresh) {
        queryLiabilityList();
      }
    }
  );
};

const handleCreateCoverage = () => {
  if (currentRow.value) {
    show(
      "createCoverage",
      {
        liability: currentRow.value,
      },
      (isNeedRefresh: boolean) => {
        if (isNeedRefresh) {
          queryLiabilityList();
        }
      }
    );
  } else {
    ElMessage.warning("请先选择行");
  }
};

const handleCreateLiability = () => {
  if (currentRow.value) {
    //首先先获取当前行对应的计划
    const plan = currentRow.value.plan;
    //然后找到liabilityList中相同计划的险种
    const samePlanLiabilityList = liabilityList.value.filter(
      (item) => item.plan.planName === plan.planName
    );
    //然后找到相同计划的险种的coverageDTO，需要过滤掉undefined null和去重
    const coverageList = samePlanLiabilityList
      ?.map((item) => item.coverage)
      ?.filter((item) => item !== undefined && item !== null)
      ?.filter(
        (item, index, self) =>
          self.findIndex(
            (coverage) => coverage?.coverageName === item?.coverageName
          ) === index
      );

    if (coverageList.length === 0) {
      ElMessage.warning("请先新增险种");
      return;
    }

    show(
      "createLiability",
      {
        liability: currentRow.value,
        coverageList: coverageList,
      },
      (isNeedRefresh: boolean) => {
        if (isNeedRefresh) {
          queryLiabilityList();
        }
      }
    );
  } else {
    ElMessage.warning("请先选择行");
  }
};

const handleEditPlan = () => {
  if (currentRow.value) {
    show(
      "editPlan",
      {
        liability: currentRow.value,
      },
      (isNeedRefresh: boolean) => {
        if (isNeedRefresh) {
          queryLiabilityList();
        }
      }
    );
  } else {
    ElMessage.warning("请先选择行");
  }
};

const handleEditCoverage = () => {
  if (currentRow.value) {
    if (isEmpty(currentRow.value.coverage)) {
      ElMessage.warning("请先新增险种");
      return;
    }

    show(
      "editCoverage",
      {
        liability: currentRow.value,
      },
      (isNeedRefresh: boolean) => {
        if (isNeedRefresh) {
          queryLiabilityList();
        }
      }
    );
  } else {
    ElMessage.warning("请先选择行");
  }
};

const handleDeploy = async () => {
  if (currentRow.value) {
    await LiabilityAPI.deployPlan(currentRow.value.plan.id);
    queryLiabilityList();
    ElMessage.success("发布成功");
  } else {
    ElMessage.warning("请先选择行");
  }
};

const handleEditLiability = () => {
  if (currentRow.value) {
    if (!currentRow.value.liability) {
      ElMessage.warning("请先新增责任");
      return;
    }

    show(
      "editLiability",
      {
        id: currentRow.value.liability.id,
      },
      (isNeedRefresh: boolean) => {
        if (isNeedRefresh) {
          queryLiabilityList();
        }
      }
    );
  } else {
    ElMessage.warning("请先选择行");
  }
};

const handleSetLiabilityRelation = () => {
  show("setLiabilityRelation", { policyNo: props.policyNo });
};

const handleToCreateCoverage = (row: LiabilityObject) => {
  currentRow.value = row;
  handleCreateCoverage();
};

const handleToCreateLiability = (row: LiabilityObject) => {
  currentRow.value = row;
  handleCreateLiability();
};

const handleLiabilityPushConfig = () => {
  show("liabilityPushConfig", {
    policyNo: props.policyNo,
  });
};
</script>

<template>
  <div>
    <div class="flex justify-between">
      <div>
        <el-button @click="handleEditPlan">编辑计划</el-button>
        <el-button @click="handleEditCoverage">编辑险种</el-button>
        <el-button @click="handleEditLiability">编辑责任</el-button>
        <el-button disabled>编辑费用项目</el-button>
        <el-button>复制责任</el-button>
      </div>
      <div>
        <el-button type="primary" @click="handleCreatePlan">新增计划</el-button>
        <el-button type="primary" @click="handleCreateCoverage">
          新增险种
        </el-button>
        <el-button type="primary" @click="handleCreateLiability">
          新增责任
        </el-button>
        <el-button type="primary" @click="handleSetLiabilityRelation">
          设置责任关系
        </el-button>
        <el-button type="primary" @click="handleLiabilityPushConfig">
          责任推送配置
        </el-button>
        <el-button type="primary" @click="handleDeploy">发布计划</el-button>
      </div>
    </div>

    <el-table
      v-adaptive="30"
      :loading="loading"
      class="mt-2"
      border
      :data="liabilityList"
      :span-method="spanMethod"
      highlight-current-row
      @current-change="handleCurrentChange"
    >
      <el-table-column type="index" align="center" width="50" />

      <el-table-column label="计划层" align="center">
        <el-table-column label="计划名称" prop="plan.planName" align="center" />
        <el-table-column label="计划保额" align="center">
          <template #default="{ row }">
            <span>
              {{
                row.plan.planLimit === -1 ? "无限额" : `${row.plan.planLimit}`
              }}
            </span>
          </template>
        </el-table-column>
      </el-table-column>

      <el-table-column label="险种层" align="center">
        <el-table-column label="险种名称" align="center">
          <template #default="{ row }">
            <span v-if="row.coverage">
              {{ row.coverage.coverageName }}
            </span>
            <el-button
              v-else
              type="primary"
              link
              @click="handleToCreateCoverage(row)"
            >
              添加险种
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="险种保额" align="center">
          <template #default="{ row }">
            <span>
              {{
                row.coverage?.coverageLimit === -1
                  ? "无限额"
                  : `${row.coverage?.coverageLimit}`
              }}
            </span>
          </template>
        </el-table-column>
      </el-table-column>

      <el-table-column label="责任层" align="center">
        <el-table-column label="责任名称" prop="name" align="center">
          <template #default="{ row }">
            <span v-if="row.liability">{{ row.liability.liabilityName }}</span>
            <el-button
              v-else
              type="primary"
              link
              @click="handleToCreateLiability(row)"
            >
              添加责任
            </el-button>
          </template>
        </el-table-column>
      </el-table-column>
    </el-table>

    <template v-for="(dialog, name) in dialogs" :key="name">
      <component
        :is="dialogComponents[name]"
        v-if="dialog.visible"
        v-model="dialog.visible"
        v-bind="dialog.params"
        @close="(result: any) => hide(name, result)"
      />
    </template>
  </div>
</template>

<style lang="scss" scoped>
// :deep(.el-table__body tr.current-row > td.el-table__cell) {
//   background-color: $table-current-row-bg-color;
// }
</style>
