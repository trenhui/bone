<script setup lang="ts">
import PageAPI from "@/api/page";
import { formatDate } from "@/utils/date";

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });

const props = defineProps<{
  bizIdentityCode?: string;
}>();

const confirmLoading = ref(false);
const lastPublishTime = ref<any>("");

const isBizIdentity = computed(() => {
  return !!props.bizIdentityCode;
});

const initLastPublishTime = async () => {
  let time = "";
  if (isBizIdentity.value) {
    const res = await PageAPI.getLastPublishTime(
      props.bizIdentityCode as string
    );
    time = res;
  } else {
    const res = await PageAPI.getLastPublishTime();
    time = res;
  }
  lastPublishTime.value = formatDate(time);
};

watch(
  dialogVisible,
  (newVal) => {
    if (newVal) {
      initLastPublishTime();
    }
  },
  { immediate: true }
);

const handleConfirm = async () => {
  confirmLoading.value = true;
  try {
    if (isBizIdentity.value) {
      await PageAPI.publishExclusive(props.bizIdentityCode as string);
    } else {
      await PageAPI.publishBaseTemplate();
    }
    ElMessage.success("发布成功");
    handleClose();
  } catch (error) {
    console.error((error as Error).message);
  } finally {
    confirmLoading.value = false;
  }
};

const handleClose = () => {
  emits("close");
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    title="发布基础模板"
    width="35%"
  >
    <div v-if="lastPublishTime">上一次发布：{{ lastPublishTime }}</div>

    <div class="mt-5">
      <div>请确认当前调整内容，[发布] 后应用于对应专属页面</div>
      <div>已存在的业务字段，专属页面不受此基础模板发布的影响。</div>
      <div>新增加的业务字段，专属页面在配置业务字段后方可使用。</div>
    </div>

    <template #footer>
      <div class="flex justify-center">
        <el-button @click="handleClose">取消</el-button>
        <el-button
          type="primary"
          @click="handleConfirm"
          :loading="confirmLoading"
        >
          发布
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped></style>
