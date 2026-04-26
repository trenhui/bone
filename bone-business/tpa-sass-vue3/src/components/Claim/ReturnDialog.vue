<script setup lang="ts">
import ClaimAPI, { IReturnNodePageInit, IReturnNodeBack } from "@/api/claim";
import { useDictSelect } from "@/hooks";
import { DictSourceTypeEnum } from "@/enums/DictSourceTypeEnum";
import type { ElCascader } from "element-plus";
defineOptions({
  name: "ReturnDialog",
});

const emit = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  claimId: {
    type: String,
    default: "",
  },
});
const isNeedRefresh = ref(false);

const returnNodePageInit = ref<IReturnNodePageInit>({
  reasonCode: "",
  returnStageList: [],
});

const { loadCascaderData } = useDictSelect(
  DictSourceTypeEnum.MAIN_DATA,
  computed(() => returnNodePageInit.value.reasonCode),
  {
    isCascader: true,
    maxLevel: 1,
  }
);

const cascaderProps = {
  value: "code",
  label: "name",
  children: "children",
  lazy: true,
  lazyLoad: loadCascaderData,
};

const getReturnNodePageInit = async () => {
  try {
    const res = await ClaimAPI.returnNodePageInit(props.claimId);
    returnNodePageInit.value = res;
  } catch (error) {
    console.error(error);
  }
};

watch(
  dialogVisible,
  (newVal) => {
    if (newVal) {
      getReturnNodePageInit();
    }
  },
  { immediate: true }
);

const onClose = () => {
  dialogVisible.value = false;
  emit("close", isNeedRefresh.value);
  isNeedRefresh.value = false;
};

const form = ref<IReturnNodeBack>({
  claimNumber: "",
  targetStage: "",
  dealerStrategy: "0",
  assignDealerName: "",
  reasonCode: "",
  reasonData: "",
  remark: "",
});

const cascaderRef = ref<InstanceType<typeof ElCascader>>();

const loading = ref(false);

const handleConfirm = async () => {
  if (!form.value.targetStage) {
    ElMessage.warning("请选择退回至环节");
    return;
  }

  if (form.value.dealerStrategy === "1" && !form.value.assignDealerName) {
    ElMessage.warning("请输入指定处理人员姓名");
    return;
  }

  if (!form.value.reasonCode) {
    ElMessage.warning("请选择差错类型");
    return;
  }

  if (String(form.value.remark).length < 10) {
    ElMessage.warning("差错原因必须大于10个字");
    return;
  }

  try {
    loading.value = true;
    await ClaimAPI.returnNodeBack({
      ...form.value,
      claimNumber: props.claimId,
      reasonCode: form.value.reasonCode[form.value.reasonCode.length - 1],
    });
    ElMessage.success("退回成功");
    isNeedRefresh.value = true;
    onClose();
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const handleReasonCodeChange = (value: any) => {
  form.value.reasonData = JSON.stringify({
    reasonCode: value[value.length - 1],
    reasonCn: cascaderRef.value?.getCheckedNodes(true)?.[0]?.label || "",
  });
};

const handleTargetStageChange = (value: any) => {
  if (form.value.dealerStrategy === "0") {
    const targetStage = returnNodePageInit.value.returnStageList.find(
      (item) => item.code === value
    );
    if (targetStage) {
      form.value.assignDealerName = targetStage.operaterName;
    }
  }
};

const handleDealerStrategyChange = (value: any) => {
  if (value === "0") {
    const targetStage = returnNodePageInit.value.returnStageList.find(
      (item) => item.code === form.value.targetStage
    );
    if (targetStage) {
      form.value.assignDealerName = targetStage.operaterName;
    }
  } else {
    form.value.assignDealerName = "";
  }
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="onClose"
    title="赔案退回"
    width="30%"
    :close-on-click-modal="false"
  >
    <div class="p-2">
      <el-form label-width="auto" label-position="left">
        <el-form-item label="赔案号">
          {{ claimId }}
        </el-form-item>
        <el-form-item label="退回至环节">
          <el-select
            v-model="form.targetStage"
            placeholder="请选择退回环节"
            @change="handleTargetStageChange"
          >
            <el-option
              v-for="item in returnNodePageInit.returnStageList"
              :key="item.code"
              :label="item.name"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="退回至原处理人员">
          <el-select
            style="width: 100px; margin-right: 10px"
            v-model="form.dealerStrategy"
            placeholder="请选择退回至原处理人员"
            @change="handleDealerStrategyChange"
          >
            <el-option value="0" label="是" />
            <el-option value="1" label="否" />
          </el-select>
          <el-input
            style="width: calc(100% - 110px)"
            v-model="form.assignDealerName"
            :maxlength="100"
            :disabled="form.dealerStrategy == '0'"
          />
        </el-form-item>
        <el-form-item label="差错类型">
          <el-cascader
            ref="cascaderRef"
            v-model="form.reasonCode"
            :props="cascaderProps"
            clearable
            :show-all-levels="false"
            :key="returnNodePageInit.reasonCode"
            @change="handleReasonCodeChange"
          />
        </el-form-item>
        <el-form-item label="差错原因">
          <el-input
            v-model="form.remark"
            :maxlength="100"
            :show-word-limit="true"
            :rows="3"
            type="textarea"
            placeholder="请输入差错原因，大于10个字"
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <div class="flex justify-center">
        <el-button @click="onClose">取消</el-button>
        <el-button type="primary" @click="handleConfirm" :loading="loading">
          确定
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped></style>
