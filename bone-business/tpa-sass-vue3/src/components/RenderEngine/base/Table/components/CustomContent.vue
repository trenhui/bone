<script setup>
import ClaimAPI from "@/api/claim";
import { useScopeData } from "@/components/RenderEngine/hooks/useScopeData";
import { getValueByJsonPath } from "@/utils/jsonpathUtils";

const scopeData = useScopeData();

defineOptions({
  name: "TableCustomContent",
});

const refresh = defineModel("refresh", {
  type: Boolean,
  default: false,
});

const content = ref("");
const errorMessage = ref("");
const isChecking = ref(false);
let statusTimer = null;
let maxRetries = 10; // 最大重试次数 (10次 * 5秒 = 50秒)
let retryCount = 0;

const showCustomContent = computed(() => {
  return content.value || errorMessage.value || isChecking.value;
});

const checkSameInvoice = async () => {
  const tableData = scopeData.getData("tableData");

  if (!tableData || tableData.value.length === 0) {
    ElMessage.error("暂无发票数据，无法进行查重");
    return;
  }

  isChecking.value = true;
  let taskId = null;
  const claimId = scopeData.getData("claimId");

  try {
    taskId = await ClaimAPI.checkSameInvoice(claimId);
  } catch (error) {
    errorMessage.value = "获取任务信息失败";
    isChecking.value = false;
    return;
  }

  if (!taskId) {
    errorMessage.value = "获取任务信息失败";
    isChecking.value = false;
    return;
  }

  const checkStatus = async () => {
    // 使用定时器检查状态
    statusTimer = setInterval(async () => {
      try {
        // 检查重试次数
        if (retryCount >= maxRetries) {
          errorMessage.value = "查重超时，请稍后重试";
          clearInterval(statusTimer);
          statusTimer = null;
          isChecking.value = false;
          return;
        }

        const status = await ClaimAPI.checkSameInvoiceTaskStatus(taskId);
        console.log(`状态检查 ${retryCount + 1}/${maxRetries}:`, status);
        retryCount++;

        if (status === 1) {
          const result = await ClaimAPI.checkSameInvoiceResult(claimId);
          console.log(result);
          content.value = result;
          clearInterval(statusTimer);
          statusTimer = null;
          isChecking.value = false;
        } else if (status === 2) {
          errorMessage.value = "查重失败";
          clearInterval(statusTimer);
          statusTimer = null;
          isChecking.value = false;
        }
      } catch (error) {
        console.error("状态检查失败:", error);
        retryCount++;

        // 如果连续失败次数过多，停止重试
        if (retryCount >= maxRetries) {
          errorMessage.value = "查重过程中发生错误";
          clearInterval(statusTimer);
          statusTimer = null;
          isChecking.value = false;
        }
      }
    }, 5000);
  };

  // 一分钟后开始使用定时器检查
  setTimeout(() => {
    checkStatus();
  }, 60000);
};

watch(refresh, (newVal) => {
  if (newVal) {
    //如果还在查重，则不进行查重
    if (isChecking.value) {
      refresh.value = false;
      ElMessage.error("正在查重，请稍后再试");
      return;
    }
    content.value = "";
    errorMessage.value = "";
    checkSameInvoice();
    refresh.value = false;
  }
});

onMounted(() => {
  const data = scopeData.getData("data");
  if (data.value) {
    content.value = getValueByJsonPath(data.value, "$.syncHintMsg.checkSame");
  }
});

onUnmounted(() => {
  if (statusTimer) {
    clearInterval(statusTimer);
    statusTimer = null;
  }
  // 重置计数器
  retryCount = 0;
});

const isExpand = ref(false);
const handleExpand = () => {
  isExpand.value = !isExpand.value;
};
</script>

<template>
  <div
    v-if="showCustomContent"
    class="p-3 bg-red-50 rounded-md border border-red-100 text-sm text-red-500"
  >
    <el-button class="cursor-pointer float-right" link @click="handleExpand">
      {{ isExpand ? "收起" : "展开" }}
    </el-button>
    <div
      v-if="content"
      class="whitespace-pre-wrap"
      :class="{ 'is-collapse': !isExpand }"
    >
      {{ content }}
    </div>
    <div v-else-if="errorMessage">
      {{ errorMessage }}
    </div>
    <div v-else class="flex items-center gap-2">
      <el-icon class="is-loading"><Loading /></el-icon>
      {{ "查重中，请稍等..." }}
    </div>
  </div>
</template>

<style lang="scss" scoped>
.is-collapse {
  display: -webkit-box;
  max-height: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  line-clamp: 3;
}
</style>
