<script setup lang="ts">
import CreateShareCodeDialog from "./CreateShareCodeDialog.vue";
import UpdateShareLimitDialog from "./UpdateShareLimitDialog.vue";
import CreateShareMemberDialog from "./CreateShareMemberDialog.vue";
import LiabilityAPI, {
  ShareDTO,
  LiabilityShortDTO,
  ShareRelationDTO,
  PlanDTO,
} from "@/api/liability";
defineOptions({
  name: "ShareRelation",
});

const props = defineProps<{
  policyNo: string;
}>();

const liabilityConfigList = ref<LiabilityShortDTO[]>([]);
const shareCodeList = ref<ShareDTO[]>([]);
const relationList = ref<ShareRelationDTO[]>([]);
const planList = ref<PlanDTO[]>([]);

const initSharePage = () => {
  try {
    LiabilityAPI.shareConfigPageInit(props.policyNo).then((res) => {
      liabilityConfigList.value = res.liabilityConfigList;
      shareCodeList.value = res.shareCodeList;
      relationList.value = res.relationList;
      planList.value = res.planList;
    });
  } catch (error) {
    console.log("error", error);
  }
};

onMounted(() => {
  initSharePage();
});

const createShareCodeDialog = ref<{
  isVisible: boolean;
  params: {
    policyNo: string;
    planList: PlanDTO[];
  };
  onClose: (isNeedRefresh: boolean) => void;
}>({
  isVisible: false,
  params: {
    policyNo: "",
    planList: [],
  },
  onClose: (isNeedRefresh: boolean) => {
    createShareCodeDialog.value.isVisible = false;
    createShareCodeDialog.value.params = {
      policyNo: "",
      planList: [],
    };
    if (isNeedRefresh) {
      initSharePage();
    }
  },
});

const updateShareLimitDialog = ref<{
  isVisible: boolean;
  params: {
    oriShare: ShareDTO;
  };
  onClose: (isNeedRefresh: boolean) => void;
}>({
  isVisible: false,
  params: {
    oriShare: {
      id: "",
      policyNo: "",
      planId: "",
      planName: "",
      shareId: "",
      shareCode: "",
      shareLimit: 0,
    },
  },
  onClose: (isNeedRefresh: boolean) => {
    updateShareLimitDialog.value.isVisible = false;
    updateShareLimitDialog.value.params = {
      oriShare: {
        id: "",
        policyNo: "",
        planId: "",
        planName: "",
        shareId: "",
        shareCode: "",
        shareLimit: 0,
      },
    };
    if (isNeedRefresh) {
      initSharePage();
    }
  },
});

const createShareMemberDialog = ref<{
  isVisible: boolean;
  params: {
    policyNo: string;
    planList: PlanDTO[];
    liabilityList: LiabilityShortDTO[];
    shareCodeList: ShareDTO[];
  };
  onClose: (isNeedRefresh: boolean) => void;
}>({
  isVisible: false,
  params: {
    policyNo: "",
    planList: [],
    liabilityList: [],
    shareCodeList: [],
  },
  onClose: (isNeedRefresh: boolean) => {
    createShareMemberDialog.value.isVisible = false;
    createShareMemberDialog.value.params = {
      policyNo: "",
      planList: [],
      liabilityList: [],
      shareCodeList: [],
    };
    if (isNeedRefresh) {
      initSharePage();
    }
  },
});

const handleCreateCode = () => {
  createShareCodeDialog.value.isVisible = true;
  createShareCodeDialog.value.params = {
    policyNo: props.policyNo,
    planList: planList.value,
  };
};

const handleUpdateLimit = (row: ShareDTO) => {
  updateShareLimitDialog.value.isVisible = true;
  updateShareLimitDialog.value.params.oriShare = row;
};

const handleCreateMember = () => {
  createShareMemberDialog.value.isVisible = true;
  createShareMemberDialog.value.params = {
    policyNo: props.policyNo,
    planList: planList.value,
    liabilityList: liabilityConfigList.value,
    shareCodeList: shareCodeList.value,
  };
};

const handleDeleteCode = (row: ShareDTO) => {
  ElMessageBox.confirm("确定删除该行？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    center: true,
  })
    .then(() => {
      if (!row.id) {
        ElMessage.error("删除失败");
        return;
      }
      LiabilityAPI.deleteShareCode({ id: row.id }).then(() => {
        initSharePage();
        ElMessage.success("删除成功");
      });
    })
    .catch(() => {});
};

const handleDeleteShareMember = (row: ShareRelationDTO) => {
  ElMessageBox.confirm("确定删除该行？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    center: true,
  })
    .then(() => {
      if (!row.id) {
        ElMessage.error("删除失败");
        return;
      }
      LiabilityAPI.deleteShareRelation({ id: row.id }).then(() => {
        initSharePage();
        ElMessage.success("删除成功");
      });
    })
    .catch(() => {});
};
</script>

<template>
  <div>
    <div class="flex items-center justify-between">
      <span>责任共保代码</span>
      <el-button type="primary" @click="handleCreateCode">
        新增共保代码
      </el-button>
    </div>
    <el-table class="mt-2" border :data="shareCodeList">
      <el-table-column label="计划名称(Code)" prop="planName" align="center" />
      <el-table-column label="责任共保ID" prop="shareId" align="center" />
      <el-table-column label="责任共保代码" prop="shareCode" align="center" />
      <el-table-column label="共保保额" prop="shareLimit" align="center" />
      <el-table-column label="操作" align="center">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleUpdateLimit(row)">
            更新保额
          </el-button>
          <el-button type="primary" link @click="handleDeleteCode(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="flex items-center justify-between mt-4">
      <span>共保关系责任列表</span>
      <div>
        <el-button type="primary" @click="handleCreateMember">
          新增共保责任
        </el-button>
      </div>
    </div>
    <el-table class="mt-2" border :data="relationList">
      <el-table-column
        label="计划名称(Code)"
        prop="planInfo.planName"
        align="center"
      />
      <el-table-column label="计划保额" align="center">
        <template #default="{ row }">
          {{
            row.planInfo.planLimit === -1 ? "无限额" : row.planInfo.planLimit
          }}
        </template>
      </el-table-column>
      <el-table-column
        label="险种名称(Code)"
        prop="coverageDTO.coverageName"
        align="center"
      />
      <el-table-column label="险种保额" align="center">
        <template #default="{ row }">
          {{
            row.coverageDTO.coverageLimit === -1
              ? "无限额"
              : row.coverageDTO.coverageLimit
          }}
        </template>
      </el-table-column>
      <el-table-column label="责任名称" prop="liabilityName" align="center" />
      <el-table-column label="共保代码" prop="shareCode" align="center" />
      <el-table-column label="操作" align="center">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleDeleteShareMember(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <create-share-code-dialog
      v-model="createShareCodeDialog.isVisible"
      v-bind="createShareCodeDialog.params"
      @close="createShareCodeDialog.onClose"
    />

    <update-share-limit-dialog
      v-model="updateShareLimitDialog.isVisible"
      v-bind="updateShareLimitDialog.params"
      @close="updateShareLimitDialog.onClose"
    />

    <create-share-member-dialog
      v-model="createShareMemberDialog.isVisible"
      v-bind="createShareMemberDialog.params"
      @close="createShareMemberDialog.onClose"
    />
  </div>
</template>

<style lang="scss" scoped></style>
