<script setup lang="ts">
import ClaimAPI from "@/api/claim";
defineOptions({
  name: "PolicySettingDialog",
});

const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  claimId: string;
  type: number;
}>();

const policySetting = ref<string>("");

const getPolicySetting = async () => {
  const res = await ClaimAPI.policySetting(props.claimId, props.type);
  policySetting.value = res;
};

const computedTitle = computed(() => {
  return props.type === 0 ? "特约信息" : "特殊信息";
});

watch(
  dialogVisible,
  (newVal) => {
    if (newVal) {
      getPolicySetting();
    }
  },
  { immediate: true }
);

const onClose = () => {
  dialogVisible.value = false;
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="onClose"
    :title="computedTitle"
    width="40%"
    :close-on-click-modal="false"
  >
    <div class="content">
      <div v-if="policySetting" v-html="policySetting"></div>
      <div v-else>
        <el-empty description="暂无数据" />
      </div>
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped>
.content {
  min-height: 150px;
  max-height: 350px;
  padding: 10px;
  overflow-y: auto;
  white-space: pre-wrap;
}
</style>
