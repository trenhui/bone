<script setup lang="ts">
import FlowAPI, { Condition, TPAFlowConfig } from "@/api/jobConfig/flow";
import TableContainer from "./TableContainer.vue";
import FlowNodeSelector from "./FlowNodeSelector.vue";
import ScriptDialog from "./ScriptDialog.vue";
import { Config } from "./config";
defineOptions({
  name: "TPAFlowConfig",
});

const props = defineProps<{
  bizIdentityCode?: string;
}>();

const model = ref<TPAFlowConfig>({
  config: {
    approveCheckConfigVO: {
      dealerAssignType: "",
    },
    approveConfigVO: {
      autoApproveTag: "",
      dealerAssignType: "",
      invoiceDeepType: "",
      onePersonOnePolicyTag: "",
      policyMoreCalTag: "",
      quickCallTag: "",
      canModifyMoneyTag: "",
    },
    flowConfigVO: {
      approveCheckFlowNode: {
        jumpTypeList: [],
      },
      approveFlowNode: {
        jumpTypeList: [],
      },
      inputFlowNode: {
        jumpTypeList: [],
      },
      preCheckFlowNode: {
        jumpTypeList: [],
      },
      qualityFlowNode: {
        jumpTypeList: [],
      },
    },
    inputConfigVO: {
      autoTag: "",
      dealerAssignType: "",
      inputType: "",
      invoiceDeepType: "",
      invoiceImageBind: "",
    },
    preCheckConfigVO: {
      autoCategoryImage: "",
      autoTag: "",
      categoryRule: "",
      categoryType: "",
      dealerAssignType: "",
    },
    qualityConfigVO: {
      dealerAssignType: "",
      invoiceDeepType: "",
      liabilityBindType: "",
    },
    bizIdentityCode: "",
  },
});
const loading = reactive({
  flowConfig: false,
  preCheckConfig: false,
  inputConfig: false,
  qualityConfig: false,
  approveConfig: false,
  approveCheckConfig: false,
  deploy: false,
});

const getTPAFlowConfig = async () => {
  const res = await FlowAPI.pageInit({
    bizIdentityCode: props.bizIdentityCode || "",
  });
  model.value = res;
};

// 保存流程配置
const saveFlowConfig = async () => {
  loading.flowConfig = true;
  try {
    await FlowAPI.saveFlowConfig(
      props.bizIdentityCode || "",
      model.value.config.flowConfigVO
    );
    ElMessage.success("保存成功");
  } catch (error) {
    console.error("保存流程配置失败：", error);
  } finally {
    loading.flowConfig = false;
  }
};

const savePreCheckConfig = async () => {
  loading.preCheckConfig = true;
  try {
    await FlowAPI.savePreCheckConfig(
      props.bizIdentityCode || "",
      model.value.config.preCheckConfigVO
    );
    ElMessage.success("保存成功");
  } catch (error) {
    console.error("保存初审配置失败：", error);
  } finally {
    loading.preCheckConfig = false;
  }
};

const saveInputConfig = async () => {
  loading.inputConfig = true;
  try {
    await FlowAPI.saveInputConfig(
      props.bizIdentityCode || "",
      model.value.config.inputConfigVO
    );
    ElMessage.success("保存成功");
  } catch (error) {
    console.error("保存录入配置失败：", error);
  } finally {
    loading.inputConfig = false;
  }
};

const saveQualityConfig = async () => {
  loading.qualityConfig = true;
  try {
    await FlowAPI.saveQualityConfig(
      props.bizIdentityCode || "",
      model.value.config.qualityConfigVO
    );
    ElMessage.success("保存成功");
  } catch (error) {
    console.error("保存质检配置失败：", error);
  } finally {
    loading.qualityConfig = false;
  }
};

const saveApproveConfig = async () => {
  console.log("保存的数据：", model.value);
  loading.approveConfig = true;
  try {
    await FlowAPI.saveApproveConfig(
      props.bizIdentityCode || "",
      model.value.config.approveConfigVO
    );
    ElMessage.success("保存成功");
  } catch (error) {
    console.error("保存审核配置失败：", error);
  } finally {
    loading.approveConfig = false;
  }
};

const saveApproveCheckConfig = async () => {
  console.log("保存的数据：", model.value);
  loading.approveCheckConfig = true;
  try {
    await FlowAPI.saveApproveCheckConfig(
      props.bizIdentityCode || "",
      model.value.config.approveCheckConfigVO
    );
    ElMessage.success("保存成功");
  } catch (error) {
    console.error("保存复核配置失败：", error);
  } finally {
    loading.approveCheckConfig = false;
  }
};

const deploy = async () => {
  loading.deploy = true;
  try {
    await FlowAPI.deployFlowConfig(props.bizIdentityCode || "");
    ElMessage.success("发布成功");
  } catch (error) {
    console.error("发布失败：", error);
  } finally {
    loading.deploy = false;
  }
};

const scriptDialogRef = ref<InstanceType<typeof ScriptDialog>>();
const handleEditScript = (condition: Condition) => {
  scriptDialogRef.value?.open(condition);
};

onMounted(() => {
  getTPAFlowConfig();
});
</script>

<template>
  <div>
    <p class="text-14px">TPA流程配置</p>
    <div class="border border-gray-200 pt-10 pb-4 px-4">
      <div class="flex-x-center">
        <div class="px-1 py-1 text-14px">签收</div>
        <el-icon class="mx-2 mt-1.5" size="20"><Right /></el-icon>
        <FlowNodeSelector
          v-model="model.config.flowConfigVO.preCheckFlowNode"
          node-name="初审"
          bg-color="#f9f9f9"
          @edit-script="handleEditScript"
        />
        <el-icon class="mx-2 mt-1.5" size="20"><Right /></el-icon>
        <FlowNodeSelector
          v-model="model.config.flowConfigVO.inputFlowNode"
          node-name="录入"
          bg-color="#c6ebfc"
          @edit-script="handleEditScript"
        />
        <el-icon class="mx-2 mt-1.5" size="20"><Right /></el-icon>
        <FlowNodeSelector
          v-model="model.config.flowConfigVO.qualityFlowNode"
          node-name="质检"
          bg-color="#e7fcc7"
          @edit-script="handleEditScript"
        />
        <el-icon class="mx-2 mt-1.5" size="20"><Right /></el-icon>
        <FlowNodeSelector
          v-model="model.config.flowConfigVO.approveFlowNode"
          node-name="审核"
          bg-color="#c6ebfc"
          @edit-script="handleEditScript"
        />
        <el-icon class="mx-2 mt-1.5" size="20"><Right /></el-icon>
        <FlowNodeSelector
          v-model="model.config.flowConfigVO.approveCheckFlowNode"
          node-name="复核"
          bg-color="#c6ebfc"
          @edit-script="handleEditScript"
        />
        <el-icon class="mx-2 mt-1.5" size="20"><Right /></el-icon>
        <div class="px-1 py-1 text-14px">推送</div>
      </div>
      <div class="flex-x-end mt-4">
        <el-button
          type="primary"
          @click="saveFlowConfig"
          :loading="loading.flowConfig"
        >
          保存
        </el-button>
      </div>
    </div>

    <p class="text-14px">各环节作业规则配置</p>
    <div class="border border-gray-200 py-4 px-4">
      <div class="text-14px mb-2">初审环节</div>
      <div class="grid grid-cols-2 gap-10 items-start">
        <TableContainer
          v-model="model.config.preCheckConfigVO"
          :headers="Config.precheck.headers"
          :items="Config.precheck.items"
        />

        <TableContainer
          v-model="model.config.preCheckConfigVO"
          :headers="Config.precheckAuto.headers"
          :items="Config.precheckAuto.items"
        />
      </div>

      <div class="flex-x-end mt-4">
        <el-button
          type="primary"
          @click="savePreCheckConfig"
          :loading="loading.preCheckConfig"
        >
          保存
        </el-button>
      </div>
    </div>

    <div class="border border-t-0 border-gray-200 py-4 px-4">
      <div class="text-14px mb-2">录入环节</div>
      <div class="grid grid-cols-2 gap-10 items-start">
        <TableContainer
          v-model="model.config.inputConfigVO"
          :headers="Config.input.headers"
          :items="Config.input.items"
        />

        <TableContainer
          v-model="model.config.inputConfigVO"
          :headers="Config.inputAuto.headers"
          :items="Config.inputAuto.items"
        />
      </div>

      <div class="flex-x-end mt-4">
        <el-button
          type="primary"
          @click="saveInputConfig"
          :loading="loading.inputConfig"
        >
          保存
        </el-button>
      </div>
    </div>

    <div class="border border-t-0 border-gray-200 py-4 px-4">
      <div class="text-14px mb-2">质检环节</div>
      <div class="grid grid-cols-2 gap-10 items-start">
        <TableContainer
          v-model="model.config.qualityConfigVO"
          :headers="Config.quality.headers"
          :items="Config.quality.items"
        />

        <TableContainer
          v-model="model.config.qualityConfigVO"
          :headers="Config.qualityAuto.headers"
          :items="Config.qualityAuto.items"
        />
      </div>

      <div class="flex-x-end mt-4">
        <el-button
          type="primary"
          @click="saveQualityConfig"
          :loading="loading.qualityConfig"
        >
          保存
        </el-button>
      </div>
    </div>

    <div class="border border-t-0 border-gray-200 py-4 px-4">
      <div class="text-14px mb-2">审核环节</div>
      <div class="grid grid-cols-2 gap-10 items-start">
        <TableContainer
          v-model="model.config.approveConfigVO"
          :headers="Config.approve.headers"
          :items="Config.approve.items"
        />

        <TableContainer
          v-model="model.config.approveConfigVO"
          :headers="Config.approveAuto.headers"
          :items="Config.approveAuto.items"
        />
      </div>

      <div class="flex-x-end mt-4">
        <el-button
          type="primary"
          @click="saveApproveConfig"
          :loading="loading.approveConfig"
        >
          保存
        </el-button>
      </div>
    </div>

    <div class="border border-t-0 border-gray-200 py-4 px-4">
      <div class="text-14px mb-2">复核环节</div>
      <div class="grid grid-cols-2 gap-10 items-start">
        <TableContainer
          v-model="model.config.approveCheckConfigVO"
          :headers="Config.approveCheck.headers"
          :items="Config.approveCheck.items"
        />

        <TableContainer
          v-model="model.config.approveCheckConfigVO"
          :headers="Config.approveCheckAuto.headers"
          :items="Config.approveCheckAuto.items"
        />
      </div>

      <div class="flex-x-end mt-4">
        <el-button
          type="primary"
          @click="saveApproveCheckConfig"
          :loading="loading.approveCheckConfig"
        >
          保存
        </el-button>
      </div>
    </div>

    <div class="flex-center mt-4">
      <el-button type="primary" @click="deploy" :loading="loading.deploy">
        发布
      </el-button>
    </div>

    <ScriptDialog ref="scriptDialogRef" />
  </div>
</template>

<style lang="scss" scoped></style>
